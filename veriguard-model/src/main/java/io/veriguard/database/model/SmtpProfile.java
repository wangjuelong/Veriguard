package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "smtp_profiles")
@EntityListeners(ModelBaseListener.class)
public class SmtpProfile implements Base {

  public enum AUTH_TYPE {
    @JsonProperty("none")
    NONE,
    @JsonProperty("password")
    PASSWORD,
  }

  public enum TLS_MODE {
    @JsonProperty("none")
    NONE,
    @JsonProperty("starttls")
    STARTTLS,
    @JsonProperty("tls")
    TLS,
  }

  @Id
  @Column(name = "smtp_profile_id")
  @JsonProperty("smtp_profile_id")
  @NotBlank
  private String id = UUID.randomUUID().toString();

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "smtp_profile_name")
  @JsonProperty("smtp_profile_name")
  @NotBlank
  private String name;

  @Column(name = "smtp_profile_host")
  @JsonProperty("smtp_profile_host")
  @NotBlank
  private String host;

  @Column(name = "smtp_profile_port")
  @JsonProperty("smtp_profile_port")
  @NotNull
  @Min(1)
  private Integer port;

  @Column(name = "smtp_profile_auth_type")
  @JsonProperty("smtp_profile_auth_type")
  @Enumerated(EnumType.STRING)
  @NotNull
  private AUTH_TYPE authType = AUTH_TYPE.NONE;

  @Column(name = "smtp_profile_username")
  @JsonProperty("smtp_profile_username")
  private String username;

  @Column(name = "smtp_profile_password")
  @JsonProperty("smtp_profile_password")
  private String password;

  @Column(name = "smtp_profile_tls_mode")
  @JsonProperty("smtp_profile_tls_mode")
  @Enumerated(EnumType.STRING)
  @NotNull
  private TLS_MODE tlsMode = TLS_MODE.STARTTLS;

  @Column(name = "smtp_profile_default_from")
  @JsonProperty("smtp_profile_default_from")
  @NotBlank
  private String defaultFrom;

  @Column(name = "smtp_profile_default_reply_to")
  @JsonProperty("smtp_profile_default_reply_to")
  private String defaultReplyTo;

  @CreationTimestamp
  @Column(name = "smtp_profile_created_at")
  @JsonProperty("smtp_profile_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "smtp_profile_updated_at")
  @JsonProperty("smtp_profile_updated_at")
  private Instant updatedAt;
}
