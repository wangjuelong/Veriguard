package io.veriguard.integration.impl.injectors.manual;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.manual.ManualContract;
import io.veriguard.injectors.manual.ManualExecutor;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class ManualNodeExecutorIntegration extends IntegrationInMemory {
  private static final String MANUAL_INJECTOR_NAME = "Manual";
  public static final String MANUAL_INJECTOR_ID = "6981a39d-e219-4016-a235-cf7747994abc";

  private final ManualContract manualContract;
  private final NodeExecutorContext nodeExecutorContext;

  private final NodeExecutorService nodeExecutorService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {ManualContract.TYPE, MANUAL_INJECTOR_ID})
  private ManualExecutor manualExecutor;

  public ManualNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ManualContract manualContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.manualContract = manualContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        MANUAL_INJECTOR_ID,
        MANUAL_INJECTOR_NAME,
        manualContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.manualExecutor = new ManualExecutor(nodeExecutorContext, attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
