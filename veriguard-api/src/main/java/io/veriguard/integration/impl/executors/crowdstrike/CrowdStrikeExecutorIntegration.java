package io.veriguard.integration.impl.executors.crowdstrike;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.Executor;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.veriguard.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.veriguard.executors.crowdstrike.service.CrowdStrikeExecutorContextService;
import io.veriguard.executors.crowdstrike.service.CrowdStrikeExecutorService;
import io.veriguard.executors.crowdstrike.service.CrowdStrikeGarbageCollectorService;
import io.veriguard.executors.exception.ExecutorException;
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
public class CrowdStrikeExecutorIntegration extends Integration {
  public static final String CROWDSTRIKE_EXECUTOR_DEFAULT_ID =
      "b522d9bc-7ed6-44ac-9984-810dfb18f7be";
  public static final String CROWDSTRIKE_EXECUTOR_TYPE = "veriguard_crowdstrike_executor";
  public static final String CROWDSTRIKE_EXECUTOR_NAME = "CrowdStrike";
  private static final String CROWDSTRIKE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.veriguard.io/latest/deployment/ecosystem/executors/#crowdstrike-falcon-agent";

  private static final String CROWDSTRIKE_EXECUTOR_BACKGROUND_COLOR = "#E12E37";

  @QualifiedComponent(identifier = CrowdStrikeExecutorContextService.SERVICE_NAME)
  private CrowdStrikeExecutorContextService crowdStrikeExecutorContextService;

  private CrowdStrikeExecutorService crowdStrikeExecutorService;
  private CrowdStrikeGarbageCollectorService crowdStrikeGarbageCollectorService;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  private CrowdStrikeExecutorClient client;
  private CrowdStrikeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstance connectorInstance;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public CrowdStrikeExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.taskScheduler = taskScheduler;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.connectorInstanceService = connectorInstanceService;
    this.connectorInstance = connectorInstance;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the CrowdStrike Executor", e);
      throw new ExecutorException(
          e, "Error during initialization of the Executor", CROWDSTRIKE_EXECUTOR_NAME);
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
            CROWDSTRIKE_EXECUTOR_TYPE,
            CROWDSTRIKE_EXECUTOR_NAME,
            CROWDSTRIKE_EXECUTOR_DOCUMENTATION_LINK,
            CROWDSTRIKE_EXECUTOR_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-crowdstrike.png"),
            getClass().getResourceAsStream("/img/banner-crowdstrike.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    client = new CrowdStrikeExecutorClient(config, httpClientFactory);
    crowdStrikeExecutorContextService =
        new CrowdStrikeExecutorContextService(config, client, executorService);
    crowdStrikeExecutorService =
        new CrowdStrikeExecutorService(
            executor, client, config, endpointService, agentService, assetGroupService);
    crowdStrikeGarbageCollectorService =
        new CrowdStrikeGarbageCollectorService(
            config, crowdStrikeExecutorContextService, agentService);

    timers.add(
        taskScheduler.scheduleAtFixedRate(
            crowdStrikeExecutorService, Duration.ofSeconds(this.config.getApiRegisterInterval())));
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            crowdStrikeGarbageCollectorService,
            Duration.ofHours(this.config.getCleanImplantInterval())));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(CrowdStrikeExecutorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), CrowdStrikeExecutorConfig.class);
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
    timers.clear();
  }
}
