package io.veriguard.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VeriguardSandboxNetworkRule(
    @JsonProperty("rule_direction") @NotNull Direction direction,
    @JsonProperty("rule_action") @NotNull RuleAction action,
    @JsonProperty("rule_protocol") @NotBlank String protocol,
    @JsonProperty("rule_cidr") @NotBlank String cidr,
    @JsonProperty("rule_ports") @NotBlank String ports) {

  public enum Direction {
    INGRESS,
    EGRESS
  }

  public enum RuleAction {
    ALLOW,
    DENY
  }
}
