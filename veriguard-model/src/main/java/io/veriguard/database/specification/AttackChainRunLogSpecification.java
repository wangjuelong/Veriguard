package io.veriguard.database.specification;

import io.veriguard.database.model.Log;
import org.springframework.data.jpa.domain.Specification;

public class AttackChainRunLogSpecification {

  public static Specification<Log> fromAttackChainRun(String attackChainRunId) {
    return (root, query, cb) -> cb.equal(root.get("attackChainRun").get("id"), attackChainRunId);
  }
}
