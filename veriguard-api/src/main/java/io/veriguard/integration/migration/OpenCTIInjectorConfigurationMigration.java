package io.veriguard.integration.migration;

import io.veriguard.injectors.opencti.config.OpenCTIInjectorConfig;
import io.veriguard.integration.impl.injectors.opencti.OpenCTIInjectorIntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenCTIInjectorConfigurationMigration extends ConfigurationMigration {
  public OpenCTIInjectorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      OpenCTIInjectorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        OpenCTIInjectorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
