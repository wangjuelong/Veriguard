package io.veriguard.integration.impl.executors.tanium;

import static io.veriguard.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_TYPE;

import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.config.cache.LicenseCacheManager;
import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.ee.Ee;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.tanium.config.TaniumExecutorConfig;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.integration.migration.TaniumExecutorConfigurationMigration;
import io.veriguard.service.AgentService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.EndpointService;
import io.veriguard.service.FileService;
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
public class TaniumExecutorIntegrationFactory extends IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;
  private final TaniumExecutorConfigurationMigration taniumExecutorConfigurationMigration;

  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public TaniumExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      TaniumExecutorConfigurationMigration taniumExecutorConfigurationMigration,
      AgentService agentService,
      EndpointService endpointService,
      AssetGroupService assetGroupService,
      Ee eeService,
      LicenseCacheManager licenseCacheManager,
      ThreadPoolTaskScheduler taskScheduler,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorService = catalogConnectorService;
    this.taniumExecutorConfigurationMigration = taniumExecutorConfigurationMigration;
    this.agentService = agentService;
    this.endpointService = endpointService;
    this.assetGroupService = assetGroupService;
    this.eeService = eeService;
    this.licenseCacheManager = licenseCacheManager;
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
    taniumExecutorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(TANIUM_EXECUTOR_TYPE);
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-tanium.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Tanium Executor");
    connector.setSlug(TANIUM_EXECUTOR_TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
                With Tanium executor register your asset in Veriguard and enable execution of Veriguard scenarios through your Tanium instance.
                """);
    connector.setShortDescription(
        "Enable execution of Veriguard scenarios through your Tanium instance.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://www.tanium.com");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new TaniumExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new TaniumExecutorIntegration(
        instance,
        connectorInstanceService,
        endpointService,
        agentService,
        assetGroupService,
        eeService,
        licenseCacheManager,
        componentRequestEngine,
        executorService,
        taskScheduler,
        baseIntegrationConfigurationBuilder,
        httpClientFactory);
  }
}
