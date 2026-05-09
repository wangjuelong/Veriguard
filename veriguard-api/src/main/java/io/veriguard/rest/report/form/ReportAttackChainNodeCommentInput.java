package io.veriguard.rest.report.form;

import static io.veriguard.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportAttackChainNodeCommentInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("node_id")
  private String attackChainNodeId;

  @JsonProperty("report_inject_comment")
  private String comment;
}
