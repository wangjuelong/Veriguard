package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.veriguard.annotation.Queryable;
import io.veriguard.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

/**
 * Persistence form of a Veriguard "WebAttack" payload (PR B7).
 *
 * <p>Wire-format is aligned with {@code WebAttackContent} (inject runtime form) so importers can
 * round-trip via {@code POST /api/payloads/upsert} without field renames.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(WebAttackPayload.WEB_ATTACK_TYPE)
@EntityListeners(ModelBaseListener.class)
public class WebAttackPayload extends Payload {

  public static final String WEB_ATTACK_TYPE = "WebAttack";

  @JsonProperty("payload_type")
  private String type = WEB_ATTACK_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_web_request_method")
  @JsonProperty("web_request_method")
  @NotNull
  private String method;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_web_request_url")
  @JsonProperty("web_request_url")
  @NotNull
  private String url;

  @Column(name = "payload_web_request_body")
  @JsonProperty("web_request_body")
  private String body;

  @Column(name = "payload_web_request_body_type")
  @JsonProperty("web_request_body_type")
  private String bodyType;

  @Column(name = "payload_web_request_timeout_seconds")
  @JsonProperty("web_request_timeout_seconds")
  private Integer timeoutSeconds = 30;

  @Column(name = "payload_web_request_cookies")
  @JsonProperty("web_request_cookies")
  private String cookies;

  @Type(JsonType.class)
  @Column(name = "payload_web_request_headers", columnDefinition = "jsonb")
  @JsonProperty("web_request_headers")
  private List<WebRequestHeaderEntry> headers = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "payload_web_expected_status_codes", columnDefinition = "jsonb")
  @JsonProperty("expected_status_codes")
  private List<Integer> expectedStatusCodes = new ArrayList<>();

  @Column(name = "payload_web_expected_body_regex")
  @JsonProperty("expected_body_regex")
  private String expectedBodyRegex;

  public WebAttackPayload() {}

  public WebAttackPayload(String id, String type, String name) {
    super(id, type, name);
  }
}
