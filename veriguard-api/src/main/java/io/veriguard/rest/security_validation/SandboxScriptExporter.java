package io.veriguard.rest.security_validation;

import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SandboxScriptExporter {

  public String toIptables(String sandboxName, List<VeriguardSandboxNetworkRule> rules) {
    StringBuilder out = new StringBuilder();
    out.append("#!/bin/sh\n");
    out.append("# 沙箱预设「").append(sandboxName).append("」iptables 规则导出。\n");
    out.append("# 在 CAPEv2 主机以 root 执行。\n\n");

    if (rules.isEmpty()) {
      out.append("# 沙箱预设「").append(sandboxName).append("」未配置网络访问控制规则。\n");
      return out.toString();
    }

    rules.stream()
        .sorted(Comparator.comparing(VeriguardSandboxNetworkRule::direction))
        .forEach(rule -> appendIptablesRule(out, rule));
    return out.toString();
  }

  public String toRoutingConf(String sandboxName, List<VeriguardSandboxNetworkRule> rules) {
    StringBuilder out = new StringBuilder();
    out.append("# 沙箱预设「").append(sandboxName).append("」routing.conf 片段。\n");
    out.append("# 将以下内容追加到 CAPEv2 主机的 conf/routing.conf 并重启沙箱服务。\n\n");
    if (rules.isEmpty()) {
      out.append("# 未配置规则。\n");
      return out.toString();
    }
    for (VeriguardSandboxNetworkRule rule : rules) {
      out.append("# ")
          .append(rule.direction())
          .append(" ")
          .append(rule.action())
          .append(" ")
          .append(rule.protocol())
          .append(" ")
          .append(rule.cidr())
          .append(":")
          .append(rule.ports())
          .append("\n");
    }
    return out.toString();
  }

  public String iptablesFilename(String sandboxName) {
    return safe(sandboxName) + ".iptables.sh";
  }

  public String routingConfFilename(String sandboxName) {
    return safe(sandboxName) + ".routing.conf";
  }

  private static String safe(String sandboxName) {
    return sandboxName.replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5-]+", "_");
  }

  private static void appendIptablesRule(StringBuilder out, VeriguardSandboxNetworkRule rule) {
    String chain = rule.direction() == VeriguardSandboxNetworkRule.Direction.INGRESS ? "INPUT" : "OUTPUT";
    String action = rule.action() == VeriguardSandboxNetworkRule.RuleAction.ALLOW ? "ACCEPT" : "DROP";
    String proto = rule.protocol().toLowerCase();
    out.append("iptables -A ").append(chain);
    out.append(" -s ").append(rule.cidr());
    if (!"icmp".equalsIgnoreCase(proto) && !"all".equalsIgnoreCase(proto)) {
      out.append(" -p ").append(proto);
      if (!"all".equalsIgnoreCase(rule.ports())) {
        out.append(" --dport ").append(rule.ports().replace(',', ','));
      }
    } else if ("icmp".equalsIgnoreCase(proto)) {
      out.append(" -p icmp");
    }
    out.append(" -j ").append(action).append("\n");
  }
}
