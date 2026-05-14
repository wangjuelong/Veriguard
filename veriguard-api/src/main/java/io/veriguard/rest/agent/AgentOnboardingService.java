package io.veriguard.rest.agent;

import io.veriguard.crypto.Ed25519SignatureService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Stateful service for the 3-step Veriguard Agent onboarding flow:
 *
 * <ol>
 *   <li>{@link #init} — admin creates a new agent_id + onboard_token, returns platform pubs
 *   <li>{@link #bootstrap} — exchange a one-time token for the install_pack (one-line curl path)
 *   <li>{@link #register} — agent re-attests with its 2 fresh pub keys + signed proof, sets pubs
 * </ol>
 *
 * <p><strong>Scaffold mode</strong>: tokens are stored in-memory ({@link ConcurrentHashMap}) with
 * TTL 24h. Single-node deploy only — multi-node coordination + DB persistence is C1-Platform-2
 * scope. The longer-term persistence target is the {@code agents} table加密列 (V19) — but Agent JPA
 * row creation requires {@code agent_asset / executor / privilege} that are not in the spec's
 * onboard body; this PR defers that wiring intentionally.
 *
 * <p>Thread safety — backed by {@link ConcurrentHashMap}; mutator methods are atomic per key.
 */
@Service
public class AgentOnboardingService {

  /** Onboard token TTL — spec §3.5.1 init response.ttl_seconds. */
  public static final Duration ONBOARD_TOKEN_TTL = Duration.ofHours(24);

  /** Onboard token byte length (256-bit entropy → 64 hex chars). */
  public static final int ONBOARD_TOKEN_BYTES = 32;

  private final Ed25519SignatureService ed25519;
  private final SecureRandom secureRandom = new SecureRandom();

  /** token -> state. Lookup is O(1); pruning expired entries is opportunistic on next read. */
  private final Map<String, AgentOnboardingState> byToken = new ConcurrentHashMap<>();

  public AgentOnboardingService(Ed25519SignatureService ed25519) {
    this.ed25519 = ed25519;
  }

  /**
   * Create a new onboarding intent.
   *
   * @return state record containing agent_id (UUID) and one-time onboard_token (hex 64 chars)
   */
  public AgentOnboardingState init(
      String displayName, List<String> capabilities, List<String> allowedModes) {
    String agentId = UUID.randomUUID().toString();
    String token = newToken();
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(ONBOARD_TOKEN_TTL);
    AgentOnboardingState state =
        new AgentOnboardingState(
            agentId,
            token,
            displayName,
            List.copyOf(capabilities == null ? List.of() : capabilities),
            List.copyOf(allowedModes == null ? List.of() : allowedModes),
            issuedAt,
            expiresAt,
            null,
            null,
            null);
    byToken.put(token, state);
    return state;
  }

  /**
   * Look up a token (must be unused/unexpired).
   *
   * @return present iff token exists and is not expired
   */
  public Optional<AgentOnboardingState> findByToken(String token) {
    AgentOnboardingState state = byToken.get(token);
    if (state == null) {
      return Optional.empty();
    }
    if (state.isExpired(Instant.now())) {
      byToken.remove(token);
      return Optional.empty();
    }
    return Optional.of(state);
  }

  /**
   * Bootstrap fetch — same as {@link #findByToken} but encapsulates the semantics that the token is
   * being exchanged for an install_pack. Token is NOT consumed (register step consumes it).
   */
  public Optional<AgentOnboardingState> bootstrap(String token) {
    return findByToken(token);
  }

  /**
   * Register an agent with its fresh pub keys + a registration signature.
   *
   * @param token the onboard_token returned from {@link #init}
   * @param agentSignPub agent Ed25519 public key (32 bytes)
   * @param agentEncPub agent X25519 public key (32 bytes)
   * @param registrationSig agent-signed proof of pub key possession (signature over canonical bytes
   *     {@code token || agentSignPub || agentEncPub})
   * @return registered state (with agent_id), or empty if token invalid / expired
   * @throws RegistrationSignatureInvalidException if {@code registrationSig} does not verify
   */
  public Optional<AgentOnboardingState> register(
      String token, byte[] agentSignPub, byte[] agentEncPub, byte[] registrationSig) {
    Optional<AgentOnboardingState> existing = findByToken(token);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    AgentOnboardingState state = existing.get();
    if (state.onboardedAt() != null) {
      // Already registered — reject (token is one-time).
      return Optional.empty();
    }

    byte[] signedBytes = registrationSignedBytes(token, agentSignPub, agentEncPub);
    if (!ed25519.verify(agentSignPub, signedBytes, registrationSig)) {
      throw new RegistrationSignatureInvalidException(
          "Registration signature failed Ed25519 verification");
    }

    AgentOnboardingState registered =
        new AgentOnboardingState(
            state.agentId(),
            token,
            state.displayName(),
            state.capabilities(),
            state.allowedModes(),
            state.issuedAt(),
            state.expiresAt(),
            agentSignPub.clone(),
            agentEncPub.clone(),
            Instant.now());
    byToken.put(token, registered);
    return Optional.of(registered);
  }

  /**
   * Compose the canonical bytes the agent signs at register time. Format (length-prefix free,
   * fixed-order concatenation): {@code utf8(token) || agentSignPub || agentEncPub}.
   *
   * <p>Rust agent 必须用同顺序构造 sign input — 任一偏离 verify 失败.
   */
  public static byte[] registrationSignedBytes(
      String token, byte[] agentSignPub, byte[] agentEncPub) {
    byte[] tokenBytes = token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] out = new byte[tokenBytes.length + agentSignPub.length + agentEncPub.length];
    System.arraycopy(tokenBytes, 0, out, 0, tokenBytes.length);
    System.arraycopy(agentSignPub, 0, out, tokenBytes.length, agentSignPub.length);
    System.arraycopy(
        agentEncPub, 0, out, tokenBytes.length + agentSignPub.length, agentEncPub.length);
    return out;
  }

  private String newToken() {
    byte[] raw = new byte[ONBOARD_TOKEN_BYTES];
    secureRandom.nextBytes(raw);
    return HexFormat.of().formatHex(raw);
  }

  /** State of an onboarding intent. Immutable record. */
  public record AgentOnboardingState(
      String agentId,
      String onboardToken,
      String displayName,
      List<String> capabilities,
      List<String> allowedModes,
      Instant issuedAt,
      Instant expiresAt,
      byte[] agentSignPub,
      byte[] agentEncPub,
      Instant onboardedAt) {

    public boolean isExpired(Instant now) {
      return now.isAfter(expiresAt);
    }
  }

  /** Thrown when registration signature verification fails. */
  public static class RegistrationSignatureInvalidException extends RuntimeException {
    public RegistrationSignatureInvalidException(String message) {
      super(message);
    }
  }
}
