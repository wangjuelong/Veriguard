package io.veriguard.database.model;

import java.time.Instant;
import java.util.Optional;

public interface Injection {
  String getId();

  AttackChainRun getAttackChainRun();

  AttackChain getAttackChain();

  Optional<Instant> getDate();

  AttackChainNode getAttackChainNode();
}
