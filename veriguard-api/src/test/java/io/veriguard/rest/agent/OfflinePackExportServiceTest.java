package io.veriguard.rest.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.audit.OfflinePackAuditService;
import io.veriguard.crypto.Ed25519SignatureService;
import io.veriguard.crypto.VpackSerializer;
import io.veriguard.crypto.VpackTaskListBuilder;
import io.veriguard.crypto.X25519BoxService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit tests for {@link OfflinePackExportService}.
 *
 * <p>Uses real {@link VpackTaskListBuilder} / {@link VpackSerializer} / crypto services (pure +
 * stateless) so the test exercises the actual envelope build + round-trip, while mocking the data
 * layer (queue / onboarding / audit / agent repo) for deterministic input.
 */
class OfflinePackExportServiceTest {

  private static final String AGENT_ID = "agent-export-svc-1";
  private static final String PLATFORM_ID = "veriguard-test-1";
  private static final String OPERATOR = "alice@platform";
  private static final String CLIENT_IP = "10.0.0.42";
  private static final String ONBOARD_TOKEN = "tok-export-svc-1";

  private Ed25519SignatureService ed25519;
  private X25519BoxService x25519;
  private VpackSerializer vpackSerializer;
  private VpackTaskListBuilder builder;
  private ObjectMapper objectMapper;

  private PlatformIdentityService platformIdentity;
  private AgentOnboardingService onboardingService;
  private AgentTaskQueueService taskQueueService;
  private OfflinePackAuditService auditService;

  private OfflinePackExportService exportService;

  private Ed25519SignatureService.Ed25519KeyPair platformSignPair;
  private X25519BoxService.X25519KeyPair platformEncPair;
  private Ed25519SignatureService.Ed25519KeyPair agentSignPair;
  private X25519BoxService.X25519KeyPair agentEncPair;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    ed25519 = new Ed25519SignatureService();
    x25519 = new X25519BoxService();
    vpackSerializer = new VpackSerializer(objectMapper, ed25519);
    builder = new VpackTaskListBuilder(vpackSerializer, x25519, objectMapper);

    platformSignPair = ed25519.generate();
    platformEncPair = x25519.generate();
    agentSignPair = ed25519.generate();
    agentEncPair = x25519.generate();

    platformIdentity = Mockito.mock(PlatformIdentityService.class);
    when(platformIdentity.getPlatformId()).thenReturn(PLATFORM_ID);
    when(platformIdentity.getPlatformSignPriv()).thenReturn(platformSignPair.privateKey());
    when(platformIdentity.getPlatformSignPub()).thenReturn(platformSignPair.publicKey());
    when(platformIdentity.getPlatformEncPriv()).thenReturn(platformEncPair.privateKey());
    when(platformIdentity.getPlatformEncPub()).thenReturn(platformEncPair.publicKey());

    onboardingService = Mockito.mock(AgentOnboardingService.class);
    when(onboardingService.findByToken(ONBOARD_TOKEN))
        .thenReturn(
            Optional.of(
                new AgentOnboardingService.AgentOnboardingState(
                    AGENT_ID,
                    ONBOARD_TOKEN,
                    "test-agent",
                    List.of("http_attack"),
                    List.of("A", "C"),
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    agentSignPair.publicKey(),
                    agentEncPair.publicKey(),
                    Instant.now())));

    taskQueueService = Mockito.mock(AgentTaskQueueService.class);
    auditService = Mockito.mock(OfflinePackAuditService.class);

