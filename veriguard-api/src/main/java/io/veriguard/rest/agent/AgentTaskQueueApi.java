package io.veriguard.rest.agent;

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
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;

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
 * header. The {@code X-Veriguard-Timestamp} header carries a Unix epoch millis. The signature
 * input is {@code utf8(timestamp) || rawBody} (rawBody empty for GET).
 *
 * <p><strong>Scaffold mode</strong>: actual signature verification requires looking up the agent's
 * Ed25519 pub key, which lives on the agent_onboarding state (not yet persisted to JPA — see
 * {@link AgentOnboardingService}). For this PR, signature verification is gated on whether the
 * agent_id has been registered (via {@link AgentOnboardingService#findByToken} look-ups would
 * need an inverse index by agent_id which we don't have yet). Tests exercise the gating by
 * registering an agent first and reusing the same key pair.
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

  public AgentTaskQueueApi(
      AgentTaskQueueService queueService,
      AgentOnboardingService onboardingService,
      Ed25519SignatureService ed25519) {
    this.queueService = queueService;
    this.onboardingService = onboardingService;
    this.ed25519 = ed25519;
  }

  @GetMapping(POLL_URI)
  @Operation(summary = "Veriguard Agent — poll for pending tasks (Mode A)")
  @RBAC(skipRBAC = true)
  public ResponseEntity<AgentDtos.PollOutput> poll(
      @RequestParam("agent_id") @NotBlank String agentId,
      @RequestHeader(value = SIGNATURE_HEADER, required = false) String signatureB64,
      @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
      @RequestHeader(value = "X-Veriguard-Onboard-Token", required = false) String onboardToken,
      WebRequest req) {
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
      @Valid @RequestBody AgentDtos.ResultInput input,
      WebAsyncManager asyncManager) {
    // For signature verification we need the raw body bytes; in Spring MVC with @RequestBody
    // we have already parsed JSON. For this scaffold we sign over the canonical JSON re-serialized
    // to a deterministic representation (status||taskId pair) — Rust agent must use the same
    // canonical form. Real-world implementation should use a HttpServletRequest filter to
    // capture raw body bytes before JSON parsing.
    byte[] canonicalBody =
        (taskId + ":" + input.status() + ":" + input.exitCode())
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (!verifyAgentSignature(agentId, onboardToken, timestamp, canonicalBody, signatureB64)) {
      return ResponseEntity.status(401).build();
    }
    queueService.acceptResult(taskId, agentId, input);
    return ResponseEntity.ok(new AgentDtos.ResultOutput("accepted"));
  }

  /**
   * Verify the agent's signature. Scaffold lookup: uses {@code onboardToken} header to locate the
   * agent state (since we don't persist by agent_id to JPA yet).
   *
   * @return true if signature verifies, false otherwise
   */
  private boolean verifyAgentSignature(
      String agentId,
      String onboardToken,
      String timestamp,
      byte[] body,
      String signatureB64) {
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
