package io.veriguard.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeDocumentInput;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AtomicTestingInput {

  @NotBlank
  @JsonProperty("node_title")
  private String title;

  @JsonProperty("node_description")
  private String description;

  @JsonProperty("node_injector_contract")
  private String nodeContract;

  @JsonProperty("node_content")
  private ObjectNode content;

  @JsonProperty("node_teams")
  private List<String> teams = new ArrayList<>();

  @JsonProperty("node_assets")
  private List<String> assets = new ArrayList<>();

  @JsonProperty("node_asset_groups")
  private List<String> assetGroups = new ArrayList<>();

  @JsonProperty("node_documents")
  private List<AttackChainNodeDocumentInput> documents = new ArrayList<>();

  @JsonProperty("node_all_teams")
  private boolean allTeams;

  @JsonProperty("node_tags")
  private List<String> tagIds = new ArrayList<>();
}
