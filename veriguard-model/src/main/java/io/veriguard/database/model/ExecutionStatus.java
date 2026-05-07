package io.veriguard.database.model;

public enum ExecutionStatus {
  // AttackChainNode Status
  SUCCESS,
  PARTIAL,
  ERROR,

  // -- Deprecated (kept for backward compatibility with existing DB data) --
  @Deprecated
  MAYBE_PREVENTED,
  @Deprecated
  MAYBE_PARTIAL_PREVENTED,

  // AttackChainNode Execution Progress
  DRAFT,
  QUEUING,
  EXECUTING,
  PENDING,
}
