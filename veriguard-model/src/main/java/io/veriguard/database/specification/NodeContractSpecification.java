package io.veriguard.database.specification;

import io.veriguard.database.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public class NodeContractSpecification {

  private NodeContractSpecification() {}

  public static Specification<NodeContract> fromAttackPattern(String attackPatternId) {
    return (root, query, cb) -> cb.equal(root.get("attackPatterns").get("id"), attackPatternId);
  }

  public static Specification<NodeContract> byPayloadId(final String payloadId) {
    if (payloadId == null || payloadId.isEmpty()) {
      throw new IllegalArgumentException("Payload ID must not be null or empty");
    }
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      return cb.equal(payload.get("id"), payloadId);
    };
  }

  public static Specification<NodeContract> byPayloadExternalId(
      final String payloadExternalId) {
    if (payloadExternalId == null || payloadExternalId.isEmpty()) {
      throw new IllegalArgumentException("Payload external ID must not be null or empty");
    }
    return (root, query, cb) -> {
      Join<Object, Object> payload = root.join("payload", JoinType.LEFT);
      return cb.equal(payload.get("externalId"), payloadExternalId);
    };
  }

  /**
   * Specification to filter NodeContracts based on user grants on associated payloads. NodeExecutor
   * contracts without payload are always accessible. NodeExecutor contracts with payload are only
   * accessible if the user has at least OBSERVER grant on the payload OR the ACCESS capability on
   * payloads. Usage:
   * myResourceSearchSpecification.and(SpecificationUtils.hasAccessToNodeContract(userId,
   * isAdmin, hasCapaForClass)) Only works for NodeContract entities, return cb.conjunction()
   * otherwise.
   *
   * @param currentUser current user performing the search
   * @return Specification for filtering NodeContracts based on user grants
   */
  public static Specification<NodeContract> hasAccessToNodeContract(
      final User currentUser) {
    return (root, query, cb) -> {
      // If user bypasses grants entirely or has the specific capa for payloads, return all
      if (currentUser.isAdminOrBypass()
          || currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS)) {
        return cb.conjunction();
      }

      // Join payload (can be null because of left join)
      Join<NodeContract, Payload> payloadJoin = root.join("payload", JoinType.LEFT);

      // Case 1: no payload
      Predicate noPayload = cb.isNull(payloadJoin.get("id"));

      // Case 2: payload accessible
      Predicate payloadGranted =
          payloadJoin
              .get("id")
              .in(
                  SpecificationUtils.accessibleResourcesSubquery(
                      query,
                      cb,
                      currentUser.getId(),
                      Grant.GRANT_RESOURCE_TYPE.PAYLOAD,
                      Grant.GRANT_TYPE.OBSERVER.andHigher()));

      return cb.or(noPayload, payloadGranted);
    };
  }
}
