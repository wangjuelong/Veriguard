package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.veriguard.helper.MonoIdSerializer;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "attack_chain_nodes_documents")
public class AttackChainNodeDocument {

  @EmbeddedId @JsonIgnore
  private AttackChainNodeDocumentId compositeId = new AttackChainNodeDocumentId();

  @ManyToOne(fetch = FetchType.EAGER)
  @MapsId("attackChainNodeId")
  @JoinColumn(name = "node_id")
  @JsonProperty("node_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private AttackChainNode attackChainNode;

  @ManyToOne(fetch = FetchType.EAGER)
  @MapsId("documentId")
  @JoinColumn(name = "document_id")
  @JsonProperty("document_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Document document;

  @Column(name = "document_attached")
  @JsonProperty("document_attached")
  private boolean attached = true;

  @JsonProperty("document_name")
  public String getDocumentName() {
    return this.document.getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttackChainNodeDocument that = (AttackChainNodeDocument) o;
    return compositeId.equals(that.compositeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compositeId);
  }
}
