package io.veriguard.integration.local_fixtures.integration_throws;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.service.connector_instances.ConnectorInstanceService;

public class TestIntegrationStartThrows extends Integration {
  public static final String THROWING_INTEGRATION_ID = "cc35b890-3d4a-4a1f-842b-857736f34783";

  public TestIntegrationStartThrows(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
  }

  @Override
  protected void innerStart() throws Exception {
    throw new RuntimeException("throw exception on start()");
  }

  @Override
  protected void refresh() throws Exception {
    // noop
  }

  @Override
  protected void innerStop() {
    // noop
  }
}
