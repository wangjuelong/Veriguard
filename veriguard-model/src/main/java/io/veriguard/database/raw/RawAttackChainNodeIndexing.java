package io.veriguard.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawAttackChainNodeIndexing {

  String getNode_id();

  String getNode_title();

  Instant getNode_created_at();

  Instant getNode_updated_at();

  String getNode_injector_contract();

  Instant getInjector_contract_updated_at();

  Instant getTracking_sent_date();

  Set<String> getNode_platforms();

  Set<String> getNode_attack_patterns();

  Set<String> getNode_children();

  Set<String> getAttack_patterns_children();

  Set<String> getNode_kill_chain_phases();

  Set<String> getNode_tags();

  Set<String> getNode_assets();

  Set<String> getNode_asset_groups();

  // Set used here to avoid duplication because a concatenation of 3 tables is done in the request
  // AttackChainNodeRepository.findForIndexing()
  Set<String> getNode_teams();

  String getNode_status_name();

  String getNode_attackChain();

  String getNode_AttackChainRun();
}
