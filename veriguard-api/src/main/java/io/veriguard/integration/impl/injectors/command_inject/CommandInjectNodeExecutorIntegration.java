package io.veriguard.integration.impl.injectors.command_inject;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.command_inject.CommandInjectContract;
import io.veriguard.injectors.command_inject.CommandInjectExecutor;
import io.veriguard.injectors.command_inject.service.CommandInjectDispatchService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

/**
 * Integration registering the {@link CommandInjectExecutor} into the injector registry (Task C.11).
 */
public class CommandInjectNodeExecutorIntegration extends IntegrationInMemory {

  public static final String COMMAND_INJECT_INJECTOR_ID = "53c7d0a5-0e74-5078-be9c-0c4e5f60718c";
  private static final String COMMAND_INJECT_INJECTOR_NAME = "Command Inject";

  private final CommandInjectContract commandInjectContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final CommandInjectDispatchService commandInjectDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {CommandInjectContract.TYPE, COMMAND_INJECT_INJECTOR_ID})
  private CommandInjectExecutor commandInjectExecutor;

  public CommandInjectNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      CommandInjectContract commandInjectContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      CommandInjectDispatchService commandInjectDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.commandInjectContract = commandInjectContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.commandInjectDispatchService = commandInjectDispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        COMMAND_INJECT_INJECTOR_ID,
        COMMAND_INJECT_INJECTOR_NAME,
        commandInjectContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.commandInjectExecutor =
        new CommandInjectExecutor(
            nodeExecutorContext, commandInjectDispatchService, attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // not possible to stop this integration
  }
}
