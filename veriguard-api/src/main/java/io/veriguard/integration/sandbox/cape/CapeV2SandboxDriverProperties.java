package io.veriguard.integration.sandbox.cape;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration bundle for {@link CapeV2SandboxDriver} (M2).
 *
 * <p>Bound to {@code veriguard.sandbox.cape.*} via Spring Boot {@link ConfigurationProperties}. The
 * driver is registered as the {@code @Primary} {@code SandboxDriver} only when {@code endpoint} is
 * populated (see {@link CapeV2SandboxDriver}'s {@code @ConditionalOnProperty}); otherwise the M1
 * {@code NotImplementedSandboxDriver} stays in charge so dev / CI environments that have no CAPEv2
 * reachable keep building.
 *
 * <p>The prefix matches the M1 placeholders that already exist in {@code application.properties};
 * only {@code endpoint} + auth fields are new.
 *
 * <h2>Credentials policy</h2>
 *
 * <p>Per {@code .github/instructions/security.instructions.md} + project CLAUDE.md, secrets MUST
 * NOT appear in source. The auth fields below are <strong>optional</strong> and read from the dev /
 * prod profile only — the checked-in {@code application.properties} ships commented placeholders
 * that reference {@code ${VERIGUARD_SANDBOX_CAPE_*}} env vars.
 *
 * <ul>
 *   <li>{@code api-token} — sent as {@code Authorization: Token <value>} (CAPEv2 native API token;
 *       see {@code apiconf.conf}).
 *   <li>{@code basic-auth-user} + {@code basic-auth-pass} — sent as {@code Authorization: Basic
 *       <base64(user:pass)>} for deployments behind an nginx reverse proxy that handles HTTP basic
 *       auth.
 * </ul>
 *
 * <p>If both modes are configured, token wins. If neither, no {@code Authorization} header is sent
 * (which is fine for the CAPEv2 default no-auth deployment).
 */
@ConfigurationProperties(prefix = "veriguard.sandbox.cape")
public record CapeV2SandboxDriverProperties(
    String endpoint,
    String apiToken,
    String basicAuthUser,
    String basicAuthPass,
    Duration connectTimeout,
    Duration readTimeout,
    Duration sampleSubmitTimeout,
    Boolean verifyTls) {

  /** Apply defaults so the driver can use the record directly without null guards. */
  public CapeV2SandboxDriverProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(5);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(30);
    }
    if (sampleSubmitTimeout == null) {
      sampleSubmitTimeout = readTimeout;
    }
    if (verifyTls == null) {
      verifyTls = Boolean.TRUE;
    }
  }

  /** Strip trailing slash from endpoint so endpoint composition is unambiguous. */
  public String normalizedEndpoint() {
    if (endpoint == null || endpoint.isBlank()) {
      return null;
    }
    return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
  }

  /** Returns true when either auth mode has enough fields to send an Authorization header. */
  public boolean hasAuth() {
    return (apiToken != null && !apiToken.isBlank())
        || (basicAuthUser != null
            && !basicAuthUser.isBlank()
            && basicAuthPass != null
            && !basicAuthPass.isBlank());
  }
}
