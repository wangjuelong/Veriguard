package io.veriguard.database.repository;

import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 攻击组合任务仓储 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 */
@Repository
public interface AttackCombinationRunRepository
    extends CrudRepository<AttackCombinationRun, String>,
        JpaSpecificationExecutor<AttackCombinationRun> {

  Page<AttackCombinationRun> findAll(Pageable pageable);

  Page<AttackCombinationRun> findAllByStatus(AttackCombinationRunStatus status, Pageable pageable);

  List<AttackCombinationRun> findAllByStatusInAndExpiresAtBefore(
      List<AttackCombinationRunStatus> statuses, Instant cutoff);

  long countByStatus(AttackCombinationRunStatus status);
}
