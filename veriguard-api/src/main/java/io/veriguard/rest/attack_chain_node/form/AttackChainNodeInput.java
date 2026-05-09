package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.NodeContract;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainNodeInput {

  @NotBlank
  @JsonProperty("node_title")
  private String title;

  @JsonProperty("node_description")
  private String description;

  @JsonProperty("node_injector_contract")
  private String nodeContract;

  @JsonProperty("node_content")
  private ObjectNode content;

  @JsonProperty("node_depends_on")
  private List<AttackChainEdgeInput> dependsOn = new ArrayList<>();

  @JsonProperty("node_depends_duration")
  private Long dependsDuration;

  @JsonProperty("node_teams")
  private List<String> teams = new ArrayList<>();

  @JsonProperty("node_assets")
  private List<String> assets = new ArrayList<>();

  @JsonProperty("node_asset_groups")
  private List<String> assetGroups = new ArrayList<>();

  @JsonProperty("node_documents")
  private List<AttackChainNodeDocumentInput> documents = new ArrayList<>();

  @JsonProperty("node_all_teams")
  private boolean allTeams = false;

  @JsonProperty("node_country")
  private String country;

  @JsonProperty("node_city")
  private String city;

  @JsonProperty("node_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("node_enabled")
  private boolean enabled = true;

  public AttackChainNode toAttackChainNode(@NotNull final NodeContract nodeContract) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setTitle(getTitle());
    attackChainNode.setDescription(getDescription());
    attackChainNode.setContent(getContent());
    attackChainNode.setNodeContract(nodeContract);
    attackChainNode.setDependsDuration(getDependsDuration());
    attackChainNode.setAllTeams(isAllTeams());
    attackChainNode.setCountry(getCountry());
    attackChainNode.setCity(getCity());
    attackChainNode.setEnabled(isEnabled());
    return attackChainNode;
  }
}
