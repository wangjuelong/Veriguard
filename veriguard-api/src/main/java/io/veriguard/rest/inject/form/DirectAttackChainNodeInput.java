package io.veriguard.rest.inject.form;

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

  @JsonProperty("inject_title")
  private String title;

  @JsonProperty("inject_description")
  private String description;

  @JsonProperty("inject_injector_contract")
  private String nodeContract;

  @JsonProperty("inject_content")
  private ObjectNode content;

  @JsonProperty("inject_users")
  private List<String> userIds = new ArrayList<>();

  @JsonProperty("inject_documents")
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
