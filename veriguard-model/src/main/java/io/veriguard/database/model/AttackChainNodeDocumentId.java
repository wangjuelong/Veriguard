package io.veriguard.database.model;

import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AttackChainNodeDocumentId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String attackChainNodeId;
  private String documentId;

  public AttackChainNodeDocumentId() {
    // Default constructor
  }

  public String getAttackChainNodeId() {
    return attackChainNodeId;
  }

  public void setAttackChainNodeId(String attackChainNodeId) {
    this.attackChainNodeId = attackChainNodeId;
  }

  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttackChainNodeDocumentId that = (AttackChainNodeDocumentId) o;
    return attackChainNodeId.equals(that.attackChainNodeId) && documentId.equals(that.documentId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attackChainNodeId, documentId);
  }
}
