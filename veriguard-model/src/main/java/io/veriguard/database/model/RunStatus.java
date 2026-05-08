package io.veriguard.database.model;

/**
 * AttackChainRun 运行时状态机 (PRD §2.4 / spec §2.2.5).
 *
 * <p>注意：该 enum 是 spec 新增；现有 {@link AttackChainRunStatus}（值含 SCHEDULED / RUNNING / PAUSED / CANCELED /
 * FINISHED）仍为持久化字段使用。新调度器扩展状态时引用 {@code RunStatus}（含 STOPPED_ON_BLOCK），由后续 Phase
 * 决定是否合并/迁移。
 */
public enum RunStatus {
  SCHEDULED,
  RUNNING,
  STOPPED_ON_BLOCK,
  COMPLETED,
  CANCELED
}
