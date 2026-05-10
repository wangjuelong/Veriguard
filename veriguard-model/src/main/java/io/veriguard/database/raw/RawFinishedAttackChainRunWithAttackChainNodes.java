package io.veriguard.database.raw;

import java.time.Instant;
import java.util.Set;

public interface RawFinishedAttackChainRunWithAttackChainNodes {
  Instant getAttack_chain_run_end_date();

  Set<String> getNode_ids();
}
