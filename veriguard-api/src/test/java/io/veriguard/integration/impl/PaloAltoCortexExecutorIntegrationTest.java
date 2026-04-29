package io.veriguard.integration.impl;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstanceInMemory;
import io.veriguard.database.repository.CatalogConnectorRepository;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.veriguard.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegrationFactory;
import io.veriguard.service.*;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.utils.reflection.FieldUtils;
import io.veriguard.utilstest.RabbitMQTestListener;
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
public class PaloAltoCortexExecutorIntegrationTest {
  @Autowired private PaloAltoCortexExecutorClient client;
  @Autowired private EndpointService endpointService;
  @Autowired private AgentService agentService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private ExecutorService executorService;
  @Autowired private ComponentRequestEngine componentRequestEngine;
  @Autowired private ThreadPoolTaskScheduler taskScheduler;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private HttpClientFactory httpClientFactory;
  @Autowired private BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  @Autowired private PreviewFeatureService previewFeatureService;

  @Autowired private FileService fileService;

  private PaloAltoCortexExecutorIntegrationFactory getFactory() {
    return new PaloAltoCortexExecutorIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        executorService,
        componentRequestEngine,
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
        .isEqualTo(PaloAltoCortexExecutorIntegrationFactory.class.getCanonicalName());
  }

  @Test
  @DisplayName("When factory is initialised, there is a connector with correct configuration")
  public void whenFactoryIsInitialised_thereIsAConnectorWithCorrectConfiguration()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
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
