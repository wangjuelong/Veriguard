package io.veriguard.rest.agent;

import io.veriguard.audit.OfflinePackAuditService;
import io.veriguard.crypto.VpackSerializer;
import io.veriguard.crypto.VresultsTaskResultParser;
import io.veriguard.crypto.X25519BoxService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Verify + decrypt + parse a {@code .vresults} envelope and update the matching {@code
 * offline_pack_audit} row.
 *
 * <p>Replaces the C1-Platform-2 stub that only verified the Ed25519 signature and skipped the
 * encrypted body. Now opens the body via {@link VresultsTaskResultParser} so the platform actually
 * observes the agent-side per-task {@code status / exit_code / stdout / ...} payloads.
 *
 * <p>Persistence to {@code inject_execution / execution_traces} remains C1-Platform-3 scope; this
 * service stops at envelope verification + result-list decode + audit write. The returned {@link
 * ImportResult} surfaces the parsed result count back to the operator so the UI can render "agent
 * returned N results" right away even before per-result persistence lands.
 */
@Service
@Slf4j
public class OfflinePackImportService {

  private final PlatformIdentityService platformIdentity;
  private final AgentOnboardingService onboardingService;
  private final VresultsTaskResultParser vresultsParser;
  private final OfflinePackAuditService auditService;

  public OfflinePackImportService(
      PlatformIdentityService platformIdentity,
      AgentOnboardingService onboardingService,
      VresultsTaskResultParser vresultsParser,
      OfflinePackAuditService auditService) {
    this.platformIdentity = platformIdentity;
    this.onboardingService = onboardingService;
    this.vresultsParser = vresultsParser;
    this.auditService = auditService;
  }

  /**
   * Decode an uploaded {@code .vresults} envelope.
   *
   * @param envelopeBytes raw UTF-8 bytes of the file the operator uploaded
   * @param onboardToken active onboard token for the agent (used to look up the expected agent
   *     Ed25519 signing public key)
   * @param importedBy admin operator username (audit attribution)
   * @param clientIp immediate connection IP (audit attribution; never trust X-Forwarded-For)
   * @return an {@link ImportResult} with one of two shapes: an {@link ImportResult#errors} list
   *     populated (preserving the existing 400 contract on the controller side) or a success result
   *     with {@code packId / importedCount / rejectedCount} set
   */
  public ImportResult importPack(
      byte[] envelopeBytes, String onboardToken, String importedBy, String clientIp) {
    if (envelopeBytes == null) {
      return ImportResult.failure("file_read_error", List.of("file_read_error: null body"));
    }

    Optional<AgentOnboardingService.AgentOnboardingState> stateOpt =
        onboardingService.findByToken(onboardToken);
    if (stateOpt.isEmpty()) {
      return ImportResult.failure("onboard_token_invalid", List.of("onboard_token_invalid"));
    }
    AgentOnboardingService.AgentOnboardingState state = stateOpt.get();
    if (state.agentSignPub() == null) {
      return ImportResult.failure("agent_not_registered", List.of("agent_not_registered"));
    }

    VresultsTaskResultParser.ParsedTaskResults parsed;
    try {
      parsed =
          vresultsParser.parse(
              envelopeBytes, state.agentSignPub(), platformIdentity.getPlatformEncPriv());
    } catch (VpackSerializer.SignatureVerificationException
        | VpackSerializer.SchemaVersionException
        | VpackSerializer.VpackParseException ex) {
      return ImportResult.failure(
          "envelope_invalid", List.of("envelope_invalid: " + ex.getMessage()));
    } catch (X25519BoxService.BoxOpenException ex) {
      return ImportResult.failure(
          "envelope_decrypt_failed", List.of("envelope_decrypt_failed: " + ex.getMessage()));
    } catch (VresultsTaskResultParser.ResultListParseException ex) {
      return ImportResult.failure(
          "result_list_invalid", List.of("result_list_invalid: " + ex.getMessage()));
    }

    if (!parsed.metadata().agentId().equals(state.agentId())) {
      return ImportResult.failure(
          parsed.metadata().packId().toString(),
          0,
          parsed.metadata().resultCount(),
          List.of("agent_id_mismatch"));
    }

    // result_count claimed by the envelope vs actual decoded list length — small sanity check;
    // mismatch means the agent emitted a metadata block that is out-of-sync with its body. Treat
    // as a soft warning surfaced in errors[] but still proceed with the decoded list as truth.
    List<String> warnings = new ArrayList<>();
    if (parsed.metadata().resultCount() != parsed.results().size()) {
      warnings.add(
          "metadata_result_count_mismatch: metadata="
              + parsed.metadata().resultCount()
              + " decoded="
              + parsed.results().size());
    }

    // Audit — recordImport tolerates a missing export row (logs WARN + returns empty). We always
    // call it so successful imports are observable in audit, and the lack of a matching export is
    // diagnosable from the same row search.
    auditService.recordImport(
        parsed.metadata().packId(),
        importedBy,
        clientIp,
        parsed.results().size(),
        0); // rejected_count is 0 until C1-Platform-3 persistence introduces per-row rejection

    return new ImportResult(
        parsed.metadata().packId().toString(),
        parsed.results().size(),
        0,
        warnings,
        parsed.results());
  }

  /**
   * Successful or partial import outcome.
   *
   * <p>{@code results} is the decoded list — the controller currently does not need it (it only
   * returns count + errors to keep the existing wire response) but it is exposed here for
   * forthcoming C1-Platform-3 callers that will persist each result into {@code inject_execution}.
   */
  public record ImportResult(
      String packId,
      int importedCount,
      int rejectedCount,
      List<String> errors,
      List<AgentDtos.ResultInput> results) {

    /** Build a hard-failure result with no results decoded yet. */
    public static ImportResult failure(String packId, List<String> errors) {
      return new ImportResult(packId, 0, 0, errors, List.of());
    }

    /** Build a partial-failure result (envelope parsed but agent_id mismatched, etc.). */
    public static ImportResult failure(
        String packId, int importedCount, int rejectedCount, List<String> errors) {
      return new ImportResult(packId, importedCount, rejectedCount, errors, List.of());
    }

    /** {@code true} when there were no fatal errors (errors list is empty or only warnings). */
    public boolean isSuccess() {
      return errors.isEmpty();
    }
  }
}
