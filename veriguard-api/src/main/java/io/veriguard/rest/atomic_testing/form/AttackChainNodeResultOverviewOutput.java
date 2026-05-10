package io.veriguard.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class AttackChainNodeResultOverviewOutput {

  @Schema(description = "Id of inject")
  @JsonProperty("node_id")
  @NotBlank
  private String id;

  @Schema(description = "Title of inject")
  @JsonProperty("node_title")
  @NotBlank
  private String title;

  @Schema(description = "Description of inject")
  @JsonProperty("node_description")
  private String description;

  @Schema(description = "Content of inject")
  @JsonProperty("node_content")
  private ObjectNode content;

  @Schema(description = "Type of inject")
  @JsonProperty("node_type")
  private String type;

  @Schema(description = "Tags")
  @JsonProperty("injects_tags")
  private List<String> tagIds;

  @Schema(description = "Documents")
  @JsonProperty("injects_documents")
  private List<String> documentIds;

  @Schema(description = "Full contract")
  @JsonProperty("node_injector_contract")
  private AtomicNodeContractOutput nodeContract;

  @Schema(description = "status")
  @JsonProperty("node_status")
  private AttackChainNodeStatusSimple status;

  @Schema(description = "Expectations")
  @JsonProperty("node_expectations")
  private List<AttackChainNodeExpectationSimple> expectations;

  @Schema(description = "Kill chain phases")
  @JsonProperty("node_kill_chain_phases")
  private List<KillChainPhaseSimple> killChainPhases;

  @Schema(description = "Tags")
  @JsonProperty("node_tags")
  private Set<String> tags;

  @Schema(description = "Indicates whether the inject is ready for use")
  @JsonProperty("node_ready")
  private boolean isReady;

  @Schema(description = "Timestamp when the inject was last updated")
  @JsonProperty("node_updated_at")
  private Instant updatedAt;

  // -- COMPUTED ATTRIBUTES --

  @Builder.Default
  @Schema(description = "Result of expectations")
  @JsonProperty("node_expectation_results")
  @NotNull
  private List<ExpectationResultsByType> expectationResultByTypes = new ArrayList<>();
}
