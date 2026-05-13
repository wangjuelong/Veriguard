package io.veriguard.database.repository;

import io.veriguard.database.model.coverage.CoverageHitState;
import io.veriguard.database.model.coverage.CoverageResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 覆盖度单元格仓储 —— PR C3.
 */
@Repository
public interface CoverageResultRepository
    extends CrudRepository<CoverageResult, String>,
        JpaSpecificationExecutor<CoverageResult> {

  Page<CoverageResult> findAllByRunId(String runId, Pageable pageable);

  Page<CoverageResult> findAllByRunIdAndHitState(
      String runId, CoverageHitState hitState, Pageable pageable);

  List<CoverageResult> findAllByRunId(String runId);

  long countByRunId(String runId);

  long countByRunIdAndHitState(String runId, CoverageHitState hitState);

  /** 单次查询 4 类 hitState 计数，便于 run.detail 和 matrix view 汇总. */
  @Query(
      value =
          "SELECT coverage_result_hit_state, COUNT(*)"
              + " FROM coverage_results"
              + " WHERE coverage_result_run_id = :runId"
              + " GROUP BY coverage_result_hit_state",
      nativeQuery = true)
  List<Object[]> countByRunIdGroupedByHitState(@Param("runId") String runId);

  void deleteByRunId(String runId);
}
