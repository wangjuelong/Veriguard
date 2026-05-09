package io.veriguard.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainNodeResultOutput {

  @Schema(description = "Id of inject")
  @JsonProperty("node_id")
  @NotBlank
  private String id;

  @Schema(description = "Title of inject")
  @JsonProperty("node_title")
  @NotBlank
  private String title;

  @Schema(description = "Timestamp when the inject was last updated")
  @JsonProperty("node_updated_at")
  @NotNull
  private Instant updatedAt;

  @Schema(description = "Type of inject")
  @JsonProperty("node_type")
  private String attackChainNodeType;

  @Schema(description = "Injector contract")
  @JsonProperty("node_injector_contract")
  private NodeContractSimple nodeContract;

  @Schema(description = "Status")
  @JsonProperty("node_status")
  private AttackChainNodeStatusSimple status;

  @JsonIgnore private ObjectNode content;
  @JsonIgnore private String[] teamIds;
  @JsonIgnore private String[] assetIds;
  @JsonIgnore private String[] assetGroupIds;

  // -- COMPUTED ATTRIBUTES --

  @Schema(description = "Result of expectations")
  @JsonProperty("node_expectation_results")
  @NotNull
  private List<ExpectationResultsByType> expectationResultByTypes = new ArrayList<>();

  @JsonProperty("node_targets")
  private List<TargetSimple> targets = new ArrayList<>();

  @JsonProperty("node_contract_domains")
  @Schema(description = "Domain of the inject")
  public String[] getDomains() {
    return nodeContract != null ? nodeContract.getDomains() : new String[] {};
  }
}
