package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Single HTTP header KV pair persisted inside {@link WebAttackPayload#headers} (JSONB list).
 *
 * <p>Mirrors {@code io.veriguard.injectors.web_attack.model.WebRequestHeader} to keep the
 * persistence-layer POJO independent of the api module while preserving wire compatibility.
 */
@Getter
@Setter
public class WebRequestHeaderEntry {

  @JsonProperty("name")
  private String name;

  @JsonProperty("value")
  private String value;
}
