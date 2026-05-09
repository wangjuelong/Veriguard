package io.veriguard.rest.attack_chain_run.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.rest.atomic_testing.form.TargetSimple;
import io.veriguard.utils.NodeExpectationResultUtils.ExpectationResultsByType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonInclude(NON_NULL)
public class AttackChainRunSimple {

  @Schema(description = "Exercise Id")
  @JsonProperty("attack_chain_run_id")
  @NotBlank
  private String id;

  @Schema(description = "Exercise Name")
  @JsonProperty("attack_chain_run_name")
  @NotBlank
  private String name;

  @Schema(description = "Exercise status")
  @JsonProperty("attack_chain_run_status")
  @Enumerated(EnumType.STRING)
  private AttackChainRunStatus status;

  @Schema(description = "Exercise Subtitle")
  @JsonProperty("attack_chain_run_subtitle")
  private String subtitle;

  @Schema(description = "Exercise Category")
  @JsonProperty("attack_chain_run_category")
  private String category;

  @Schema(description = "Exercise Start Date")
  @JsonProperty("attack_chain_run_start_date")
  private Instant start;

  @Schema(description = "Exercise Update Date")
  @JsonProperty("attack_chain_run_updated_at")
  private Instant updatedAt = now();

  @Schema(description = "Tags")
  @JsonProperty("attack_chain_run_tags")
  private Set<String> tagIds = new HashSet<>();

  @JsonIgnore private String[] attackChainNodeIds;

  // COMPUTED ATTRIBUTES

  @JsonProperty("attack_chain_run_global_score")
  @NotNull
  private List<ExpectationResultsByType> expectationResultByTypes = new ArrayList<>();

  @JsonProperty("attack_chain_run_targets")
  private List<TargetSimple> targets = new ArrayList<>();
}
