package io.veriguard.integration.impl.injectors.veriguard;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.config.VeriguardConfig;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.veriguard.VeriguardImplantContract;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.Integration;
import io.veriguard.integration.IntegrationFactory;
import io.veriguard.rest.inject.service.AttackChainNodeService;
import io.veriguard.service.AssetGroupService;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.catalog_connectors.CatalogConnectorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VeriguardNodeExecutorIntegrationFactory extends IntegrationFactory {

  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final NodeExecutorService nodeExecutorService;
  private final VeriguardImplantContract veriguardImplantContract;
  private final VeriguardConfig veriguardConfig;
  private final NodeExecutorContext nodeExecutorContext;
  private final AssetGroupService assetGroupService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;
  private final AttackChainNodeService attackChainNodeService;

  public VeriguardNodeExecutorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      NodeExecutorService nodeExecutorService,
      VeriguardImplantContract veriguardImplantContract,
      VeriguardConfig veriguardConfig,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      NodeExecutorContext nodeExecutorContext,
      AssetGroupService assetGroupService,
      AttackChainNodeExpectationService attackChainNodeExpectationService,
      AttackChainNodeService attackChainNodeService) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.nodeExecutorService = nodeExecutorService;
    this.veriguardImplantContract = veriguardImplantContract;
    this.veriguardConfig = veriguardConfig;
    this.nodeExecutorContext = nodeExecutorContext;
    this.assetGroupService = assetGroupService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
    this.attackChainNodeService = attackChainNodeService;
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
            VeriguardNodeExecutorIntegration.VERIGUARD_INJECTOR_ID,
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
    return new VeriguardNodeExecutorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        nodeExecutorService,
        veriguardImplantContract,
        veriguardConfig,
        nodeExecutorContext,
        assetGroupService,
        attackChainNodeExpectationService,
        attackChainNodeService);
  }
}
