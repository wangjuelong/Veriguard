package io.veriguard.integration.impl.injectors.manual;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.manual.ManualContract;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ManualNodeExecutorIntegrationFactory extends IntegrationFactory {

  private final ManualContract manualContract;
  private final NodeExecutorContext nodeExecutorContext;

  private final ConnectorInstanceService connectorInstanceService;
  private final NodeExecutorService nodeExecutorService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  private final ComponentRequestEngine componentRequestEngine;

  public ManualNodeExecutorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ManualContract manualContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      AttackChainNodeExpectationService attackChainNodeExpectationService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.manualContract = manualContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            ManualNodeExecutorIntegration.MANUAL_INJECTOR_ID,
            this.getClassName(),
            ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new ManualNodeExecutorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        manualContract,
        nodeExecutorContext,
        nodeExecutorService,
        attackChainNodeExpectationService);
  }
}
