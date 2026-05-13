package io.veriguard.coverage;

import io.veriguard.database.model.coverage.CoverageHitState;
import io.veriguard.database.model.coverage.CoverageResult;
import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.repository.CoverageResultRepository;
import io.veriguard.database.repository.CoverageRunRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 覆盖度对比计算器 —— PR C3.
 *
 * <p>输入：两个 run（同一 baseline 推荐，但允许跨 baseline 比较）.
 * 输出：每个 (asset, policy) 单元格的状态变化.
 *
 * <p>变化类型 {@link ChangeType}：
 * <ul>
 *   <li>{@link ChangeType#unchanged}    两侧 hit_state 相同（含都缺失）</li>
 *   <li>{@link ChangeType#new_hit}      旧无 / 旧 miss → 新 hit （改善）</li>
 *   <li>{@link ChangeType#new_miss}     旧无 / 旧 hit → 新 miss （恶化）</li>
 *   <li>{@link ChangeType#dropped_hit}  旧 hit → 新无 / 新非 hit 非 miss （疑似遗漏）</li>
 *   <li>{@link ChangeType#dropped_miss} 旧 miss → 新无 （格被移除）</li>
 *   <li>{@link ChangeType#state_changed} 其它状态切换（如 timeout ↔ out_of_scope）</li>
 * </ul>
 */
@Component
public class CoverageDiffCalculator {

  private final CoverageRunRepository runRepository;
  private final CoverageResultRepository resultRepository;

  public CoverageDiffCalculator(
      CoverageRunRepository runRepository, CoverageResultRepository resultRepository) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
  }

  public CoverageDiffReport compare(String runIdA, String runIdB) {
    CoverageRun runA =
        runRepository
            .findById(runIdA)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runIdA));
    CoverageRun runB =
        runRepository
            .findById(runIdB)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runIdB));

    Map<CellKey, CoverageHitState> oldStates = indexByKey(resultRepository.findAllByRunId(runIdA));
    Map<CellKey, CoverageHitState> newStates = indexByKey(resultRepository.findAllByRunId(runIdB));

    Set<CellKey> allKeys = new HashSet<>();
    allKeys.addAll(oldStates.keySet());
    allKeys.addAll(newStates.keySet());

    List<CellDelta> deltas = new ArrayList<>(allKeys.size());
    int unchanged = 0;
    int newHit = 0;
    int newMiss = 0;
    int droppedHit = 0;
    int droppedMiss = 0;
    int stateChanged = 0;

    for (CellKey key : allKeys) {
      CoverageHitState oldS = oldStates.get(key);
      CoverageHitState newS = newStates.get(key);
      ChangeType ct = classify(oldS, newS);
      deltas.add(new CellDelta(key.assetId(), key.policyId(), oldS, newS, ct));
      switch (ct) {
        case unchanged -> unchanged++;
        case new_hit -> newHit++;
        case new_miss -> newMiss++;
        case dropped_hit -> droppedHit++;
        case dropped_miss -> droppedMiss++;
        case state_changed -> stateChanged++;
      }
    }

    return new CoverageDiffReport(
        runA.getId(),
        runB.getId(),
        deltas,
        new DiffSummary(unchanged, newHit, newMiss, droppedHit, droppedMiss, stateChanged));
  }

  private static ChangeType classify(CoverageHitState oldS, CoverageHitState newS) {
    if (oldS == newS) {
      return ChangeType.unchanged;
    }
    // new cell didn't exist before
    if (oldS == null) {
      if (newS == CoverageHitState.hit) {
        return ChangeType.new_hit;
      }
      if (newS == CoverageHitState.miss) {
        return ChangeType.new_miss;
      }
      return ChangeType.state_changed;
    }
    // cell removed
    if (newS == null) {
      if (oldS == CoverageHitState.hit) {
        return ChangeType.dropped_hit;
      }
      if (oldS == CoverageHitState.miss) {
        return ChangeType.dropped_miss;
      }
      return ChangeType.state_changed;
    }
    // both present, different states
    if (newS == CoverageHitState.hit && oldS != CoverageHitState.hit) {
      return ChangeType.new_hit;
    }
    if (newS == CoverageHitState.miss && oldS == CoverageHitState.hit) {
      return ChangeType.new_miss;
    }
    if (newS != CoverageHitState.hit && oldS == CoverageHitState.hit) {
      return ChangeType.dropped_hit;
    }
    return ChangeType.state_changed;
  }

  private static Map<CellKey, CoverageHitState> indexByKey(List<CoverageResult> results) {
    Map<CellKey, CoverageHitState> out = new HashMap<>(results.size());
    for (CoverageResult r : results) {
      out.put(new CellKey(r.getAssetId(), r.getPolicyId()), r.getHitState());
    }
    return out;
  }

  // ============================================================
  // 公共 DTO
  // ============================================================

  public enum ChangeType {
    unchanged,
    new_hit,
    new_miss,
    dropped_hit,
    dropped_miss,
    state_changed
  }

  public record CellKey(String assetId, String policyId) {}

  public record CellDelta(
      String assetId,
      String policyId,
      CoverageHitState oldState,
      CoverageHitState newState,
      ChangeType changeType) {}

  public record DiffSummary(
      int unchanged,
      int newHit,
      int newMiss,
      int droppedHit,
      int droppedMiss,
      int stateChanged) {}

  public record CoverageDiffReport(
      String runIdA, String runIdB, List<CellDelta> deltas, DiffSummary summary) {}
}
