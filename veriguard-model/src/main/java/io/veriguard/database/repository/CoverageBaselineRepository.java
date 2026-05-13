package io.veriguard.database.repository;

import io.veriguard.database.model.coverage.CoverageBaseline;
import io.veriguard.database.model.coverage.CoverageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 覆盖度基线仓储 —— PR C3.
 */
@Repository
public interface CoverageBaselineRepository
    extends CrudRepository<CoverageBaseline, String>,
        JpaSpecificationExecutor<CoverageBaseline> {

  Page<CoverageBaseline> findAll(Pageable pageable);

  Page<CoverageBaseline> findAllByCoverageType(CoverageType coverageType, Pageable pageable);
}
