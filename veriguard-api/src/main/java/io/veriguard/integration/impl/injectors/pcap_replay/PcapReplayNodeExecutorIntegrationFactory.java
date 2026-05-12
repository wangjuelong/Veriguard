package io.veriguard.integration.impl.injectors.pcap_replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.veriguard.authorisation.HttpClientFactory;
import io.veriguard.database.model.ConnectorInstance;
import io.veriguard.database.model.ConnectorType;
import io.veriguard.executors.NodeExecutorContext;
import io.veriguard.injectors.pcap_replay.PcapReplayContract;
import io.veriguard.injectors.pcap_replay.service.PcapReplayDispatchService;
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
public class PcapReplayNodeExecutorIntegrationFactory extends IntegrationFactory {

  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final PcapReplayContract pcapReplayContract;
  private final NodeExecutorContext nodeExecutorContext;
  private final NodeExecutorService nodeExecutorService;
  private final PcapReplayDispatchService pcapReplayDispatchService;
  private final AttackChainNodeExpectationService attackChainNodeExpectationService;

  public PcapReplayNodeExecutorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      PcapReplayContract pcapReplayContract,
      NodeExecutorContext nodeExecutorContext,
      NodeExecutorService nodeExecutorService,
      PcapReplayDispatchService pcapReplayDispatchService,
      AttackChainNodeExpectationService attackChainNodeExpectationService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.pcapReplayContract = pcapReplayContract;
    this.nodeExecutorContext = nodeExecutorContext;
    this.nodeExecutorService = nodeExecutorService;
    this.pcapReplayDispatchService = pcapReplayDispatchService;
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
            PcapReplayNodeExecutorIntegration.PCAP_REPLAY_INJECTOR_ID,
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
    return new PcapReplayNodeExecutorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        pcapReplayContract,
        nodeExecutorContext,
        nodeExecutorService,
        pcapReplayDispatchService,
        attackChainNodeExpectationService);
  }
}
