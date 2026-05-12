package io.veriguard.injectors.web_attack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.model.attack_chain_node.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * POJO mirroring {@code inject_content} JSONB for the {@code veriguard_web_attack} inject type.
 *
 * <p>Field names mirror spec §5.1 wire format (snake_case).
 */
@Getter
@Setter
public class WebAttackContent {

  @JsonProperty("web_request_method")
  private String method;

  @JsonProperty("web_request_url")
  private String url;

  @JsonProperty("web_request_headers")
  private List<WebRequestHeader> headers = new ArrayList<>();

  @JsonProperty("web_request_body")
  private String body;

  @JsonProperty("web_request_body_type")
  private String bodyType;

  @JsonProperty("web_request_cookies")
  private String cookies;

  @JsonProperty("web_request_follow_redirects")
  private boolean followRedirects = false;

  @JsonProperty("web_request_verify_tls")
  private boolean verifyTls = false;

  @JsonProperty("web_request_timeout_seconds")
  private int timeoutSeconds = 30;

  @JsonProperty("expected_status_codes")
  private List<Integer> expectedStatusCodes = new ArrayList<>();

  @JsonProperty("expected_body_regex")
  private String expectedBodyRegex;

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();
}
