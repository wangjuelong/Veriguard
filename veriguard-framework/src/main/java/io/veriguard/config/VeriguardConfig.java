package io.veriguard.config;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Main configuration class for the Veriguard platform.
 *
 * <p>This component holds the core application configuration including:
 *
 * <ul>
 *   <li>Application identity (name, version, instance ID)
 *   <li>URLs (base URL, frontend URL, agent URL)
 *   <li>Authentication settings (local, OpenID, SAML2, Kerberos)
 *   <li>Security settings (cookies, certificates)
 *   <li>Map tile server configuration
 *   <li>Queue configuration for background processing
 * </ul>
 *
 * <p>Configuration can be provided via properties with either {@code openbas.*} or {@code
 * veriguard.*} prefixes for backward compatibility.
 */
@Component
@Data
@ConfigurationProperties(prefix = "veriguard")
public class VeriguardConfig {

  @JsonProperty("parameters_id")
  @Value("${openbas.id:${veriguard.id:global}}")
  private String id;

  @JsonProperty("application_name")
  @Value("${openbas.name:${veriguard.name:Veriguard}}")
  private String name;

  @JsonProperty("application_license")
  @Value("${openbas.application-license:${veriguard.application-license:}}")
  private String applicationLicense;

  @JsonProperty("application_base_url")
  @Value("${openbas.base-url:${veriguard.base-url:#{null}}}")
  private String baseUrl;

  @JsonProperty("application_version")
  @Value("${openbas.version:${veriguard.version:#{null}}}")
  private String version;

  @JsonProperty("map_tile_server_light")
  @Value("${openbas.map-tile-server-light:${veriguard.map-tile-server-light:#{null}}}")
  private String mapTileServerLight;

  @JsonProperty("map_tile_server_dark")
  @Value("${openbas.map-tile-server-dark:${veriguard.map-tile-server-dark:#{null}}}")
  private String mapTileServerDark;

  @JsonProperty("auth_local_enable")
  @Value("${openbas.auth-local-enable:${veriguard.auth-local-enable:false}}")
  private boolean authLocalEnable;

  @JsonProperty("auth_openid_enable")
  @Value("${openbas.auth-openid-enable:${veriguard.auth-openid-enable:false}}")
  private boolean authOpenidEnable;

  @JsonProperty("auth_saml2_enable")
  @Value("${openbas.auth-saml2-enable:${veriguard.auth-saml2-enable:false}}")
  private boolean authSaml2Enable;

  @JsonProperty("auth_kerberos_enable")
  @Value("${openbas.auth-kerberos-enable:${veriguard.auth-kerberos-enable:false}}")
  private boolean authKerberosEnable;

  @JsonProperty("default_mailer")
  @Value("${openbas.default-mailer:${veriguard.default-mailer:#{null}}}")
  private String defaultMailer;

  @JsonProperty("default_reply_to")
  @Value("${openbas.default-reply-to:${veriguard.default-reply-to:#{null}}}")
  private String defaultReplyTo;

  @JsonProperty("admin_token")
  @Value("${openbas.admin.token:${veriguard.admin.token:#{null}}}")
  private String adminToken;

  @JsonProperty("enabled_dev_features")
  @Value("${openbas.enabled-dev-features:${veriguard.enabled-dev-features:}}")
  private String enabledDevFeatures;

  @JsonProperty("instance_id")
  @Value("${openbas.instance-id:${veriguard.instance-id:#{null}}}")
  private String instanceId;

  @JsonIgnore
  @Value("${openbas.cookie-name:${veriguard.cookie-name:veriguard_token}}")
  private String cookieName;

  @JsonIgnore
  @Value("${openbas.cookie-duration:${veriguard.cookie-duration:P1D}}")
  private String cookieDuration;

  @JsonIgnore
  @Value("${openbas.cookie-secure:${veriguard.cookie-secure:false}}")
  private boolean cookieSecure;

  @JsonProperty("application_agent_url")
  @Value("${openbas.agent-url:${veriguard.agent-url:#{null}}}")
  private String agentUrl;

  @JsonProperty("unsecured_certificate")
  @Value("${openbas.unsecured-certificate:${veriguard.unsecured-certificate:false}}")
  private boolean unsecuredCertificate;

  @JsonProperty("with_proxy")
  @Value("${openbas.with-proxy:${veriguard.with-proxy:false}}")
  private boolean withProxy;

  @JsonProperty("max_size")
  @Value("${veriguard.implant-logs-max-size:1000000}")
  private int logsMaxSize;

  @JsonProperty("extra_trusted_certs_dir")
  @Value("${openbas.extra-trusted-certs-dir:${veriguard.extra-trusted-certs-dir:#{null}}}")
  private String extraTrustedCertsDir;

  @JsonProperty("queue-config")
  @Value("${openbas.queue-config:${veriguard.queue-config:#{null}}}")
  private Map<String, QueueConfig> queueConfig;

  @JsonProperty("logout_success_url")
  @Value("${openbas.logout-success-url:${veriguard.logout-success-url:/}}")
  private String logoutSuccessUrl;

  @JsonProperty("frontend_url")
  @Value("${openbas.frontend-url:${veriguard.frontend-url:}}")
  private String frontendUrl;

  /**
   * Returns the normalized base URL for the platform.
   *
   * <p>The URL is normalized by removing any trailing slash.
   *
   * @return the base URL without trailing slash, or null if not configured
   */
  public String getBaseUrl() {
    return normalizeUrl(baseUrl);
  }

  /**
   * Returns the URL that agents should use to connect to the platform.
   *
   * <p>If an explicit agent URL is configured, it will be used. Otherwise, falls back to the base
   * URL. This allows configuring a different endpoint for agent communication (e.g., for network
   * segregation or load balancing).
   *
   * @return the agent URL without trailing slash, or the base URL if agent URL is not configured
   */
  public String getBaseUrlForAgent() {
    return hasText(agentUrl) ? normalizeUrl(agentUrl) : normalizeUrl(baseUrl);
  }

  // -- PRIVATE --

  /**
   * Normalizes a URL by removing trailing slashes.
   *
   * @param url the URL to normalize
   * @return the normalized URL without trailing slash, or null if input is null/empty
   */
  private String normalizeUrl(final String url) {
    if (!hasText(url)) {
      return null;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
