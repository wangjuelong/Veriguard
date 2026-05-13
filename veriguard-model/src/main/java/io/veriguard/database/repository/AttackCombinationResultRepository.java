package io.veriguard.database.repository;

import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.database.model.combination.AttackCombinationResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 攻击组合结果仓储 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 */
@Repository
public interface AttackCombinationResultRepository
    extends CrudRepository<AttackCombinationResult, String>,
        JpaSpecificationExecutor<AttackCombinationResult> {

  Page<AttackCombinationResult> findAllByRunId(String runId, Pageable pageable);

  Page<AttackCombinationResult> findAllByRunIdAndHitState(
      String runId, AttackCombinationHitState hitState, Pageable pageable);

  long countByRunId(String runId);

  long countByRunIdAndHitState(String runId, AttackCombinationHitState hitState);

  /**
   * 单次查询 6 类 hitState 计数（用于任务详情展示分布）.
   * 返回 [hit_state, count] 行集合.
   */
  @Query(
      value =
          "SELECT attack_combination_result_hit_state, COUNT(*)"
              + " FROM attack_combination_results"
              + " WHERE attack_combination_result_run_id = :runId"
              + " GROUP BY attack_combination_result_hit_state",
      nativeQuery = true)
  List<Object[]> countByRunIdGroupedByHitState(@Param("runId") String runId);
}
