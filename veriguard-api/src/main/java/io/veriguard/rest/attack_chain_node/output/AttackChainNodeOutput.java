package io.veriguard.rest.attack_chain_node.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.database.model.AttackChainEdge;
import io.veriguard.database.model.Domain;
import io.veriguard.database.model.NodeContract;
import io.veriguard.healthcheck.dto.HealthCheck;
import io.veriguard.helper.NodeModelHelper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttackChainNodeOutput {

  @JsonProperty("inject_id")
  @NotBlank
  @Schema(description = "ID of the inject")
  private String id;

  @JsonProperty("inject_title")
  @NotBlank
  @Schema(description = "Title of the inject")
  private String title;

  @JsonProperty("inject_enabled")
  @Schema(description = "Enabled state of the inject")
  private boolean enabled;

  @JsonProperty("inject_exercise")
  @Schema(description = "Simulation ID of the inject")
  private String attackChainRun;

  @JsonProperty("inject_scenario")
  @Schema(description = "Scenario ID of the inject")
  private String attackChain;

  @JsonProperty("inject_depends_duration")
  @NotNull
  @Min(value = 0L, message = "The value must be positive")
  @Schema(description = "Depend duration of the inject")
  private Long dependsDuration;

  @JsonProperty("inject_depends_on")
  @ArraySchema(schema = @Schema(description = "Inject dependencies of the inject"))
  private List<AttackChainEdge> dependsOn;

  @JsonProperty("inject_injector_contract")
  @Schema(description = "Injector contract of the inject")
  private NodeContract nodeContract;

  @JsonProperty("inject_tags")
  @ArraySchema(schema = @Schema(description = "Tags of the inject"))
  private Set<String> tags;

  @JsonProperty("inject_ready")
  @Schema(description = "Ready state of the inject")
  public boolean isReady;

  @JsonProperty("inject_type")
  @Schema(description = "Type of the inject")
  public String attackChainNodeType;

  @JsonProperty("inject_teams")
  @ArraySchema(schema = @Schema(description = "Teams of the inject"))
  private List<String> teams;

  @JsonProperty("inject_assets")
  @ArraySchema(schema = @Schema(description = "Assets of the inject"))
  private List<String> assets;

  @JsonProperty("inject_asset_groups")
  @ArraySchema(schema = @Schema(description = "Asset groups of the inject"))
  private List<String> assetGroups;

  @JsonProperty("inject_content")
  @Schema(description = "Content of the inject")
  private ObjectNode content;

  @JsonProperty("inject_healthchecks")
  @ArraySchema(schema = @Schema(description = "Healthchecks of the inject"))
  private List<HealthCheck> healthchecks = new ArrayList<>();

  @JsonProperty("inject_testable")
  @Schema(description = "Testable state of the inject")
  public boolean canBeTested() {
    // 二开移除 Email nodeExecutor — 其他注入器目前都不支持单独 test
    return false;
  }

  @JsonProperty("inject_contract_domains")
  @Schema(description = "Domain of the inject")
  public Set<Domain> getDomains() {
    return nodeContract != null ? nodeContract.getDomains() : new HashSet<>();
  }

  public AttackChainNodeOutput(
      String id,
      String title,
      boolean enabled,
      ObjectNode content,
      boolean allTeams,
      String attackChainRunId,
      String attackChainId,
      Long dependsDuration,
      NodeContract nodeContract,
      String[] tags,
      String[] teams,
      String[] assets,
      String[] assetGroups,
      String attackChainNodeType,
      AttackChainEdge attackChainEdge) {
    this.id = id;
    this.title = title;
    this.enabled = enabled;
    this.attackChainRun = attackChainRunId;
    this.attackChain = attackChainId;
    this.dependsDuration = dependsDuration;
    this.nodeContract = nodeContract;
    this.tags = tags != null ? new HashSet<>(Arrays.asList(tags)) : new HashSet<>();

    this.teams = teams != null ? new ArrayList<>(Arrays.asList(teams)) : new ArrayList<>();
    this.assets = assets != null ? new ArrayList<>(Arrays.asList(assets)) : new ArrayList<>();
    this.assetGroups =
        assetGroups != null ? new ArrayList<>(Arrays.asList(assetGroups)) : new ArrayList<>();

    this.isReady =
        NodeModelHelper.isReady(
            nodeContract, content, allTeams, this.teams, this.assets, this.assetGroups);
    this.attackChainNodeType = attackChainNodeType;
    this.teams = teams != null ? new ArrayList<>(Arrays.asList(teams)) : new ArrayList<>();
    this.content = content;

    if (attackChainEdge != null) {
      this.dependsOn = List.of(attackChainEdge);
    }
  }
}
