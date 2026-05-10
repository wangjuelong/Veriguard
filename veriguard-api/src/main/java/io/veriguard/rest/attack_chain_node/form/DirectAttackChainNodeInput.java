package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.NodeContract;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DirectAttackChainNodeInput {

  @JsonProperty("node_title")
  private String title;

  @JsonProperty("node_description")
  private String description;

  @JsonProperty("node_injector_contract")
  private String nodeContract;

  @JsonProperty("node_content")
  private ObjectNode content;

  @JsonProperty("node_users")
  private List<String> userIds = new ArrayList<>();

  @JsonProperty("node_documents")
  private List<AttackChainNodeDocumentInput> documents = new ArrayList<>();

  public AttackChainNode toAttackChainNode(@NotNull final NodeContract nodeContract) {
    AttackChainNode attackChainNode = new AttackChainNode();
    attackChainNode.setTitle(getTitle());
    attackChainNode.setDescription(getDescription());
    attackChainNode.setContent(getContent());
    attackChainNode.setNodeContract(nodeContract);
    return attackChainNode;
  }
}
