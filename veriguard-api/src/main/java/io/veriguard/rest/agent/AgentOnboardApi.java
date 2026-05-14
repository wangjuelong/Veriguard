package io.veriguard.rest.agent;

import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import jakarta.validation.Valid;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Veriguard Agent (C1) onboarding REST endpoints — 3-step flow per spec §3.5.1:
 *
 * <ol>
 *   <li>{@code POST /api/agent/onboard/init} — admin creates an onboard intent
 *   <li>{@code POST /api/agent/onboard/register} — agent re-attests with its 2 fresh public keys
 *   <li>{@code POST /api/agent/onboard/bootstrap} — exchange a token for the install pack
 * </ol>
 *
 * <p>Security model:
 *
 * <ul>
 *   <li>{@code /init} — admin-only (RBAC: WRITE on AGENT)
 *   <li>{@code /register} — agent self-service; signature-protected (the agent proves possession of
 *       the X25519 + Ed25519 priv keys via the {@code registration_sig_b64} field)
 *   <li>{@code /bootstrap} — agent self-service; token-protected (only the one-time onboard_token
 *       holder can fetch the install pack)
 * </ul>
 *
 * <p>{@code AppSecurityConfig} already maps {@code /api/agent/**} as {@code permitAll()};
 * per-endpoint auth is handled at the controller layer (token + signature checks).
 */
@RestController
public class AgentOnboardApi {

  public static final String ONBOARD_INIT_URI = "/api/agent/onboard/init";
  public static final String ONBOARD_REGISTER_URI = "/api/agent/onboard/register";
  public static final String ONBOARD_BOOTSTRAP_URI = "/api/agent/onboard/bootstrap";

  private final AgentOnboardingService onboardingService;
  private final PlatformIdentityService platformIdentity;

  public AgentOnboardApi(
      AgentOnboardingService onboardingService, PlatformIdentityService platformIdentity) {
    this.onboardingService = onboardingService;
    this.platformIdentity = platformIdentity;
  }

  /**
   * Admin endpoint — creates a new agent onboarding intent.
   *
   * <p>Returns the agent_id (UUID) + one-time onboard_token + platform public keys + URL. The
   * platform_cert_fingerprint_sha256 field is currently empty (TLS pinning configuration is
   * deferred to C1-Platform-2 — to be wired through Spring's SSLContextFactory).
   */
  @PostMapping(ONBOARD_INIT_URI)
  @Operation(summary = "Create a new Veriguard Agent onboarding intent (admin)")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.AGENT)
  public ResponseEntity<AgentDtos.InitOutput> init(@Valid @RequestBody AgentDtos.InitInput input) {
    AgentOnboardingService.AgentOnboardingState state =
        onboardingService.init(input.displayName(), input.capabilities(), input.allowedModes());

    return ResponseEntity.ok(
        new AgentDtos.InitOutput(
            state.agentId(),
            state.onboardToken(),
            Base64.getEncoder().encodeToString(platformIdentity.getPlatformSignPub()),
            Base64.getEncoder().encodeToString(platformIdentity.getPlatformEncPub()),
            platformIdentity.getPlatformId(),
            platformIdentity.getPlatformUrl(),
            // TODO C1-Platform-2: replace with actual TLS fingerprint loaded from
            // SSLContextFactory once HTTPS termination is configured.
            "",
            AgentOnboardingService.ONBOARD_TOKEN_TTL.toSeconds()));
  }

  /**
   * Agent endpoint — submit two fresh public keys + a signature proving private-key possession.
   *
   * <p>Verifies:
   *
   * <ul>
   *   <li>onboard_token exists and is not expired
   *   <li>agent_id matches the one returned at init time
   *   <li>registration_sig_b64 is a valid Ed25519 signature over {@code token || agentSignPub ||
   *       agentEncPub} (verified with the just-submitted agent Ed25519 pub key)
   * </ul>
   */
  @PostMapping(ONBOARD_REGISTER_URI)
  @Operation(summary = "Register a Veriguard Agent — submit pub keys + signed proof")
  @RBAC(skipRBAC = true)
  public ResponseEntity<AgentDtos.RegisterOutput> register(
      @Valid @RequestBody AgentDtos.RegisterInput input) {
    byte[] agentSignPub = Base64.getDecoder().decode(input.agentEd25519PubB64());
    byte[] agentEncPub = Base64.getDecoder().decode(input.agentX25519PubB64());
    byte[] registrationSig = Base64.getDecoder().decode(input.registrationSigB64());

    Optional<AgentOnboardingService.AgentOnboardingState> existing =
        onboardingService.findByToken(input.onboardToken());
    if (existing.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new AgentDtos.RegisterOutput("onboard_token_invalid_or_expired", input.agentId()));
    }
    if (!existing.get().agentId().equals(input.agentId())) {
      return ResponseEntity.badRequest()
          .body(new AgentDtos.RegisterOutput("agent_id_mismatch", input.agentId()));
    }

    Optional<AgentOnboardingService.AgentOnboardingState> registered;
    try {
      registered =
          onboardingService.register(
              input.onboardToken(), agentSignPub, agentEncPub, registrationSig);
    } catch (AgentOnboardingService.RegistrationSignatureInvalidException ex) {
      return ResponseEntity.status(401)
          .body(new AgentDtos.RegisterOutput("registration_signature_invalid", input.agentId()));
    }
    if (registered.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new AgentDtos.RegisterOutput("token_already_used", input.agentId()));
    }
    return ResponseEntity.ok(
        new AgentDtos.RegisterOutput("registered", registered.get().agentId()));
  }

  /**
   * Agent endpoint — exchange the one-time onboard_token for the full install_pack. Used by the
   * Rust agent's {@code --bootstrap} subcommand (typically invoked from a one-line {@code curl |
   * sh} install script).
   */
  @PostMapping(ONBOARD_BOOTSTRAP_URI)
  @Operation(summary = "Bootstrap — exchange onboard_token for install_pack")
  @RBAC(skipRBAC = true)
  public ResponseEntity<AgentDtos.BootstrapOutput> bootstrap(
      @Valid @RequestBody AgentDtos.BootstrapInput input) {
    Optional<AgentOnboardingService.AgentOnboardingState> state =
        onboardingService.bootstrap(input.onboardToken());
    if (state.isEmpty()) {
      return ResponseEntity.status(404).build();
    }

    AgentOnboardingService.AgentOnboardingState s = state.get();
    AgentDtos.InstallPack pack =
        new AgentDtos.InstallPack(
            s.agentId(),
            s.onboardToken(),
            Base64.getEncoder().encodeToString(platformIdentity.getPlatformSignPub()),
            Base64.getEncoder().encodeToString(platformIdentity.getPlatformEncPub()),
            platformIdentity.getPlatformId(),
            platformIdentity.getPlatformUrl(),
            s.capabilities(),
            s.allowedModes(),
            AgentOnboardingService.ONBOARD_TOKEN_TTL.toSeconds());
    return ResponseEntity.ok(new AgentDtos.BootstrapOutput(pack));
  }
}
