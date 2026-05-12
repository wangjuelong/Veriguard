package io.veriguard.integration.impl.injectors.pcap_replay;

import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.PcapReplayExecutor;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
import io.veriguard.integration.ComponentRequestEngine;
import io.veriguard.integration.IntegrationInMemory;
import io.veriguard.integration.QualifiedComponent;
import io.veriguard.service.AttackChainNodeExpectationService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class PcapReplayNodeExecutorIntegration extends IntegrationInMemory {

  public static final String PCAP_REPLAY_INJECTOR_ID = "42b6c9f4-9d5a-4e7c-d9fb-fa3c4d5e6f7a";
  private static final String PCAP_REPLAY_INJECTOR_NAME = "PCAP Replay";

  private final PcapReplayContract pcapReplayContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final PcapReplayDispatchService pcapReplayDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  @QualifiedComponent(identifier = {PcapReplayContract.TYPE, PCAP_REPLAY_INJECTOR_ID})
  private PcapReplayExecutor pcapReplayExecutor;

  public PcapReplayNodeExecutorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      PcapReplayContract pcapReplayContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      PcapReplayDispatchService pcapReplayDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.pcapReplayContract = pcapReplayContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.pcapReplayDispatchService = pcapReplayDispatchService;
    this.attackChainNodeExpectationService = attackChainNodeExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    nodeExecutorService.registerBuiltinNodeExecutor(
        PCAP_REPLAY_INJECTOR_ID,
        PCAP_REPLAY_INJECTOR_NAME,
        pcapReplayContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.pcapReplayExecutor =
        new PcapReplayExecutor(
            nodeExecutorContext, pcapReplayDispatchService, attackChainNodeExpectationService);
  }

  @Override
  protected void innerStop() {
    // not possible to stop this integration
  }
}
