package io.veriguard.injectors.web_attack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** Single HTTP header KV pair (used inside WebAttackContent.headers list). */
@Getter
@Setter
public class WebRequestHeader {

  @JsonProperty("name")
  private String name;

  @JsonProperty("value")
  private String value;
}
