package io.veriguard.rest.atomic_testing.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.NodeExpectationResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(NON_NULL)
@Builder
@Schema(description = "Represents a single inject expectation with agent name")
public class AttackChainNodeExpectationAgentOutput {

  @NotNull
  @JsonProperty("node_expectation_type")
  private AttackChainNodeExpectation.EXPECTATION_TYPE type;

  @NotBlank
  @JsonProperty("node_expectation_id")
  private String id;

  @JsonProperty("node_expectation_name")
  private String name;

  @JsonProperty("node_expectation_results")
  private List<NodeExpectationResult> results;

  @JsonProperty("node_expectation_score")
  private Double score;

  @JsonProperty("node_expectation_status")
  public AttackChainNodeExpectation.EXPECTATION_STATUS status;

  @JsonProperty("node_expiration_time")
  @NotNull
  private Long expirationTime;

  @JsonProperty("node_expectation_created_at")
  private Instant createdAt;

  @JsonProperty("node_expectation_group")
  private boolean expectationGroup;

  @JsonProperty("node_expectation_asset")
  private String assetId; // id

  @JsonProperty("node_expectation_agent")
  private String agentId; // id

  @JsonProperty("node_expectation_agent_name")
  private String agentName;
}
