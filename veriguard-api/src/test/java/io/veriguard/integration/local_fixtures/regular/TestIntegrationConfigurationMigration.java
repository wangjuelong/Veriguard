package io.veriguard.integration.local_fixtures.regular;

import io.veriguard.integration.migration.ConfigurationMigration;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class TestIntegrationConfigurationMigration extends ConfigurationMigration {
  protected TestIntegrationConfigurationMigration(
      TestIntegrationConfiguration configuration,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      EncryptionFactory encryptionFactory) {
    super(
        configuration,
        TestIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
