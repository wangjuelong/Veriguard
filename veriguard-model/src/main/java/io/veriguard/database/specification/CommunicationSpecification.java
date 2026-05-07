package io.veriguard.database.specification;

import io.veriguard.database.model.Communication;
import org.springframework.data.jpa.domain.Specification;

public class CommunicationSpecification {

  public static Specification<Communication> fromAttackChainNode(String attackChainNodeId) {
    return (root, query, cb) -> cb.equal(root.get("inject").get("id"), attackChainNodeId);
  }
}
