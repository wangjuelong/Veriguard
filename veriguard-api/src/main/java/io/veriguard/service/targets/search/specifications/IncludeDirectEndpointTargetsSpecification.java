package io.veriguard.service.targets.search.specifications;

import static io.veriguard.service.targets.search.specifications.SearchSpecificationUtils.createJoinedFrom;

import io.veriguard.database.model.AttackChainNode;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IncludeDirectEndpointTargetsSpecification<T> {

  public Specification<T> buildSpecification(AttackChainNode scopedAttackChainNode, List<String> joinPath) {
    return getDirectTargetingSpecification(scopedAttackChainNode, joinPath);
  }

  private Specification<T> getDirectTargetingSpecification(
      AttackChainNode scopedAttackChainNode, List<String> joinPath) {
    return (root, query, criteriaBuilder) -> {
      Subquery<Integer> subQuery = query.subquery(Integer.class);
      Root<AttackChainNode> attackChainNodeTable = subQuery.from(AttackChainNode.class);
      From<?, ?> finalFrom = createJoinedFrom(attackChainNodeTable, joinPath);

      subQuery
          .select(criteriaBuilder.literal(1))
          .where(
              criteriaBuilder.equal(attackChainNodeTable.get("id"), scopedAttackChainNode.getId()),
              criteriaBuilder.equal(finalFrom.get("id"), root.get("id")));
      return criteriaBuilder.exists(subQuery);
    };
  }
}
