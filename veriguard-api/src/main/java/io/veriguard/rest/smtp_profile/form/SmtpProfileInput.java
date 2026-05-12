package io.veriguard.rest.smtp_profile.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.SmtpProfile;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SmtpProfileInput(
    @JsonProperty("smtp_profile_name") @NotBlank String name,
    @JsonProperty("smtp_profile_host") @NotBlank String host,
    @JsonProperty("smtp_profile_port") @NotNull @Min(1) Integer port,
    @JsonProperty("smtp_profile_auth_type") @NotNull SmtpProfile.AUTH_TYPE authType,
    @JsonProperty("smtp_profile_username") String username,
    @JsonProperty("smtp_profile_password") String password,
    @JsonProperty("smtp_profile_tls_mode") @NotNull SmtpProfile.TLS_MODE tlsMode,
    @JsonProperty("smtp_profile_default_from") @NotBlank String defaultFrom,
    @JsonProperty("smtp_profile_default_reply_to") String defaultReplyTo) {

  public SmtpProfile toEntity(SmtpProfile target) {
    target.setName(this.name);
    target.setHost(this.host);
    target.setPort(this.port);
    target.setAuthType(this.authType);
    target.setUsername(this.username);
    target.setPassword(this.password);
    target.setTlsMode(this.tlsMode);
    target.setDefaultFrom(this.defaultFrom);
    target.setDefaultReplyTo(this.defaultReplyTo);
    return target;
  }
}
