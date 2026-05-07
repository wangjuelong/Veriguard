package io.veriguard.executors;

import static io.veriguard.database.model.ExecutionStatus.EXECUTING;
import static io.veriguard.utils.InjectionUtils.isInInjectableRange;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.veriguard.asset.QueueService;
import io.veriguard.database.model.*;
import io.veriguard.database.model.NodeExecutor;
import io.veriguard.database.repository.AttackChainNodeStatusRepository;
import io.veriguard.database.repository.NodeExecutorRepository;
import io.veriguard.execution.ExecutableNode;
import io.veriguard.execution.ExecutableNodeDTOMapper;
import io.veriguard.execution.ExecutionExecutorService;
import io.veriguard.integration.ManagerFactory;
import io.veriguard.rest.inject.service.AttackChainNodeStatusService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class Executor {

  @Resource protected ObjectMapper mapper;

  private final ApplicationContext context;

  private final AttackChainNodeStatusRepository attackChainNodeStatusRepository;
  private final NodeExecutorRepository nodeExecutorRepository;

  private final QueueService queueService;
  private final ManagerFactory managerFactory;

  private final ExecutionExecutorService executionExecutorService;
  private final AttackChainNodeStatusService attackChainNodeStatusService;
  private final ExecutableNodeDTOMapper executableAttackChainNodeDTOMapper;
  private final ConnectorInstanceService connectorInstanceService;

  public static final String CMD = "cmd";
  public static final String PSH = "psh";

  @Qualifier("coreInjectorService")
  private final NodeExecutorService nodeExecutorService;

  private AttackChainNodeStatus executeExternal(ExecutableNode executableAttackChainNode, NodeExecutor nodeExecutor)
      throws IOException, TimeoutException {
    AttackChainNode attackChainNode = executableAttackChainNode.getInjection().getAttackChainNode();
    String jsonAttackChainNode =
        mapper.writeValueAsString(
            executableAttackChainNodeDTOMapper.toExecutableNodeDTO(executableAttackChainNode));
    AttackChainNodeStatus attackChainNodeStatus =
        this.attackChainNodeStatusRepository.findByAttackChainNodeId(attackChainNode.getId()).orElseThrow();

    queueService.publish(nodeExecutorService.getOriginNodeExecutorType(nodeExecutor.getType()), jsonAttackChainNode);
    attackChainNodeStatus.addInfoTrace(
        "The inject has been published and is now waiting to be consumed.",
        ExecutionTraceAction.EXECUTION);
    return this.attackChainNodeStatusRepository.save(attackChainNodeStatus);
  }

  private AttackChainNodeStatus executeInternal(ExecutableNode executableAttackChainNode, NodeExecutor nodeExecutor) {
    AttackChainNode attackChainNode = executableAttackChainNode.getInjection().getAttackChainNode();
    io.veriguard.executors.NodeExecutor executor =
        managerFactory.getManager().requestNodeExecutorExecutorByType(nodeExecutor.getType());

    Execution execution = executor.executeInjection(executableAttackChainNode);
    // After execution, expectations are already created
    // Injection status is filled after complete execution
    // Report attackChainNode execution
    AttackChainNodeStatus attackChainNodeStatus =
        this.attackChainNodeStatusRepository.findByAttackChainNodeId(attackChainNode.getId()).orElseThrow();
    AttackChainNodeStatus completeStatus = attackChainNodeStatusService.fromExecution(execution, attackChainNodeStatus);
    return attackChainNodeStatusRepository.save(completeStatus);
  }

  public AttackChainNodeStatus execute(ExecutableNode executableAttackChainNode) throws Exception {
    AttackChainNode attackChainNode = executableAttackChainNode.getInjection().getAttackChainNode();
    NodeContract nodeContract =
        attackChainNode
            .getNodeContract()
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    // Depending on nodeExecutor type (internal or external) execution must be done differently
    NodeExecutor nodeExecutor =
        nodeExecutorRepository
            .findByType(nodeContract.getNodeExecutor().getType())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Injector not found for type: "
                            + nodeContract.getNodeExecutor().getType()));

    boolean hasStartedConnectorInstanceForNodeExecutor =
        this.connectorInstanceService.hasStartedConnectorInstanceForNodeExecutor(nodeExecutor.getId());
    if (!hasStartedConnectorInstanceForNodeExecutor) {
      throw new IllegalStateException(
          "No started connector instance found for injector type: " + nodeExecutor.getType());
    }

    // Status
    AttackChainNodeStatus updatedStatus =
        this.attackChainNodeStatusService.initializeAttackChainNodeStatus(attackChainNode.getId(), EXECUTING);
    attackChainNode.setStatus(updatedStatus);
    if (Boolean.TRUE.equals(nodeContract.getNeedsExecutor())) {
      this.executionExecutorService.launchExecutorContext(attackChainNode);
    }
    if (nodeExecutor.isExternal()) {
      return executeExternal(executableAttackChainNode, nodeExecutor);
    } else {
      return executeInternal(executableAttackChainNode, nodeExecutor);
    }
  }

  public AttackChainNodeStatus directExecute(ExecutableNode executableAttackChainNode) throws Exception {
    boolean isScheduledAttackChainNode = !executableAttackChainNode.isDirect();
    // If empty content, attackChainNode must be rejected
    AttackChainNode attackChainNode = executableAttackChainNode.getInjection().getAttackChainNode();
    if (attackChainNode.getContent() == null) {
      throw new UnsupportedOperationException("Inject is empty");
    }
    // If attackChainNode is too old, reject the execution
    if (isScheduledAttackChainNode && !isInInjectableRange(attackChainNode)) {
      throw new UnsupportedOperationException(
          "Inject is now too old for execution: id "
              + attackChainNode.getId()
              + ", launch date "
              + attackChainNode.getDate()
              + ", now date "
              + Instant.now());
    }

    return this.execute(executableAttackChainNode);
  }
}
