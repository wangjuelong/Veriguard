package io.veriguard.database.raw;

import java.util.List;

/**
 * Spring Data projection interface for nodeExecutor contract data with attack patterns.
 *
 * <p>This interface defines a lightweight projection for retrieving nodeExecutor contracts along
 * with their associated attack pattern external IDs. It is used for efficient bulk retrieval of
 * contract metadata without loading full entity graphs.
 *
 * <p>The projection is populated from native SQL queries that join nodeExecutor contracts with
 * attack patterns, aggregating the external IDs into an array.
 *
 * @see io.veriguard.database.model.NodeContract
 * @see io.veriguard.database.repository.NodeContractRepository
 */
public interface RawNodeExecutorsContracts {

  /**
   * Returns the unique identifier of the nodeExecutor contract.
   *
   * @return the nodeExecutor contract ID
   */
  String getInjector_contract_id();

  /**
   * Returns the list of external IDs for attack patterns associated with this contract.
   *
   * <p>External IDs typically follow the MITRE ATT&CK format (e.g., "T1059.001").
   *
   * @return list of attack pattern external IDs, or an empty list if none are associated
   */
  List<String> getInjector_contract_attack_patterns_external_id();
}
