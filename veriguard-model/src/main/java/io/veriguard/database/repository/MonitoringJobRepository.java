package io.veriguard.database.repository;

import io.veriguard.database.model.monitoring.MonitoringJob;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 监控任务仓储 —— PR C4.
 */
@Repository
public interface MonitoringJobRepository
    extends CrudRepository<MonitoringJob, String>, JpaSpecificationExecutor<MonitoringJob> {

  Page<MonitoringJob> findAll(Pageable pageable);

  Page<MonitoringJob> findAllByEnabled(boolean enabled, Pageable pageable);

  /** Quartz job 调度循环：拉所有 enabled=true 的 job. */
  List<MonitoringJob> findAllByEnabledTrue();
}
