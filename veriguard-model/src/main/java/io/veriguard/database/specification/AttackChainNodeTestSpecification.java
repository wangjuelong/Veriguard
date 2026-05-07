package io.veriguard.database.specification;

import io.veriguard.database.model.AttackChainNodeTestStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

public class AttackChainNodeTestSpecification {

  public static Specification<AttackChainNodeTestStatus> findAttackChainNodeTestInAttackChainRun(String attackChainRunId) {

    return (root, query, criteriaBuilder) -> {
      Path<Object> path =
          root.join("inject", JoinType.INNER).join("exercise", JoinType.INNER).get("id");
      return criteriaBuilder.equal(path, attackChainRunId);
    };
  }

  public static Specification<AttackChainNodeTestStatus> findAttackChainNodeTestInAttackChain(String attackChainId) {

    return (root, query, criteriaBuilder) -> {
      Path<Object> path =
          root.join("inject", JoinType.INNER).join("scenario", JoinType.INNER).get("id");
      return criteriaBuilder.equal(path, attackChainId);
    };
  }
}
