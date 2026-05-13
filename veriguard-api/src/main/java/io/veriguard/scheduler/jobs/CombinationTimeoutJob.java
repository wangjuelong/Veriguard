package io.veriguard.scheduler.jobs;

import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 攻击组合任务超时清理 Job —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>Quartz 周期任务：每 5 min 扫描 status in (running, paused) AND expires_at &lt; now 的 run，
 * 强制标 failed + completed_at = now. 防止任务在被遗忘 / 错误状态下永久占用 dispatcher.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class CombinationTimeoutJob implements Job {

  public static final String JOB_NAME = "CombinationTimeoutJob";
  public static final String TRIGGER_NAME = "CombinationTimeoutTrigger";

  private static final List<AttackCombinationRunStatus> ACTIVE_STATUSES =
      List.of(AttackCombinationRunStatus.running, AttackCombinationRunStatus.paused);

  private final AttackCombinationRunRepository runRepository;
  private final TransactionTemplate transactionTemplate;

  @Override
  public void execute(JobExecutionContext context) {
    runTimeoutSweep();
  }

  /** 测试 / 显式调用入口. */
  public int runTimeoutSweep() {
    Instant now = Instant.now();
    List<AttackCombinationRun> expired =
        runRepository.findAllByStatusInAndExpiresAtBefore(ACTIVE_STATUSES, now);
    if (expired.isEmpty()) {
      return 0;
    }
    log.warn("Found {} expired combination runs at {}", expired.size(), now);
    transactionTemplate.executeWithoutResult(
        status -> {
          for (AttackCombinationRun run : expired) {
            log.warn(
                "Marking run {} ({}→failed) as timeout-expired",
                run.getId(),
                run.getStatus());
            run.setStatus(AttackCombinationRunStatus.failed);
            run.setCompletedAt(now);
            runRepository.save(run);
          }
        });
    return expired.size();
  }
}
