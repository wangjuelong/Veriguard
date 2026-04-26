package io.veriguard.integration.impl.executors.crowdstrike;

import static io.veriguard.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_TYPE;

import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.config.cache.LicenseCacheManager;
import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.ee.Ee;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.integration.migration.CrowdStrikeExecutorConfigurationMigration;
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
public class CrowdStrikeExecutorIntegrationFactory extends IntegrationFactory {
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ComponentRequestEngine componentRequestEngine;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final CrowdStrikeExecutorConfigurationMigration crowdStrikeExecutorConfigurationMigration;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public CrowdStrikeExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ExecutorService executorService,
      Ee eeService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler,
      CrowdStrikeExecutorConfigurationMigration crowdStrikeExecutorConfigurationMigration,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.eeService = eeService;
    this.licenseCacheManager = licenseCacheManager;
    this.componentRequestEngine = componentRequestEngine;
    this.taskScheduler = taskScheduler;
    this.catalogConnectorService = catalogConnectorService;
    this.connectorInstanceService = connectorInstanceService;
    this.crowdStrikeExecutorConfigurationMigration = crowdStrikeExecutorConfigurationMigration;
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    crowdStrikeExecutorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(CROWDSTRIKE_EXECUTOR_TYPE);
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-crowdstrike.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("CrowdStrike Executor");
    connector.setSlug(CROWDSTRIKE_EXECUTOR_TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
            CrowdStrike Falcon Intelligence is an integral threat intelligence module within the Falcon platform, crafted to enhance the speed and effectiveness of threat detection, investigation, and response. It equips SOC teams to work more swiftly and intelligently, leveraging automation, enrichment, and high-fidelity data to optimize their cybersecurity operations.

            With Crowdstrike executor register your asset in Veriguard and enable execution of Veriguard scenarios through your Crowdstrike instance.
            """);
    connector.setShortDescription(
        "Enable execution of Veriguard scenarios through your Crowdstrike instance.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://www.crowdstrike.com");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new CrowdStrikeExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new CrowdStrikeExecutorIntegration(
        instance,
        connectorInstanceService,
        endpointService,
        agentService,
        assetGroupService,
        executorService,
        eeService,
        licenseCacheManager,
        componentRequestEngine,
        taskScheduler,
        baseIntegrationConfigurationBuilder,
        httpClientFactory);
  }
}
