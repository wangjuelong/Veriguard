package io.veriguard.rest.attack_chain_node.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.veriguard.database.model.Document;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeDocument;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AttackChainNodeDocumentInput {

  @JsonProperty("document_id")
  private String documentId;

  @JsonProperty("document_attached")
  private boolean attached = true;

  public AttackChainNodeDocument toDocument(@NotNull final Document document, @NotNull final AttackChainNode attackChainNode) {
    AttackChainNodeDocument attackChainNodeDocument = new AttackChainNodeDocument();
    attackChainNodeDocument.setAttackChainNode(attackChainNode);
    attackChainNodeDocument.setDocument(document);
    attackChainNodeDocument.setAttached(this.isAttached());
    return attackChainNodeDocument;
  }
}
