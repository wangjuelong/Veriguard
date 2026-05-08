package io.veriguard.executors.paloaltocortex.service;

import static io.veriguard.executors.ExecutorHelper.*;
import static io.veriguard.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.veriguard.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_TYPE;

import io.veriguard.database.model.Agent;
import io.veriguard.database.model.Endpoint;
import io.veriguard.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.veriguard.executors.paloaltocortex.model.PaloAltoCortexAction;
import io.veriguard.executors.paloaltocortex.model.PaloAltoCortexCommand;
import io.veriguard.executors.paloaltocortex.model.PaloAltoCortexCommandList;
import io.veriguard.service.AgentService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaloAltoCortexGarbageCollectorService implements Runnable {

  private final PaloAltoCortexExecutorConfig config;
  private final PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService;
  private final AgentService agentService;

  public PaloAltoCortexGarbageCollectorService(
      PaloAltoCortexExecutorConfig config,
      PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService,
      AgentService agentService) {
    this.config = config;
    this.paloAltoCortexExecutorContextService = paloAltoCortexExecutorContextService;
    this.agentService = agentService;
  }

  @Override
  public void run() {
    List<Agent> agents = this.agentService.getAgentsByExecutorType(PALOALTOCORTEX_EXECUTOR_TYPE);
    if (!agents.isEmpty()) {
      log.info(
          "Running Palo Alto Cortex executor garbage collector on " + agents.size() + " agents");
      List<PaloAltoCortexAction> actions = new ArrayList<>();
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      for (Agent agent : windowsAgents) {
        PaloAltoCortexAction action = new PaloAltoCortexAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getWindowsScriptUid());
        PaloAltoCortexCommandList commandWindows = new PaloAltoCortexCommandList();
        commandWindows.setCommands_list(
            List.of(
                POWERSHELL_CMD
                    + Base64.getEncoder()
                        .encodeToString(
                            WINDOWS_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_16LE))));
        action.setCommandWindows(commandWindows);
        actions.add(action);
      }
      List<Agent> unixAgents = new ArrayList<>();
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Linux));
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.MacOS));
      for (Agent agent : unixAgents) {
        PaloAltoCortexAction action = new PaloAltoCortexAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getUnixScriptUid());
        PaloAltoCortexCommand commandUnix = new PaloAltoCortexCommand();
        commandUnix.setCommand(
            Base64.getEncoder()
                .encodeToString(UNIX_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_8)));
        action.setCommandUnix(commandUnix);
        actions.add(action);
      }
      paloAltoCortexExecutorContextService.executeActions(actions);
    }
  }
}
