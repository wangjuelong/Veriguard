package io.veriguard.rest.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.crypto.Ed25519SignatureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Veriguard Agent (C1) task queue REST — Mode A polling + result post (spec §3.5.1 Mode A).
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/agent/poll?agent_id=X&capabilities=...} — returns pending tasks
 *   <li>{@code POST /api/agent/task/{taskId}/result} — agent uploads execution result
 * </ul>
 *
 * <p>Both endpoints expect {@code X-Veriguard-Signature: <base64 Ed25519 sig over body+timestamp>}
 * header. The {@code X-Veriguard-Timestamp} header carries a Unix epoch millis. The signature input
 * is {@code utf8(timestamp) || rawBody} (rawBody empty for GET).
 *
 * <p><strong>Scaffold mode</strong>: actual signature verification requires looking up the agent's
 * Ed25519 pub key, which lives on the agent_onboarding state (not yet persisted to JPA — see {@link
 * AgentOnboardingService}). For this PR, signature verification is gated on whether the agent_id
 * has been registered (via {@link AgentOnboardingService#findByToken} look-ups would need an
 * inverse index by agent_id which we don't have yet). Tests exercise the gating by registering an
 * agent first and reusing the same key pair.
 *
 * <p>TODO C1-Platform-2:
 *
 * <ul>
 *   <li>Persist agent crypto state to {@code agents.agent_sign_pubkey / agent_enc_pubkey} columns
 *       (V19 schema already in place)
 *   <li>Wire {@code AgentRepository} lookup so any agent_id can be verified
 *   <li>Connect to real {@code Inject} / {@code InjectExecution} dispatch
 * </ul>
 */
@RestController
public class AgentTaskQueueApi {

  public static final String POLL_URI = "/api/agent/poll";
  public static final String RESULT_URI = "/api/agent/task/{taskId}/result";

  /** Header carrying the Ed25519 signature over {@code utf8(timestamp) || rawBody}. */
  public static final String SIGNATURE_HEADER = "X-Veriguard-Signature";

  /** Header carrying the Unix epoch millis as a decimal string. */
  public static final String TIMESTAMP_HEADER = "X-Veriguard-Timestamp";

  private final AgentTaskQueueService queueService;
  private final AgentOnboardingService onboardingService;
  private final Ed25519SignatureService ed25519;
  private final ObjectMapper canonicalMapper;

  public AgentTaskQueueApi(
      AgentTaskQueueService queueService,
      AgentOnboardingService onboardingService,
      Ed25519SignatureService ed25519,
      ObjectMapper objectMapper) {
    this.queueService = queueService;
    this.onboardingService = onboardingService;
    this.ed25519 = ed25519;
    // Sorted-keys + no-whitespace canonical mapper, matching VpackSerializer / VresultsSerializer
    // style. Result signature input is byte-deterministic across Java + Rust agent.
    this.canonicalMapper =
        objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  @GetMapping(POLL_URI)
  @Operation(summary = "Veriguard Agent — poll for pending tasks (Mode A)")
  @RBAC(skipRBAC = true)
  public ResponseEntity<AgentDtos.PollOutput> poll(
      @RequestParam("agent_id") @NotBlank String agentId,
      @RequestHeader(value = SIGNATURE_HEADER, required = false) String signatureB64,
      @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
      @RequestHeader(value = "X-Veriguard-Onboard-Token", required = false) String onboardToken) {
    if (!verifyAgentSignature(agentId, onboardToken, timestamp, new byte[0], signatureB64)) {
      return ResponseEntity.status(401).build();
    }
    List<AgentDtos.AgentTask> tasks = queueService.drainTasks(agentId);
    return ResponseEntity.ok(new AgentDtos.PollOutput(tasks));
  }

  @PostMapping(value = RESULT_URI, consumes = "application/json")
  @Operation(summary = "Veriguard Agent — upload task execution result")
  @RBAC(skipRBAC = true)
  public ResponseEntity<AgentDtos.ResultOutput> result(
      @PathVariable("taskId") @NotBlank String taskId,
      @RequestParam("agent_id") @NotBlank String agentId,
      @RequestHeader(value = SIGNATURE_HEADER, required = false) String signatureB64,
      @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
      @RequestHeader(value = "X-Veriguard-Onboard-Token", required = false) String onboardToken,
      @Valid @RequestBody AgentDtos.ResultInput input) {
    // For signature verification we need the raw body bytes; in Spring MVC with @RequestBody
    // we have already parsed JSON. We rebuild a canonical byte representation covering ALL fields
    // the controller persists (task_id + 7 ResultInput fields) so the agent's Ed25519 signature
    // binds to every field the platform stores. Rust agent MUST construct the same canonical form
    // before signing. TODO C1-Platform-2: switch to ContentCachingRequestWrapper so the agent can
    // sign the actual raw bytes the platform parses.
    byte[] canonicalBody = canonicalResultBytes(taskId, input);
    if (!verifyAgentSignature(agentId, onboardToken, timestamp, canonicalBody, signatureB64)) {
      return ResponseEntity.status(401).build();
    }
    queueService.acceptResult(taskId, agentId, input);
    return ResponseEntity.ok(new AgentDtos.ResultOutput("accepted"));
  }

  /**
   * Build the canonical byte representation of a result payload for Ed25519 signing/verification.
   *
   * <p>Covers all 8 fields the controller binds: {@code task_id} (path) plus the 7 {@link
   * AgentDtos.ResultInput} body fields ({@code error_message}, {@code exit_code}, {@code
   * finished_at}, {@code started_at}, {@code status}, {@code stderr}, {@code stdout}). Serialized
   * as sorted-keys, no-whitespace JSON (consistent with {@link io.veriguard.crypto.VpackSerializer}
   * / {@link io.veriguard.crypto.VresultsSerializer} canonical style).
   *
   * <p>Null body fields are coerced to empty strings to keep the byte representation deterministic;
   * the Rust agent must do the same.
   */
  private byte[] canonicalResultBytes(String taskId, AgentDtos.ResultInput body) {
    ObjectNode canonical = canonicalMapper.getNodeFactory().objectNode();
    canonical.put("error_message", body.errorMessage() != null ? body.errorMessage() : "");
    canonical.put("exit_code", body.exitCode());
    canonical.put("finished_at", body.finishedAt() != null ? body.finishedAt() : "");
    canonical.put("started_at", body.startedAt() != null ? body.startedAt() : "");
    canonical.put("status", body.status());
    canonical.put("stderr", body.stderr() != null ? body.stderr() : "");
    canonical.put("stdout", body.stdout() != null ? body.stdout() : "");
    canonical.put("task_id", taskId);
    try {
      return canonicalMapper.writeValueAsBytes(canonical);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to canonicalize result signature input", ex);
    }
  }

  /**
   * Verify the agent's signature. Scaffold lookup: uses {@code onboardToken} header to locate the
   * agent state (since we don't persist by agent_id to JPA yet).
   *
   * @return true if signature verifies, false otherwise
   */
  private boolean verifyAgentSignature(
      String agentId, String onboardToken, String timestamp, byte[] body, String signatureB64) {
    if (signatureB64 == null
        || onboardToken == null
        || timestamp == null
        || signatureB64.isBlank()) {
      return false;
    }
    Optional<AgentOnboardingService.AgentOnboardingState> state =
        onboardingService.findByToken(onboardToken);
    if (state.isEmpty() || state.get().agentSignPub() == null) {
      return false;
    }
    if (!state.get().agentId().equals(agentId)) {
      return false;
    }
    byte[] tsBytes = timestamp.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] signedInput = new byte[tsBytes.length + body.length];
    System.arraycopy(tsBytes, 0, signedInput, 0, tsBytes.length);
    System.arraycopy(body, 0, signedInput, tsBytes.length, body.length);
    byte[] sig = Base64.getDecoder().decode(signatureB64);
    try {
      return ed25519.verify(state.get().agentSignPub(), signedInput, sig);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
