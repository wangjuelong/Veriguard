package io.veriguard.database.specification;

import io.veriguard.database.model.Variable;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class VariableSpecification {

  public static Specification<Variable> fromAttackChainRun(@NotNull final String attackChainRunId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), attackChainRunId);
  }

  public static Specification<Variable> fromAttackChain(@NotNull final String attackChainId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), attackChainId);
  }
}
