package io.veriguard.database.specification;

import io.veriguard.database.model.Comcheck;
import org.springframework.data.jpa.domain.Specification;

public class ComcheckSpecification {

  public static Specification<Comcheck> id(String dryRunId) {
    return (root, query, cb) -> cb.equal(root.get("id"), dryRunId);
  }

  public static Specification<Comcheck> fromAttackChainRun(String attackChainRunId) {
    return (root, query, cb) -> cb.equal(root.get("attackChainRun").get("id"), attackChainRunId);
  }
}
