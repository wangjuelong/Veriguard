package io.veriguard.executors.model;

import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.Executor;
import io.veriguard.helper.AgentHelper;
import io.veriguard.utils.mapper.EndpointMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentRegisterInput {

  private String name;
  private String[] ips;
  private String seenIp;
  private String hostname;
  private String agentVersion;
  private Endpoint.PLATFORM_TYPE platform;
  private Endpoint.PLATFORM_ARCH arch;
  private String[] macAddresses;
  private Instant lastSeen;
  private String externalReference;
  private boolean isService;
  private boolean isElevated;
  private String executedByUser;
  private Executor executor;
  private String processName;
  private String installationMode;
  private String installationDirectory;
  private String serviceName;

  /** B-ii PR-A: Agent 启动时通过配置文件声明的能力标签列表。未上报则为空。 */
  private List<String> capabilities = new ArrayList<>();

  public void setMacAddresses(String[] macAddresses) {
    this.macAddresses = EndpointMapper.setMacAddresses(macAddresses);
  }

  public void setIps(String[] ips) {
    this.ips = EndpointMapper.setIps(ips);
  }

  public void setHostname(String hostname) {
    this.hostname = hostname.toLowerCase();
  }

  public boolean isActive() {
    return new AgentHelper().isAgentActiveFromLastSeen(this.getLastSeen());
  }
}
