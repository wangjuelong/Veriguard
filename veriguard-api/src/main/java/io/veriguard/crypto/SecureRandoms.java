package io.veriguard.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Single source of truth for picking a {@link SecureRandom} instance across Veriguard crypto code.
 *
 * <p>Policy — prefer {@link SecureRandom#getInstanceStrong()} (blocking, backed by {@code
 * /dev/random} on Linux). On the extreme legacy environment where {@code getInstanceStrong} blows
 * up (no strong algorithm registered), fall back to the default {@link SecureRandom} (urandom)
 * rather than letting the Spring context fail to start.
 *
 * <p>Rationale — both {@link Ed25519SignatureService} and {@link X25519BoxService} already used
 * this exact two-step policy via private {@code pickRandom()} methods; {@link
 * io.veriguard.rest.agent.AgentOnboardingService} (which generates the highest-value secret in the
 * agent flow — the 32-byte onboard token) was using plain {@code new SecureRandom()}, which is
 * inconsistent. This utility consolidates the three call sites onto a single, audit-friendly
 * policy.
 */
public final class SecureRandoms {

  private SecureRandoms() {}

  /**
   * Return a {@link SecureRandom} suitable for cryptographic key / token generation. Prefers {@link
   * SecureRandom#getInstanceStrong()}, falls back to {@code new SecureRandom()} when no strong
   * algorithm is registered.
   */
  public static SecureRandom strongOrDefault() {
    try {
      return SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException ex) {
      return new SecureRandom();
    }
  }
}
