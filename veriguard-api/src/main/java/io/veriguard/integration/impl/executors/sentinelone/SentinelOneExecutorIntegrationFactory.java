package io.veriguard.integration.impl.executors.sentinelone;

import static io.veriguard.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;

import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.config.cache.LicenseCacheManager;
import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.ee.Ee;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.integration.migration.SentinelOneExecutorConfigurationMigration;
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

// FIXME: this disables the executor altogether, but is not a feature flag. Revert right after
// release
// @Service
@Profile("!test")
@Slf4j
public class SentinelOneExecutorIntegrationFactory extends IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;
  private final SentinelOneExecutorConfigurationMigration sentinelOneExecutorConfigurationMigration;

  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public SentinelOneExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      SentinelOneExecutorConfigurationMigration sentinelOneExecutorConfigurationMigration,
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
    this.sentinelOneExecutorConfigurationMigration = sentinelOneExecutorConfigurationMigration;
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
    sentinelOneExecutorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(SENTINELONE_EXECUTOR_TYPE);
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-sentinelone.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("SentinelOne Executor");
    connector.setSlug(SENTINELONE_EXECUTOR_TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
                With SentinelOne executor register your asset in Veriguard and enable execution of Veriguard scenarios through your SentinelOne instance.
                """);
    connector.setShortDescription(
        "Enable execution of Veriguard scenarios through your SentinelOne instance.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://www.sentinelone.com");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new SentinelOneExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new SentinelOneExecutorIntegration(
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
