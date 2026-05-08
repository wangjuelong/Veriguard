package io.veriguard.integration.migration;

import io.veriguard.executors.tanium.config.TaniumExecutorConfig;
import io.veriguard.integration.impl.executors.tanium.TaniumExecutorIntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class TaniumExecutorConfigurationMigration extends ConfigurationMigration {
  public TaniumExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      TaniumExecutorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        TaniumExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
