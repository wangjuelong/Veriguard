package io.veriguard.integration.impl.executors.sentinelone;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.Executor;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.exception.ExecutorException;
import io.veriguard.executors.sentinelone.client.SentinelOneExecutorClient;
import io.veriguard.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.veriguard.executors.sentinelone.service.SentinelOneExecutorContextService;
import io.veriguard.executors.sentinelone.service.SentinelOneExecutorService;
import io.veriguard.executors.sentinelone.service.SentinelOneGarbageCollectorService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.service.AgentService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.EndpointService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
public class SentinelOneExecutorIntegration extends Integration {
  public static final String SENTINELONE_EXECUTOR_DEFAULT_ID =
      "b586bc98-839c-45bd-b9e4-c10830ebfefa";
  public static final String SENTINELONE_EXECUTOR_TYPE = "veriguard_sentinelone_executor";
  public static final String SENTINELONE_EXECUTOR_NAME = "SentinelOne";
  private static final String SENTINELONE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.veriguard.io/latest/deployment/ecosystem/executors/#sentinelone-agent";
  private static final String SENTINELONE_EXECUTOR_BACKGROUND_COLOR = "#6001FC";

  @QualifiedComponent(identifier = SENTINELONE_EXECUTOR_NAME)
  private SentinelOneExecutorContextService sentinelOneExecutorContextService;

  private SentinelOneExecutorService sentinelOneExecutorService;
  private SentinelOneGarbageCollectorService sentinelOneGarbageCollectorService;

  private SentinelOneExecutorConfig config;
  private SentinelOneExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstance connectorInstance;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  public SentinelOneExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ComponentRequestEngine componentRequestEngine,
      ExecutorService executorService,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.taskScheduler = taskScheduler;
    this.connectorInstanceService = connectorInstanceService;
    this.connectorInstance = connectorInstance;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the SentinelOne Executor", e);
      throw new ExecutorException(
          e, "Error during initialization of the Executor", SENTINELONE_EXECUTOR_NAME);
    }
  }

  @Override
  protected void innerStart() throws Exception {
    String executorId =
        connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
            connectorInstance.getId(), ConnectorType.EXECUTOR.getIdKeyName());

    Executor executor =
        executorService.register(
            executorId,
            SENTINELONE_EXECUTOR_TYPE,
            SENTINELONE_EXECUTOR_NAME,
            SENTINELONE_EXECUTOR_DOCUMENTATION_LINK,
            SENTINELONE_EXECUTOR_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-sentinelone.png"),
            getClass().getResourceAsStream("/img/banner-sentinelone.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    client = new SentinelOneExecutorClient(config, httpClientFactory);
    sentinelOneExecutorContextService =
        new SentinelOneExecutorContextService(config, client, executorService);
    sentinelOneExecutorService =
        new SentinelOneExecutorService(
            executor, client, endpointService, agentService, assetGroupService);
    sentinelOneGarbageCollectorService =
        new SentinelOneGarbageCollectorService(
            config, sentinelOneExecutorContextService, agentService);

    timers.add(
        taskScheduler.scheduleAtFixedRate(
            sentinelOneExecutorService, Duration.ofSeconds(this.config.getApiRegisterInterval())));
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            sentinelOneGarbageCollectorService,
            Duration.ofHours(this.config.getCleanImplantInterval())));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(SentinelOneExecutorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), SentinelOneExecutorConfig.class);
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
    timers.clear();
  }
}
