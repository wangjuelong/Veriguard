package io.veriguard.executors.caldera.service;

import static io.veriguard.executors.caldera.service.CalderaExecutorService.toArch;
import static io.veriguard.executors.caldera.service.CalderaExecutorService.toPlatform;
import static io.veriguard.utils.time.TimeUtils.toInstant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.Executor;
import io.veriguard.executors.ExecutorService;
import io.veriguard.executors.caldera.client.CalderaExecutorClient;
import io.veriguard.executors.caldera.config.CalderaExecutorConfig;
import io.veriguard.executors.caldera.model.Agent;
import io.veriguard.service.AgentService;
import io.veriguard.service.EndpointService;
import io.veriguard.service.NodeExecutorService;
import io.veriguard.service.PlatformSettingsService;
import io.veriguard.utils.mapper.EndpointMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CalderaExecutorServiceTest {

  private static final String CALDERA_AGENT_HOSTNAME = "calderahostname";
  private static final String CALDERA_AGENT_EXTERNAL_REF = "calderaExt";
  private static final String CALDERA_AGENT_IP = "10.10.10.10";
  private static final String CALDERA_AGENT_USERNAME = "veriguard_user";

  private static final String CALDERA_EXECUTOR_TYPE = "veriguard_caldera_executor";
  private static final String CALDERA_EXECUTOR_NAME = "Caldera";

  private String DATE;

  @Mock private ExecutorService executorService;

  @Mock private CalderaExecutorClient client;

  @Mock private CalderaExecutorConfig config;

  @Mock private CalderaExecutorContextService calderaExecutorContextService;

  @Mock private EndpointService endpointService;

  @Mock private AgentService agentService;

  @Mock private NodeExecutorService nodeExecutorService;

  @Mock private PlatformSettingsService platformSettingsService;

  @Mock private Executor executor;

  @InjectMocks private CalderaExecutorService calderaExecutorService;

  private Endpoint calderaEndpoint;
  private Endpoint randomEndpoint;
  private io.veriguard.database.model.Agent agentEndpoint;
  private Agent calderaAgent;
  private Agent randomAgent;
  private Executor calderaExecutor;
  private Executor randomExecutor;

  @BeforeEach
  void setUp() {
    Instant now = Instant.now();
    DateTimeFormatter formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault());
    DATE = formatter.format(now);

    calderaAgent = new Agent();
    calderaAgent.setArchitecture("Arch");
    calderaAgent.setPaw(CALDERA_AGENT_EXTERNAL_REF);
    calderaExecutor = new Executor();
    calderaExecutor.setName(CALDERA_EXECUTOR_NAME);
    calderaExecutor.setType(CALDERA_EXECUTOR_TYPE);
    randomExecutor = new Executor();
    randomExecutor.setName("NAME");
    randomExecutor.setType("TYPE");
    calderaExecutorService.setExecutor(calderaExecutor);

    calderaAgent =
        createAgent(
            CALDERA_AGENT_HOSTNAME,
            CALDERA_AGENT_IP,
            CALDERA_AGENT_EXTERNAL_REF,
            CALDERA_AGENT_USERNAME);
    randomAgent = createAgent("hostname", "1.1.1.1", "ref", CALDERA_AGENT_USERNAME);
    calderaEndpoint = createEndpoint(calderaAgent);
    randomEndpoint = createEndpoint(randomAgent);

    agentEndpoint = new io.veriguard.database.model.Agent();
    agentEndpoint.setProcessName(calderaAgent.getExe_name());
    agentEndpoint.setExecutor(calderaExecutor);
    agentEndpoint.setExternalReference(calderaAgent.getPaw());
    agentEndpoint.setPrivilege(io.veriguard.database.model.Agent.PRIVILEGE.admin);
    agentEndpoint.setDeploymentMode(io.veriguard.database.model.Agent.DEPLOYMENT_MODE.session);
    agentEndpoint.setExecutedByUser(calderaAgent.getUsername());
    agentEndpoint.setLastSeen(toInstant(DATE));
    agentEndpoint.setAsset(calderaEndpoint);
  }

  private Endpoint createEndpoint(Agent agent) {
    Endpoint endpoint = new Endpoint();
    endpoint.setName(agent.getHost());
    endpoint.setDescription("Asset collected by Caldera executor context.");
    endpoint.setIps(EndpointMapper.setIps(agent.getHost_ip_addrs()));
    endpoint.setHostname(agent.getHost());
    endpoint.setPlatform(toPlatform("windows"));
    endpoint.setArch(toArch("amd64"));
    return endpoint;
  }

  private Agent createAgent(String hostname, String ip, String externalRef, String username) {
    Agent agent = new Agent();
    agent.setArchitecture("amd64");
    agent.setPaw(externalRef);
    agent.setPlatform("windows");
    agent.setExe_name("exe");
    agent.setLast_seen(DATE);
    agent.setHost_ip_addrs(new String[] {ip});
    agent.setHost(hostname);
    agent.setUsername(username);
    return agent;
  }

  @Test
  void test_run_WITH_one_endpoint_one_agent() {
    when(client.agents()).thenReturn(List.of(calderaAgent));
    calderaExecutorService.run();
    ArgumentCaptor<io.veriguard.database.model.Agent> agentCaptor =
        ArgumentCaptor.forClass(io.veriguard.database.model.Agent.class);
    verify(agentService).createOrUpdateAgent(agentCaptor.capture());

    io.veriguard.database.model.Agent agent = agentCaptor.getValue();
    assertEquals(CALDERA_AGENT_EXTERNAL_REF, agent.getExternalReference());
  }

  @Test
  void test_run_WITH_2_existing_agents_same_machine() {
    when(client.agents()).thenReturn(List.of(calderaAgent));

    randomEndpoint.setHostname(CALDERA_AGENT_HOSTNAME);
    randomEndpoint.setIps(EndpointMapper.setIps(new String[] {CALDERA_AGENT_IP}));
    calderaExecutorService.run();
    ArgumentCaptor<Endpoint> endpointCaptor = ArgumentCaptor.forClass(Endpoint.class);
    verify(endpointService).createEndpoint(endpointCaptor.capture());

    Endpoint capturedEndpoint = endpointCaptor.getValue();
    assertEquals(CALDERA_AGENT_HOSTNAME, capturedEndpoint.getHostname());
    assertArrayEquals(new String[] {CALDERA_AGENT_IP}, capturedEndpoint.getIps());
    assertEquals(Endpoint.PLATFORM_TYPE.Windows, capturedEndpoint.getPlatform());
    assertEquals(Endpoint.PLATFORM_ARCH.x86_64, capturedEndpoint.getArch());
  }
}
