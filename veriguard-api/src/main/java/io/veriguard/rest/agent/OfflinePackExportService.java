package io.veriguard.rest.agent;

import io.veriguard.audit.OfflinePackAuditService;
import io.veriguard.crypto.VpackSerializer;
import io.veriguard.crypto.VpackTaskListBuilder;
import io.veriguard.database.model.OfflinePackAuditEntity;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Build a real {@code .vpack} for the Veriguard Agent (C1) Mode C 离线工作 export endpoint.
 *
 * <p>Replaces the C1-Platform-2 stub that wrote an empty {@code {"tasks":[]}} body. Now drains real
 * pending tasks from {@link AgentTaskQueueService} (the same in-memory queue Mode A polls) and
 * hands them to {@link VpackTaskListBuilder} for canonical envelope production.
 *
 * <p>Mode A and Mode C share the same task source: the queue is drained on export so the same task
 * cannot be both polled online and shipped offline (one-shot delivery semantics — matches the
 * upstream OpenAEV "claim + execute" pattern; see {@link AgentTaskQueueService#drainTasks}).
 *
 * <p>Audit: every successful export writes one row to {@code offline_pack_audit} via {@link
 * OfflinePackAuditService}, with {@code task_count} reflecting the real drained-task size and
 * {@code exported_ciphertext_sha256} computed from the envelope's ciphertext (defense-in-depth
 * against later mid-flight tampering since the operator carries the file out-of-band).
 *
 * <p>If the in-memory {@link AgentOnboardingService} state has no matching JPA {@code agents} row
 * (scaffold mode pre-C1-Platform-3), audit is skipped with a WARN; the export itself still succeeds
 * — buyer-side flow remains usable without enforcing JPA persistence yet.
 */
@Service
@Slf4j
public class OfflinePackExportService {

  /** Schema version emitted on the {@code .vpack} payload field — matches the Rust agent side. */
  public static final String SCHEMA_VERSION_PAYLOAD = "1.0";

  private final PlatformIdentityService platformIdentity;
  private final AgentOnboardingService onboardingService;
  private final AgentTaskQueueService taskQueueService;
  private final VpackTaskListBuilder vpackTaskListBuilder;
  private final VpackSerializer vpackSerializer;
  private final OfflinePackAuditService auditService;

  public OfflinePackExportService(
      PlatformIdentityService platformIdentity,
      AgentOnboardingService onboardingService,
      AgentTaskQueueService taskQueueService,
      VpackTaskListBuilder vpackTaskListBuilder,
      VpackSerializer vpackSerializer,
      OfflinePackAuditService auditService) {
    this.platformIdentity = platformIdentity;
    this.onboardingService = onboardingService;
    this.taskQueueService = taskQueueService;
    this.vpackTaskListBuilder = vpackTaskListBuilder;
    this.vpackSerializer = vpackSerializer;
    this.auditService = auditService;
  }

  /**
   * Drain pending tasks for {@code input.agentId()}, seal them into a {@code .vpack} envelope, and
   * record an audit row.
   *
   * @param input agent_id + max_tasks selector (max_tasks caps the drained slice; the queue may
   *     still hold more tasks for a subsequent export)
   * @param onboardToken the active onboard token for the agent (used to look up the agent's X25519
   *     enc pub key as recipient of the sealed envelope)
   * @param exportedBy admin operator username (audit attribution)
   * @param clientIp immediate connection IP (audit attribution; never trust X-Forwarded-For)
   * @return the export bundle (pack_id + canonical .vpack bytes + task_count), or {@link
   *     Optional#empty()} if {@code onboardToken} is invalid / not yet registered or the {@code
   *     agentId} in the request body does not match the token state
   */
  public Optional<ExportResult> export(
      AgentDtos.OfflinePackExportInput input,
      String onboardToken,
      String exportedBy,
      String clientIp) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    if (input.maxTasks() < 0) {
      // Defensive — @Min(0) on the DTO already rejects this at the binding layer; keep the runtime
      // check so direct service callers (e.g., unit tests) cannot bypass it.
      throw new IllegalArgumentException("max_tasks must be >= 0");
    }
    if (input.maxTasks() > OfflinePackAuditEntity.MAX_TASK_COUNT) {
      throw new IllegalArgumentException(
          "max_tasks must be <= "
              + OfflinePackAuditEntity.MAX_TASK_COUNT
              + " (spec §3.5.5 hard cap)");
    }

    Optional<AgentOnboardingService.AgentOnboardingState> stateOpt =
        onboardingService.findByToken(onboardToken);
    if (stateOpt.isEmpty()) {
      return Optional.empty();
    }
    AgentOnboardingService.AgentOnboardingState state = stateOpt.get();
    if (state.agentEncPub() == null) {
      // Agent has not completed registration yet — no enc pub key available to seal toward.
      return Optional.empty();
    }
    if (!state.agentId().equals(input.agentId())) {
      return Optional.empty();
    }

    List<AgentDtos.AgentTask> drained = taskQueueService.drainTasks(input.agentId());
    List<AgentDtos.AgentTask> packTasks =
        drained.size() <= input.maxTasks() ? drained : drained.subList(0, input.maxTasks());
    // If we drained more than max_tasks, re-enqueue the overflow so it is available for the next
    // export (or the next /api/agent/poll if the operator falls back to Mode A).
    if (drained.size() > input.maxTasks()) {
      for (int i = input.maxTasks(); i < drained.size(); i++) {
        taskQueueService.enqueue(input.agentId(), drained.get(i));
      }
    }

    UUID packId = UUID.randomUUID();
    Instant issuedAt = Instant.now();

    VpackTaskListBuilder.BuildInput buildInput =
        new VpackTaskListBuilder.BuildInput(
            List.copyOf(packTasks),
            packId,
            platformIdentity.getPlatformId(),
            input.agentId(),
            issuedAt,
            SCHEMA_VERSION_PAYLOAD,
            exportedBy != null ? exportedBy : "unknown",
            state.agentEncPub(),
            platformIdentity.getPlatformEncPriv(),
            platformIdentity.getPlatformSignPriv());
    byte[] envelopeBytes = vpackTaskListBuilder.build(buildInput);

    // Self-verify the envelope we just produced so the operator never carries a malformed file
    // out-of-band, and extract ciphertext for audit sha256.
    VpackSerializer.VpackContents parsed =
        vpackSerializer.parse(envelopeBytes, platformIdentity.getPlatformSignPub());
    byte[] ctSha256 = sha256(parsed.encryptedEnvelope().ciphertext());

    // Audit — V21 dropped the agent_id FK to the OpenAEV agents table, so the per-export audit
    // row always writes regardless of whether the agent has a row in the upstream agents table.
    // Mode C agents go through /api/agent/onboard/register and do not own an OpenAEV Asset.
    auditService.recordExport(
        packId,
        input.agentId(),
        platformIdentity.getPlatformId(),
        exportedBy,
        clientIp,
        ctSha256,
        packTasks.size());

    return Optional.of(new ExportResult(packId, envelopeBytes, packTasks.size()));
  }

  /** SHA-256 digest as 32-byte array. */
  private static byte[] sha256(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(bytes);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  /**
   * Successful export outcome — the controller turns this into a {@code Content-Disposition}
   * attachment download.
   */
  public record ExportResult(UUID packId, byte[] vpackBytes, int taskCount) {}
}
