package io.veriguard.integration.local_fixtures.integration_throws;

import static io.veriguard.integration.local_fixtures.integration_throws.TestIntegrationStartThrows.THROWING_INTEGRATION_ID;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class TestIntegrationFactoryIntegrationThrows extends IntegrationFactory {
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;

  public TestIntegrationFactoryIntegrationThrows(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      ComponentRequestEngine componentRequestEngine) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
  }

  @Override
  protected void runMigrations() throws Exception {}

  @Override
  protected void insertCatalogEntry() throws Exception {}

  @Override
  protected String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            THROWING_INTEGRATION_ID, this.getClassName(), ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new TestIntegrationStartThrows(
        componentRequestEngine, instance, connectorInstanceService);
  }
}
