package io.veriguard.rest.security_validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.VeriguardSandboxNetworkRule;
import io.veriguard.database.model.VeriguardSandboxNetworkRule.Direction;
import io.veriguard.database.model.VeriguardSandboxNetworkRule.RuleAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxScriptExporterTest {

  private final SandboxScriptExporter exporter = new SandboxScriptExporter();

  @Test
  void to_iptables_with_empty_rules_returns_header_only_script() {
    String script = exporter.toIptables("勒索沙箱", List.of());

    assertThat(script).startsWith("#!/bin/sh");
    assertThat(script).contains("# 沙箱预设「勒索沙箱」未配置网络访问控制规则。");
    assertThat(script).doesNotContain("iptables -A");
  }

  @Test
  void to_iptables_renders_ingress_before_egress() {
    VeriguardSandboxNetworkRule egress =
        new VeriguardSandboxNetworkRule(Direction.EGRESS, RuleAction.ALLOW, "TCP", "10.0.0.0/8", "443");
    VeriguardSandboxNetworkRule ingress =
        new VeriguardSandboxNetworkRule(Direction.INGRESS, RuleAction.DENY, "TCP", "0.0.0.0/0", "all");

    String script = exporter.toIptables("preset", List.of(egress, ingress));

    int ingressIdx = script.indexOf("INPUT");
    int egressIdx = script.indexOf("OUTPUT");
    assertThat(ingressIdx).isPositive();
    assertThat(egressIdx).isPositive();
    assertThat(ingressIdx).isLessThan(egressIdx);
  }

  @Test
  void to_iptables_handles_icmp_with_no_ports() {
    VeriguardSandboxNetworkRule icmp =
        new VeriguardSandboxNetworkRule(Direction.EGRESS, RuleAction.ALLOW, "ICMP", "10.0.0.0/8", "none");

    String script = exporter.toIptables("preset", List.of(icmp));

    assertThat(script).contains("-p icmp");
    assertThat(script).doesNotContain("--dport");
  }

  @Test
  void to_iptables_quotes_sandbox_name_in_filename_safely() {
    String filename = exporter.iptablesFilename("勒索 \"沙箱\"\\test");

    assertThat(filename).doesNotContain("\"");
    assertThat(filename).doesNotContain("\\");
    assertThat(filename).doesNotContain(" ");
    assertThat(filename).endsWith(".iptables.sh");
  }
}
