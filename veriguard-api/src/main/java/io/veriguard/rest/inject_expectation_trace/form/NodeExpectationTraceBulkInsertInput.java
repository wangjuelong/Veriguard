package io.veriguard.rest.inject_expectation_trace.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.config.AppConfig;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class NodeExpectationTraceBulkInsertInput {

  @JsonProperty("expectation_traces")
  @NotNull(message = AppConfig.MANDATORY_MESSAGE)
  private List<NodeExpectationTraceInput> expectationTraces;
}
