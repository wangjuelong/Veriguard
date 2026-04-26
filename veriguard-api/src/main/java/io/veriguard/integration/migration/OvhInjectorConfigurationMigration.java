package io.veriguard.integration.migration;

import io.veriguard.injectors.ovh.config.OvhSmsInjectorConfig;
import io.veriguard.integration.impl.injectors.ovh.OvhInjectorIntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class OvhInjectorConfigurationMigration extends ConfigurationMigration {
  public OvhInjectorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      OvhSmsInjectorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        OvhInjectorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
