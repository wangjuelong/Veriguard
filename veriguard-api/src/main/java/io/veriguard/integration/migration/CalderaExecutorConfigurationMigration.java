package io.veriguard.integration.migration;

import io.veriguard.executors.caldera.config.CalderaExecutorConfig;
import io.veriguard.integration.impl.executors.caldera.CalderaExecutorIntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class CalderaExecutorConfigurationMigration extends ConfigurationMigration {
  protected CalderaExecutorConfigurationMigration(
      CalderaExecutorConfig configuration,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      EncryptionFactory encryptionFactory) {
    super(
        configuration,
        CalderaExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