    exportService =
        new OfflinePackExportService(
            platformIdentity,
            onboardingService,
            taskQueueService,
            builder,
            vpackSerializer,
            auditService);
  }

  @Test
  void export_drains_and_seals_tasks_writes_audit_when_agent_persisted() throws IOException {
    List<AgentDtos.AgentTask> queued =
        List.of(
            new AgentDtos.AgentTask(
                "task-1", "http_attack", "veriguard-web-attack", "{}", List.of()),
            new AgentDtos.AgentTask(
                "task-2", "command_inject", "veriguard-command", "{}", List.of()));
    when(taskQueueService.drainTasks(AGENT_ID)).thenReturn(queued);

    Optional<OfflinePackExportService.ExportResult> result =
        exportService.export(
            new AgentDtos.OfflinePackExportInput(AGENT_ID, 100),
            ONBOARD_TOKEN,
            OPERATOR,
            CLIENT_IP);

    assertThat(result).isPresent();
    OfflinePackExportService.ExportResult er = result.get();
    assertThat(er.taskCount()).isEqualTo(2);
    assertThat(er.packId()).isNotNull();
    assertThat(er.vpackBytes()).isNotEmpty();

    // Envelope round-trips with the platform sign pub key.
    VpackSerializer.VpackContents parsed =
        vpackSerializer.parse(er.vpackBytes(), platformSignPair.publicKey());
    assertThat(parsed.metadata().agentId()).isEqualTo(AGENT_ID);
    assertThat(parsed.metadata().taskCount()).isEqualTo(2);
    assertThat(parsed.metadata().platformId()).isEqualTo(PLATFORM_ID);
    assertThat(parsed.metadata().exportedBy()).isEqualTo(OPERATOR);

    // Decrypt and verify the plaintext is the JSON-encoded queued task list.
    byte[] plaintext =
        x25519.open(
            parsed.encryptedEnvelope().ciphertext(),
            parsed.encryptedEnvelope().nonce(),
            parsed.encryptedEnvelope().senderX25519Pub(),
            agentEncPair.privateKey());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> roundTripped = objectMapper.readValue(plaintext, List.class);
    assertThat(roundTripped).hasSize(2);
    assertThat(roundTripped.get(0).get("task_id")).isEqualTo("task-1");
    assertThat(roundTripped.get(1).get("task_id")).isEqualTo("task-2");

    ArgumentCaptor<Integer> taskCountCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(auditService, times(1))
        .recordExport(
            eq(er.packId()),
            eq(AGENT_ID),
            eq(PLATFORM_ID),
            eq(OPERATOR),
            eq(CLIENT_IP),
            any(byte[].class),
            taskCountCaptor.capture());
    assertThat(taskCountCaptor.getValue()).isEqualTo(2);
  }

  @Test
  void export_returns_empty_when_token_unknown() {
    when(onboardingService.findByToken("nope")).thenReturn(Optional.empty());

    Optional<OfflinePackExportService.ExportResult> result =
        exportService.export(
            new AgentDtos.OfflinePackExportInput(AGENT_ID, 100), "nope", OPERATOR, CLIENT_IP);

    assertThat(result).isEmpty();
    verify(taskQueueService, never()).drainTasks(anyString());
    verify(auditService, never())
        .recordExport(any(), anyString(), anyString(), anyString(), anyString(), any(), anyInt());
  }

  @Test
  void export_returns_empty_when_agent_id_mismatch() {
    Optional<OfflinePackExportService.ExportResult> result =
        exportService.export(
            new AgentDtos.OfflinePackExportInput("different-agent", 100),
            ONBOARD_TOKEN,
            OPERATOR,
            CLIENT_IP);

    assertThat(result).isEmpty();
    verify(taskQueueService, never()).drainTasks(anyString());
  }

  @Test
  void export_respects_max_tasks_and_reenqueues_overflow() {
    List<AgentDtos.AgentTask> queued =
        List.of(
            mkTask("task-1"),
            mkTask("task-2"),
            mkTask("task-3"),
            mkTask("task-4"),
            mkTask("task-5"));
    when(taskQueueService.drainTasks(AGENT_ID)).thenReturn(queued);
    Optional<OfflinePackExportService.ExportResult> result =
        exportService.export(
            new AgentDtos.OfflinePackExportInput(AGENT_ID, 2), ONBOARD_TOKEN, OPERATOR, CLIENT_IP);

    assertThat(result).isPresent();
    assertThat(result.get().taskCount()).isEqualTo(2);
    ArgumentCaptor<AgentDtos.AgentTask> enqueueCaptor =
        ArgumentCaptor.forClass(AgentDtos.AgentTask.class);
    verify(taskQueueService, times(3)).enqueue(eq(AGENT_ID), enqueueCaptor.capture());
    assertThat(enqueueCaptor.getAllValues())
        .extracting(AgentDtos.AgentTask::taskId)
        .containsExactly("task-3", "task-4", "task-5");
  }

  @Test
  void export_empty_queue_emits_zero_task_pack() {
    when(taskQueueService.drainTasks(AGENT_ID)).thenReturn(List.of());
    Optional<OfflinePackExportService.ExportResult> result =
        exportService.export(
            new AgentDtos.OfflinePackExportInput(AGENT_ID, 100),
            ONBOARD_TOKEN,
            OPERATOR,
            CLIENT_IP);

    assertThat(result).isPresent();
    assertThat(result.get().taskCount()).isZero();
    VpackSerializer.VpackContents parsed =
        vpackSerializer.parse(result.get().vpackBytes(), platformSignPair.publicKey());
    assertThat(parsed.metadata().taskCount()).isZero();
  }

  @Test
  void export_rejects_max_tasks_above_hard_cap() {
    assertThatThrownBy(
            () ->
                exportService.export(
                    new AgentDtos.OfflinePackExportInput(AGENT_ID, 1001),
                    ONBOARD_TOKEN,
                    OPERATOR,
                    CLIENT_IP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max_tasks must be <= 1000");
  }

  @Test
  void export_rejects_negative_max_tasks() {
    // The DTO carries @Min(0) but records do not auto-validate; only JSR-380 binders enforce it.
    // Direct service callers can still construct OfflinePackExportInput(agentId, -1) — the runtime
    // guard in the service must catch it.
    assertThatThrownBy(
            () ->
                exportService.export(
                    new AgentDtos.OfflinePackExportInput(AGENT_ID, -1),
                    ONBOARD_TOKEN,
                    OPERATOR,
                    CLIENT_IP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max_tasks must be >= 0");
  }

  // ----- helpers -----

  private static AgentDtos.AgentTask mkTask(String id) {
    return new AgentDtos.AgentTask(id, "http_attack", "veriguard-web-attack", "{}", List.of());
  }
}
