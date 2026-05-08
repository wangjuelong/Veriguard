package io.veriguard.integration.migration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.veriguard.database.model.CatalogConnector;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorInstanceConfiguration;
import io.veriguard.database.model.ConnectorInstancePersisted;
import io.veriguard.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.veriguard.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import io.veriguard.utils.fixtures.CatalogConnectorFixture;
import io.veriguard.utils.fixtures.composers.CatalogConnectorComposer;
import io.veriguard.utils.mockConfig.executors.WithMockCrowdstrikeConfig;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

public class CrowdStrikeExecutorConfigurationMigrationTest {

  @Nested
  @SpringBootTest
  @Transactional
  @TestExecutionListeners(
      value = {RabbitMQTestListener.class},
      mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
  @WithMockCrowdstrikeConfig(
      enable = true,
      apiUrl = "cs_api",
      clientId = "cs_client_id",
      clientSecret = "cs_client_secret",
      hostGroup = "cs_host_group",
      apiRegisterInterval = 1234,
      unixScriptName = "cs_unix_script_name",
      windowsScriptName = "cs_windows_script_name")
  @DisplayName("With enabled configuration")
  public class WithEnabledConfiguration {

    @Autowired
    private CrowdStrikeExecutorConfigurationMigration crowdStrikeExecutorConfigurationMigration;

    @Autowired private CatalogConnectorService catalogConnectorService;
    @Autowired private ConnectorInstanceService connectorInstanceService;
    @Autowired private CatalogConnectorComposer catalogConnectorComposer;
    @Autowired private EncryptionFactory encryptionFactory;

    @Autowired private CrowdStrikeExecutorConfig beanConfig;

    @Test
    @DisplayName("Resulting instance is running")
    public void whenConfigIsEnabled_resultingInstanceIsRunning() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      crowdStrikeExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName());
      assertThat(connector).isPresent();

      ConnectorInstancePersisted instance =
          connectorInstanceService.findAllByCatalogConnector(connector.get()).getFirst();

      assertThat(instance).isInstanceOf(ConnectorInstancePersisted.class);
      assertThat(instance.getRequestedStatus())
          .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    }

    @Test
    @DisplayName("It migrates the config")
    public void whenConfigIsEnabled_itMigratesTheConfig() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      crowdStrikeExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName());
      assertThat(connector).isPresent();

      ConnectorInstancePersisted instance =
          connectorInstanceService.findAllByCatalogConnector(connector.get()).getFirst();

      assertThat(instance).isInstanceOf(ConnectorInstancePersisted.class);
      assertThat(instance.getConfigurations())
          .usingComparatorForType(
              (left, right) ->
                  left.getKey().compareTo(right.getKey())
                      & left.getValue().toString().compareTo(right.getValue().toString()),
              ConnectorInstanceConfiguration.class)
          .hasSameElementsAs(
              beanConfig.toInstanceConfigurationSet(
                  instance,
                  encryptionFactory.getEncryptionService(instance.getCatalogConnector())));

      assertThat("cs_client_secret")
          .isNotEqualTo(
              instance.getConfigurations().stream()
                  .filter(
                      connectorInstanceConfiguration ->
                          "EXECUTOR_CROWDSTRIKE_CLIENT_SECRET"
                              .equals(connectorInstanceConfiguration.getKey()))
                  .findFirst()
                  .orElseThrow()
                  .getValue()
                  .asText());
    }
  }

  @Nested
  @SpringBootTest
  @Transactional
  @TestExecutionListeners(
      value = {RabbitMQTestListener.class},
      mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
  @WithMockCrowdstrikeConfig(
      enable = false,
      apiUrl = "cs_api",
      clientId = "cs_client_id",
      clientSecret = "cs_client_secret",
      hostGroup = "cs_host_group",
      apiRegisterInterval = 1234,
      unixScriptName = "cs_unix_script_name",
      windowsScriptName = "cs_windows_script_name")
  @DisplayName("With disabled configuration")
  public class WithDisabledConfiguration {

    @Autowired
    private CrowdStrikeExecutorConfigurationMigration crowdStrikeExecutorConfigurationMigration;

    @Autowired private CatalogConnectorService catalogConnectorService;
    @Autowired private ConnectorInstanceService connectorInstanceService;
    @Autowired private CatalogConnectorComposer catalogConnectorComposer;

    @Test
    @DisplayName("Resulting instance is stopped")
    public void whenConfigIsEnabled_resultingInstanceIsStopped() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      crowdStrikeExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName());
      assertThat(connector).isPresent();

      ConnectorInstancePersisted instance =
          connectorInstanceService.findAllByCatalogConnector(connector.get()).getFirst();

      assertThat(instance).isInstanceOf(ConnectorInstancePersisted.class);
      assertThat(instance.getRequestedStatus())
          .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    }
  }
}
