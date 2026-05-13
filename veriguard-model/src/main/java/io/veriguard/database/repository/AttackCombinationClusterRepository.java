package io.veriguard.database.repository;

import io.veriguard.database.model.combination.AttackCombinationCluster;
import io.veriguard.database.model.combination.AttackCombinationClusterDim;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 攻击组合聚类结果仓储 —— IPv6 安全验证系统 §3.6 ★2 PR D3.
 *
 * <p>聚类是 fresh-write：每次重算前 deleteByRunId 清掉，再批量 saveAll. 不做 upsert.
 */
@Repository
public interface AttackCombinationClusterRepository
    extends CrudRepository<AttackCombinationCluster, String>,
        JpaSpecificationExecutor<AttackCombinationCluster> {

  Page<AttackCombinationCluster> findAllByRunIdAndClusterDim(
      String runId, AttackCombinationClusterDim clusterDim, Pageable pageable);

  List<AttackCombinationCluster> findAllByRunIdAndClusterDim(
      String runId, AttackCombinationClusterDim clusterDim);

  Optional<AttackCombinationCluster> findByRunIdAndClusterDimAndClusterKey(
      String runId, AttackCombinationClusterDim clusterDim, String clusterKey);

  long countByRunId(String runId);

  void deleteByRunId(String runId);
}
