package io.veriguard.integration.impl.executors.caldera;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.*;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.caldera.client.CalderaExecutorClient;
import io.veriguard.executors.caldera.config.CalderaExecutorConfig;
import io.veriguard.executors.caldera.service.CalderaExecutorContextService;
import io.veriguard.executors.caldera.service.CalderaExecutorService;
import io.veriguard.executors.exception.ExecutorException;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.service.AgentService;
import io.veriguard.service.EndpointService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.PlatformSettingsService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
public class CalderaExecutorIntegration extends Integration {
  public static final String CALDERA_EXECUTOR_DEFAULT_ID = "20696a66-5780-4cbe-b5c1-be43efddb3f7";
  public static final String CALDERA_EXECUTOR_TYPE = "veriguard_caldera_executor";
  public static final String CALDERA_EXECUTOR_NAME = "Caldera";
  public static final String CALDERA_BACKGROUND_COLOR = "#8B1316";

  @QualifiedComponent(identifier = CALDERA_EXECUTOR_NAME)
  private CalderaExecutorContextService calderaExecutorContextService;

  private CalderaExecutorService calderaExecutorService;

  private CalderaExecutorConfig config;
  private CalderaExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final NodeExecutorService nodeExecutorService;
  private final PlatformSettingsService platformSettingsService;
  private final ExecutorService executorService;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstance connectorInstance;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  public CalderaExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EndpointService endpointService,
      AgentService agentService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      PlatformSettingsService platformSettingsService,
      NodeExecutorService nodeExecutorService,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.platformSettingsService = platformSettingsService;
    this.nodeExecutorService = nodeExecutorService;
    this.taskScheduler = taskScheduler;
    this.executorService = executorService;
    this.connectorInstanceService = connectorInstanceService;
    this.connectorInstance = connectorInstance;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the Caldera Executor", e);
      throw new ExecutorException(
          e, "Error during initialization of the Executor", CALDERA_EXECUTOR_NAME);
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
            CALDERA_EXECUTOR_TYPE,
            CALDERA_EXECUTOR_NAME,
            null,
            CALDERA_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-caldera.png"),
            getClass().getResourceAsStream("/img/banner-caldera.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    client = new CalderaExecutorClient(config, httpClientFactory);
    calderaExecutorContextService =
        new CalderaExecutorContextService(config, nodeExecutorService, client);
    calderaExecutorService =
        new CalderaExecutorService(
            executor,
            client,
            config,
            calderaExecutorContextService,
            endpointService,
            nodeExecutorService,
            platformSettingsService,
            agentService);

    calderaExecutorContextService.registerAbilities();

    timers.add(
        this.taskScheduler.scheduleAtFixedRate(calderaExecutorService, Duration.ofSeconds(60)));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(CalderaExecutorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), CalderaExecutorConfig.class);
  }

  @Override
  protected void innerStop() {
    this.platformSettingsService.cleanMessage(BannerMessage.BANNER_KEYS.CALDERA_UNAVAILABLE);
    timers.forEach(timer -> timer.cancel(true));
    timers.clear();
  }
}
