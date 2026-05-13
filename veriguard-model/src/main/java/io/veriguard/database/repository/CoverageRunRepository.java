package io.veriguard.database.repository;

import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.model.coverage.CoverageRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 覆盖度任务仓储 —— PR C3.
 */
@Repository
public interface CoverageRunRepository
    extends CrudRepository<CoverageRun, String>, JpaSpecificationExecutor<CoverageRun> {

  Page<CoverageRun> findAll(Pageable pageable);

  Page<CoverageRun> findAllByBaselineId(String baselineId, Pageable pageable);

  Page<CoverageRun> findAllByStatus(CoverageRunStatus status, Pageable pageable);

  Page<CoverageRun> findAllByBaselineIdAndStatus(
      String baselineId, CoverageRunStatus status, Pageable pageable);

  long countByStatus(CoverageRunStatus status);
}
