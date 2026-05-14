package io.veriguard.rest.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** DTOs for the Veriguard Agent (C1) REST endpoints under {@code /api/agent/...}. */
public final class AgentDtos {

  private AgentDtos() {}

  // --- /api/agent/onboard/init ---

  public record InitInput(
      @JsonProperty("display_name") @NotBlank String displayName,
      @JsonProperty("capabilities") @NotNull List<String> capabilities,
      @JsonProperty("allowed_modes") @NotNull List<String> allowedModes) {}

  public record InitOutput(
      @JsonProperty("agent_id") String agentId,
      @JsonProperty("onboard_token") String onboardToken,
      @JsonProperty("platform_ed25519_pub_b64") String platformEd25519PubB64,
      @JsonProperty("platform_x25519_pub_b64") String platformX25519PubB64,
      @JsonProperty("platform_id") String platformId,
      @JsonProperty("platform_url") String platformUrl,
      @JsonProperty("platform_cert_fingerprint_sha256") String platformCertFingerprintSha256,
      @JsonProperty("ttl_seconds") long ttlSeconds) {}

  // --- /api/agent/onboard/register ---

  public record RegisterInput(
      @JsonProperty("agent_id") @NotBlank String agentId,
      @JsonProperty("onboard_token") @NotBlank String onboardToken,
      @JsonProperty("agent_ed25519_pub_b64") @NotBlank String agentEd25519PubB64,
      @JsonProperty("agent_x25519_pub_b64") @NotBlank String agentX25519PubB64,
      @JsonProperty("capabilities") @NotNull List<String> capabilities,
      @JsonProperty("registration_sig_b64") @NotBlank String registrationSigB64) {}

  public record RegisterOutput(
      @JsonProperty("status") String status, @JsonProperty("agent_id") String agentId) {}

  // --- /api/agent/onboard/bootstrap ---

  public record BootstrapInput(
      @JsonProperty("onboard_token") @NotBlank String onboardToken) {}

  public record BootstrapOutput(
      @JsonProperty("install_pack") InstallPack installPack) {}

  public record InstallPack(
      @JsonProperty("agent_id") String agentId,
      @JsonProperty("onboard_token") String onboardToken,
      @JsonProperty("platform_ed25519_pub_b64") String platformEd25519PubB64,
      @JsonProperty("platform_x25519_pub_b64") String platformX25519PubB64,
      @JsonProperty("platform_id") String platformId,
      @JsonProperty("platform_url") String platformUrl,
      @JsonProperty("capabilities") List<String> capabilities,
      @JsonProperty("allowed_modes") List<String> allowedModes,
      @JsonProperty("ttl_seconds") long ttlSeconds) {}

  // --- /api/agent/poll  ---

  public record PollOutput(@JsonProperty("tasks") List<AgentTask> tasks) {}

  public record AgentTask(
      @JsonProperty("task_id") String taskId,
      @JsonProperty("capability") String capability,
      @JsonProperty("injector_type") String injectorType,
      @JsonProperty("payload") String payloadJson,
      @JsonProperty("expectations") List<String> expectations) {}

  // --- /api/agent/task/{taskId}/result ---

  public record ResultInput(
      @JsonProperty("status") @NotBlank String status,
      @JsonProperty("exit_code") int exitCode,
      @JsonProperty("stdout") String stdout,
      @JsonProperty("stderr") String stderr,
      @JsonProperty("started_at") String startedAt,
      @JsonProperty("finished_at") String finishedAt,
      @JsonProperty("error_message") String errorMessage) {}

  public record ResultOutput(@JsonProperty("status") String status) {}

  // --- /api/agent/offline-pack/{export,import} ---

  public record OfflinePackExportInput(
      @JsonProperty("agent_id") @NotBlank String agentId,
      @JsonProperty("max_tasks") @Min(0) int maxTasks) {}

  public record OfflinePackImportOutput(
      @JsonProperty("pack_id") String packId,
      @JsonProperty("imported_count") int importedCount,
      @JsonProperty("rejected_count") int rejectedCount,
      @JsonProperty("errors") List<String> errors) {}
}
