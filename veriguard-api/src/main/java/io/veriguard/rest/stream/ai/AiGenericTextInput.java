package io.veriguard.rest.stream.ai;

import static io.veriguard.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiGenericTextInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("ai_content")
  private String content;

  @JsonProperty("ai_format")
  private String format;

  @JsonProperty("ai_tone")
  private String tone;
}
