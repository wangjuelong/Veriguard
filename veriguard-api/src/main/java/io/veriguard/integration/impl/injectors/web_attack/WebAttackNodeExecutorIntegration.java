package io.veriguard.integration.impl.injectors.web_attack;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.web_attack.WebAttackContract;
import io.veriguard.injectors.web_attack.WebAttackExecutor;
import io.veriguard.injectors.web_attack.service.WebAttackDispatchService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class WebAttackNodeExecutorIntegration extends IntegrationInMemory {

  public static final String WEB_ATTACK_INJECTOR_ID = "31a5b8e3-8c4f-4d6b-c8fa-e9f2b3c4d5e6";
  private static final String WEB_ATTACK_INJECTOR_NAME = "Web Attack";

  private final WebAttackContract webAttackContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final WebAttackDispatchService webAttackDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {WebAttackContract.TYPE, WEB_ATTACK_INJECTOR_ID})
  private WebAttackExecutor webAttackExecutor;

  public WebAttackNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      WebAttackContract webAttackContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      WebAttackDispatchService webAttackDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.webAttackContract = webAttackContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.webAttackDispatchService = webAttackDispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        WEB_ATTACK_INJECTOR_ID,
        WEB_ATTACK_INJECTOR_NAME,
        webAttackContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.webAttackExecutor =
        new WebAttackExecutor(
            nodeExecutorContext, webAttackDispatchService, attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // not possible to stop this integration
  }
}
