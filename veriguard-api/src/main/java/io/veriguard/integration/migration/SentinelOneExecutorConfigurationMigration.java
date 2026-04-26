package io.veriguard.integration.migration;

import io.veriguard.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.veriguard.integration.impl.executors.sentinelone.SentinelOneExecutorIntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class SentinelOneExecutorConfigurationMigration extends ConfigurationMigration {
  public SentinelOneExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      SentinelOneExecutorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        SentinelOneExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
