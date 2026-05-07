package io.veriguard.database.specification;

import io.veriguard.database.model.AttackChainRunStatus;
import io.veriguard.database.model.ContractOutputType;
import io.veriguard.database.model.Finding;
import jakarta.persistence.criteria.*;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class FindingSpecification {

  private FindingSpecification() {}

  public static Specification<Finding> findFindingsForAttackChainNode(
      @NotNull final String attackChainNodeId) {
    return (root, query, cb) -> cb.equal(root.get("inject").get("id"), attackChainNodeId);
  }

  public static Specification<Finding> findFindingsForSimulation(
      @NotNull final String simulationId) {
    return (root, query, cb) ->
        cb.equal(root.get("inject").get("exercise").get("id"), simulationId);
  }

  public static Specification<Finding> findFindingsForAttackChain(
      @NotNull final String attackChainId) {
    return (root, query, cb) ->
        cb.equal(root.get("inject").get("exercise").get("scenario").get("id"), attackChainId);
  }

  public static Specification<Finding> findFindingsForEndpoint(@NotNull final String endpointId) {
    return (root, query, cb) -> cb.equal(root.get("assets").get("id"), endpointId);
  }

  public static Specification<Finding> forLatestSimulations() {
    return (root, query, cb) -> {
      Join<?, ?> attackChainRunJoin1 =
          root.join("inject", JoinType.INNER).join("exercise", JoinType.LEFT);
      Join<?, ?> attackChainRunJoin2 =
          attackChainRunJoin1.join("scenario", JoinType.LEFT).join("exercises", JoinType.LEFT);

      attackChainRunJoin2.on(
          cb.and(
              cb.equal(
                  attackChainRunJoin1.get("scenario").get("id"),
                  attackChainRunJoin2.get("scenario").get("id")),
              // check this column is not null for joining
              cb.isNotNull(attackChainRunJoin1.get("launchOrder")),
              cb.isNotNull(attackChainRunJoin2.get("launchOrder")),
              // only consider finished simulations
              cb.equal(attackChainRunJoin1.get("status"), AttackChainRunStatus.FINISHED),
              cb.equal(attackChainRunJoin2.get("status"), AttackChainRunStatus.FINISHED),
              // trim to "latest" simulation
              cb.lessThan(
                  attackChainRunJoin1.get("launchOrder"), attackChainRunJoin2.get("launchOrder"))));

      return cb.and(
          cb.isNull(attackChainRunJoin2.get("id")),
          cb.or(
              cb.equal(attackChainRunJoin1.get("status"), AttackChainRunStatus.FINISHED),
              cb.isNull(attackChainRunJoin1.get("id"))));
    };
  }

  public static Specification<Finding> distinctTypeValueWithFilter(
      Specification<Finding> baseSpec) {
    return (root, query, cb) -> {
      query.distinct(true);

      Subquery<String> subquery = query.subquery(String.class);
      Root<Finding> subRoot = subquery.from(Finding.class);

      Predicate specPredicate = null;
      if (baseSpec != null) {
        specPredicate = baseSpec.toPredicate(subRoot, query, cb);
      }

      subquery.select(cb.least(subRoot.<String>get("id")));
      if (specPredicate != null) {
        subquery.where(specPredicate);
      }
      subquery.groupBy(subRoot.get("type"), subRoot.get("value"));

      return root.get("id").in(subquery);
    };
  }

  public static Specification<Finding> withAssets() {
    return (root, query, cb) -> {
      root.fetch("assets", JoinType.LEFT);
      query.distinct(true);
      return null;
    };
  }

  public static Specification<Finding> findAllWithAssetsByTypeValueIn(
      List<ContractOutputType> types, List<String> values, Specification<Finding> specification) {
    return Specification.where(specification)
        .and(withAssets())
        .and(
            (root, query, cb) -> {
              Predicate typeIn = root.get("type").in(types);
              Predicate valueIn = root.get("value").in(values);
              return cb.and(typeIn, valueIn);
            });
  }
}
