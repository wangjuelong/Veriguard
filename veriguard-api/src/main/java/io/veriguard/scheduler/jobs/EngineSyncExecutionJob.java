package io.veriguard.scheduler.jobs;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.engine.EngineContext;
import io.veriguard.engine.EngineService;
import io.veriguard.engine.EsModel;
import io.veriguard.engine.model.EsBase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class EngineSyncExecutionJob implements Job {

  private final EngineService engineService;
  private final EngineContext engineContext;

  @Override
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    List<EsModel<EsBase>> models = engineContext.getModels();
    log.info("Executing bulk parallel processing for {} models", models.size());
    engineService.bulkProcessing(models.stream().parallel());
  }
}
