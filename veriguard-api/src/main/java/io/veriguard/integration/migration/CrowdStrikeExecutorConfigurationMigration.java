package io.veriguard.integration.migration;

import io.veriguard.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.veriguard.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class CrowdStrikeExecutorConfigurationMigration extends ConfigurationMigration {
  public CrowdStrikeExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      CrowdStrikeExecutorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
