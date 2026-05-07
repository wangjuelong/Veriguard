package io.veriguard.database.repository;

import io.veriguard.database.model.NodeExecutor;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.model.Payload;
import io.veriguard.database.raw.RawNodeExecutorsContracts;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link NodeContract} entities.
 *
 * <p>This repository provides data access operations for nodeExecutor contracts, which define the
 * capabilities and parameters of attack simulation nodeExecutors. It supports:
 *
 * <ul>
 *   <li>CRUD operations via {@link CrudRepository}
 *   <li>Dynamic filtering via {@link JpaSpecificationExecutor}
 *   <li>Custom queries for contract lookup by various criteria
 *   <li>Access-controlled queries respecting user grants
 * </ul>
 *
 * @see NodeContract
 * @see NodeExecutor
 */
@Repository
public interface NodeContractRepository
    extends CrudRepository<NodeContract, String>, JpaSpecificationExecutor<NodeContract> {

  /**
   * Retrieves all nodeExecutor contracts with their associated attack pattern external IDs.
   *
   * <p>Returns a lightweight projection containing only the contract ID and aggregated attack
   * pattern IDs for efficient bulk operations.
   *
   * @return list of raw nodeExecutor contract projections
   */
  @Query(
      value =
          "SELECT injcon.injector_contract_id, "
              + "array_remove(array_agg(attpatt.attack_pattern_external_id), NULL) AS injector_contract_attack_patterns_external_id "
              + "FROM injectors_contracts injcon "
              + "LEFT JOIN injectors_contracts_attack_patterns injconatt ON injcon.injector_contract_id = injconatt.injector_contract_id "
              + "LEFT JOIN attack_patterns attpatt ON injconatt.attack_pattern_id = attpatt.attack_pattern_id "
              + "GROUP BY injcon.injector_contract_id",
      nativeQuery = true)
  List<RawNodeExecutorsContracts> getAllRawNodeExecutorsContracts();

  /**
   * Retrieves nodeExecutor contracts accessible to a specific user.
   *
   * <p>Returns contracts that either have no payload (public contracts) or where the user has been
   * granted access to the payload through their group memberships.
   *
   * @param userId the ID of the user to check access for
   * @return list of raw nodeExecutor contract projections the user can access
   */
  @Query(
      value =
          "SELECT injcon.injector_contract_id, "
              + "array_remove(array_agg(attpatt.attack_pattern_external_id), NULL) AS injector_contract_attack_patterns_external_id "
              + "FROM injectors_contracts injcon "
              + "LEFT JOIN injectors_contracts_attack_patterns injconatt ON injcon.injector_contract_id = injconatt.injector_contract_id "
              + "LEFT JOIN attack_patterns attpatt ON injconatt.attack_pattern_id = attpatt.attack_pattern_id "
              + "WHERE injcon.injector_contract_payload IS NULL "
              + "OR EXISTS ( "
              + "  SELECT 1 FROM users u "
              + "  INNER JOIN users_groups ug ON u.user_id = ug.user_id "
              + "  INNER JOIN groups g ON ug.group_id = g.group_id "
              + "  INNER JOIN grants gr ON g.group_id = gr.grant_group "
              + "  WHERE u.user_id = :userId "
              + "  AND gr.grant_resource = injcon.injector_contract_payload "
              + ") "
              + "GROUP BY injcon.injector_contract_id",
      nativeQuery = true)
  List<RawNodeExecutorsContracts> getAllRawNodeExecutorsContractsWithoutPayloadOrGranted(
      @Param("userId") String userId);

  @NotNull
  Optional<NodeContract> findById(@NotNull String id);

  @NotNull
  Optional<NodeContract> findByIdOrExternalId(String id, String externalId);

  @NotNull
  List<NodeContract> findNodeContractsByNodeExecutor(@NotNull NodeExecutor nodeExecutor);

  @NotNull
  Optional<NodeContract> findNodeContractByNodeExecutorAndPayload(
      @NotNull NodeExecutor nodeExecutor, @NotNull Payload payload);

  @NotNull
  List<NodeContract> findNodeContractsByPayload(@NotNull Payload payload);

  @Query(
      value =
          """
        SELECT *
        FROM (
            SELECT ic.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY vulnerability.vulnerability_external_id
                       ORDER BY ic.injector_contract_updated_at DESC
                   ) AS rn
            FROM injectors_contracts ic
            JOIN injectors_contracts_vulnerabilities icv
              ON ic.injector_contract_id = icv.injector_contract_id
            JOIN vulnerabilities vulnerability
              ON icv.vulnerability_id = vulnerability.vulnerability_id
            WHERE LOWER(vulnerability.vulnerability_external_id) IN (:externalIds)
        ) ranked
        WHERE ranked.rn <= :contractsPerVulnerability
        """,
      nativeQuery = true)
  Set<NodeContract> findNodeContractsByVulnerabilityIdIn(
      @Param("externalIds") Set<String> externalIds,
      @Param("contractsPerVulnerability") Integer contractsPerVulnerability);
}
