package io.veriguard.integration.impl;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static io.veriguard.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.CatalogConnectorRepository;
import io.veriguard.executors.ExecutorContextService;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.tanium.client.TaniumExecutorClient;
import io.veriguard.executors.tanium.config.TaniumExecutorConfig;
import io.veriguard.integration.ComponentRequest;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.integration.impl.executors.tanium.TaniumExecutorIntegration;
import io.veriguard.integration.impl.executors.tanium.TaniumExecutorIntegrationFactory;
import io.veriguard.integration.migration.TaniumExecutorConfigurationMigration;
import io.veriguard.service.AgentService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.EndpointService;
import io.veriguard.service.FileService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import io.veriguard.utils.reflection.FieldUtils;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class TaniumExecutorIntegrationTest {
  @Autowired private TaniumExecutorClient client;
  @Autowired private EndpointService endpointService;
  @Autowired private AgentService agentService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private ExecutorService executorService;
  @Autowired private ComponentRequestEngine componentRequestEngine;
  @Autowired private ThreadPoolTaskScheduler taskScheduler;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private TaniumExecutorConfig taniumExecutorConfig;
  @Autowired private EncryptionFactory encryptionFactory;
  @Autowired private BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  @Autowired private HttpClientFactory httpClientFactory;

  @Autowired private TaniumExecutorConfigurationMigration taniumExecutorConfigurationMigration;

  @Autowired private FileService fileService;

  private TaniumExecutorIntegrationFactory getFactory() {
    return new TaniumExecutorIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        executorService,
        componentRequestEngine,
        taniumExecutorConfigurationMigration,
        agentService,
        endpointService,
        assetGroupService,
        taskScheduler,
        fileService,
        baseIntegrationConfigurationBuilder,
        httpClientFactory);
  }

  @Test
  @DisplayName("Factory is initialised correctly and creates catalog object")
  public void factoryIsInitialisedCorrectlyAndCreatesCatalogObject() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
    AssertionsForClassTypes.assertThat(connectors.getFirst().getClassName())
        .isEqualTo(TaniumExecutorIntegrationFactory.class.getCanonicalName());
  }

  @Test
  @DisplayName("When factory syncs with stopped instance, integration is of status stopped")
  public void whenFactorySyncWithStoppedInstance_integrationIsOfStatusStopped() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    List<Integration> syncedIntegrations = integrationFactory.sync(new ArrayList<>(instances));

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(TaniumExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(integration.getCurrentStatus())
                    .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped));
  }

  @Test
  @DisplayName("When factory syncs with stopped instance, integration has no component of type")
  public void whenFactorySyncWithStoppedInstance_stoppedIntegrationHasNoComponentOfType()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    List<Integration> syncedIntegrations = integrationFactory.sync(new ArrayList<>(instances));

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(TaniumExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(
                        integration.requestComponent(
                            new ComponentRequest(TANIUM_EXECUTOR_NAME),
                            ExecutorContextService.class))
                    .isEmpty());
  }

  @Test
  @DisplayName("When factory is initialised, there is an instance with correct configuration")
  public void whenFactoryIsInitialised_thereIsAnInstanceWithCorrectConfiguration()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());

    assertThat(instances)
        .first()
        .satisfies(
            instance ->
                assertThat(instance.getConfigurations())
                    .usingComparatorForType(
                        (left, right) ->
                            left.getKey().compareTo(right.getKey())
                                & left.getValue().toString().compareTo(right.getValue().toString()),
                        ConnectorInstanceConfiguration.class)
                    .hasSameElementsAs(
                        taniumExecutorConfig.toInstanceConfigurationSet(
                            instance,
                            encryptionFactory.getEncryptionService(
                                instance.getCatalogConnector()))));
  }

  @Test
  @DisplayName(
      "When factory is initialised and an instance is spawned with an unsupported connector instance type, the encryption service is null")
  public void whenInstanceIsSpawn_encryptionServiceIsNull() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    Integration integration = integrationFactory.spawn(new ConnectorInstanceInMemory());
    AssertionsForClassTypes.assertThat(
            FieldUtils.computeAllFieldValues(integration).get("encryptionService"))
        .isNull();
  }
}
