package io.veriguard.integration.local_fixtures.regular;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import io.veriguard.service.connector_instances.EncryptionService;

public class TestIntegration extends Integration {
  public static final String TEST_COMPONENT_IDENTIFIER = "test_component_identifier";

  @QualifiedComponent(identifier = TEST_COMPONENT_IDENTIFIER)
  private TestIntegrationComponent testIntegrationComponent;

  public TestIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EncryptionService encryptionService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
  }

  @Override
  protected void innerStart() throws Exception {
    testIntegrationComponent = new TestIntegrationComponent();
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
