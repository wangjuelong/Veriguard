package io.veriguard.integration.impl.executors.caldera;

import static io.veriguard.integration.impl.executors.caldera.CalderaExecutorIntegration.CALDERA_EXECUTOR_TYPE;

import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.caldera.config.CalderaExecutorConfig;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.integration.migration.CalderaExecutorConfigurationMigration;
import io.veriguard.service.AgentService;
import io.veriguard.service.EndpointService;
import io.veriguard.service.FileService;
import io.veriguard.service.InjectorService;
import io.veriguard.service.PlatformSettingsService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@Slf4j
public class CalderaExecutorIntegrationFactory extends IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;
  private final CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration;

  private final AgentService agentService;
  private final EndpointService endpointService;
  private final InjectorService injectorService;
  private final PlatformSettingsService platformSettingsService;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public CalderaExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration,
      AgentService agentService,
      EndpointService endpointService,
      InjectorService injectorService,
      PlatformSettingsService platformSettingsService,
      ThreadPoolTaskScheduler taskScheduler,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorService = catalogConnectorService;
    this.calderaExecutorConfigurationMigration = calderaExecutorConfigurationMigration;
    this.agentService = agentService;
    this.endpointService = endpointService;
    this.injectorService = injectorService;
    this.platformSettingsService = platformSettingsService;
    this.taskScheduler = taskScheduler;
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    calderaExecutorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(CALDERA_EXECUTOR_TYPE);
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-caldera.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Caldera Executor");
    connector.setSlug(CALDERA_EXECUTOR_TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        "With Caldera executor register your asset in Veriguard and enable execution of Veriguard scenarios through your Caldera instance.");
    connector.setShortDescription(
        "Enable execution of Veriguard scenarios through your Caldera instance.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://caldera.mitre.org/");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new CalderaExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new CalderaExecutorIntegration(
        instance,
        connectorInstanceService,
        endpointService,
        agentService,
        executorService,
        componentRequestEngine,
        platformSettingsService,
        injectorService,
        taskScheduler,
        baseIntegrationConfigurationBuilder,
        httpClientFactory);
  }
}
