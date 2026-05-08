package io.veriguard.database.repository;

import io.veriguard.database.model.AttackChainNodeDocument;
import io.veriguard.database.model.AttackChainNodeDocumentId;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackChainNodeDocumentRepository
    extends CrudRepository<AttackChainNodeDocument, AttackChainNodeDocumentId>,
        JpaSpecificationExecutor<AttackChainNodeDocument> {

  @NotNull
  Optional<AttackChainNodeDocument> findById(@NotNull AttackChainNodeDocumentId id);

  @Modifying
  @Query(
      value =
          "insert into attack_chain_nodes_documents (node_id, document_id, document_attached) "
              + "values (:attackChainNodeId, :documentId, :documentAttached)",
      nativeQuery = true)
  void addAttackChainNodeDoc(
      @Param("attackChainNodeId") String attackChainNodeId,
      @Param("documentId") String docId,
      @Param("documentAttached") boolean docAttached);

  @Modifying
  @Query(
      value = "UPDATE attack_chain_nodes_documents SET node_id = :attackChainNodeId where node_id = :oldAttackChainNodeId",
      nativeQuery = true)
  void updateAttackChainNodeId(
      @Param("attackChainNodeId") String attackChainNodeId,
      @Param("oldAttackChainNodeId") String oldAttackChainNodeId);
}
