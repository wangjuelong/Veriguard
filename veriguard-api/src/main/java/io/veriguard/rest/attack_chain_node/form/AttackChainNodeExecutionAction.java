package io.veriguard.rest.attack_chain_node.form;

public enum AttackChainNodeExecutionAction {
  prerequisite_check,
  prerequisite_execution,
  cleanup_execution,

  command_execution,
  dns_resolution,
  file_execution,
  file_drop,

  complete,
}
