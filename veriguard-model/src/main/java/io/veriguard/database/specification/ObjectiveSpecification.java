package io.veriguard.database.specification;

import io.veriguard.database.model.Objective;
import org.springframework.data.jpa.domain.Specification;

public class ObjectiveSpecification {

  public static Specification<Objective> fromAttackChainRun(String attackChainRunId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), attackChainRunId);
  }

  public static Specification<Objective> fromAttackChain(String attackChainId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), attackChainId);
  }
}
