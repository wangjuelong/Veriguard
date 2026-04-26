package io.veriguard.api.xtm_composer.dto;

import static io.veriguard.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.ConnectorInstance;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class XtmComposerUpdateStatusInput {
  @NotNull(message = MANDATORY_MESSAGE)
  @Schema(description = "The connector instance current status")
  @JsonProperty("connector_instance_current_status")
  private ConnectorInstance.CURRENT_STATUS_TYPE currentStatus;
}
