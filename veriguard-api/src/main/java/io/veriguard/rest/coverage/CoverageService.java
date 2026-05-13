package io.veriguard.rest.coverage;

import io.veriguard.coverage.CoverageDiffCalculator;
import io.veriguard.coverage.CoverageDiffCalculator.CellDelta;
import io.veriguard.coverage.CoverageDiffCalculator.CoverageDiffReport;
import io.veriguard.coverage.CoverageRunner;
import io.veriguard.database.model.coverage.CoverageBaseline;
import io.veriguard.database.model.coverage.CoverageHitState;
import io.veriguard.database.model.coverage.CoverageResult;
import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.model.coverage.CoverageRunStatus;
import io.veriguard.database.model.coverage.CoverageType;
import io.veriguard.database.model.coverage.Policy;
import io.veriguard.database.model.coverage.PolicyDeviceType;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.database.repository.CoverageBaselineRepository;
import io.veriguard.database.repository.CoverageResultRepository;
import io.veriguard.database.repository.CoverageRunRepository;
import io.veriguard.database.repository.PolicyRepository;
import io.veriguard.rest.coverage.CoverageDtos.CoverageBaselineOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageBaselinePageOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageCellDeltaOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageCellOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageDiffOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageDiffSummaryOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageMatrixPageOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageRunCreateOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageRunOutput;
import io.veriguard.rest.coverage.CoverageDtos.CoverageRunPageOutput;
import io.veriguard.rest.coverage.CoverageDtos.PolicyOutput;
import io.veriguard.rest.coverage.CoverageDtos.PolicyPageOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 覆盖度子模块业务服务 —— PR C3.
 *
 * <p>聚合 baseline / run / matrix / diff / policy 五类操作；调度执行委托给 {@link CoverageRunner}.
 */
@Service
public class CoverageService {

  /** Baseline case_ids 数上限（防爆炸）. */
  public static final int MAX_CASE_IDS = 500;

  /** Baseline soc_query_delay 允许范围（秒）. */
  public static final int MIN_SOC_DELAY = 0;

  public static final int MAX_SOC_DELAY = 600;

  private final CoverageBaselineRepository baselineRepository;
  private final CoverageRunRepository runRepository;
  private final CoverageResultRepository resultRepository;
  private final PolicyRepository policyRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final CoverageRunner runner;
  private final CoverageDiffCalculator diffCalculator;

  public CoverageService(
      CoverageBaselineRepository baselineRepository,
      CoverageRunRepository runRepository,
      CoverageResultRepository resultRepository,
      PolicyRepository policyRepository,
      AssetGroupRepository assetGroupRepository,
      CoverageRunner runner,
      CoverageDiffCalculator diffCalculator) {
    this.baselineRepository = baselineRepository;
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.policyRepository = policyRepository;
    this.assetGroupRepository = assetGroupRepository;
    this.runner = runner;
    this.diffCalculator = diffCalculator;
  }

  // ============================================================
  // Baseline CRUD
  // ============================================================

  @Transactional
  public CoverageBaselineOutput createBaseline(BaselineRequest req) {
    validateBaseline(req);
    CoverageBaseline b = new CoverageBaseline();
    b.setName(req.name());
    b.setCoverageType(parseCoverageType(req.coverageType()));
    b.setCaseIds(req.caseIds() == null ? List.of() : List.copyOf(req.caseIds()));
    b.setAssetGroupId(req.assetGroupId());
    b.setDescription(req.description());
    if (req.socQueryDelaySeconds() != null) {
      b.setSocQueryDelaySeconds(req.socQueryDelaySeconds());
    }
    return toBaselineOutput(baselineRepository.save(b));
  }

  @Transactional(readOnly = true)
  public CoverageBaselinePageOutput listBaselines(
      Optional<CoverageType> coverageType, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<CoverageBaseline> p =
        coverageType
            .map(t -> baselineRepository.findAllByCoverageType(t, pageable))
            .orElseGet(() -> baselineRepository.findAll(pageable));
    return new CoverageBaselinePageOutput(
        p.getContent().stream().map(this::toBaselineOutput).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public CoverageBaselineOutput getBaseline(String id) {
    return toBaselineOutput(loadBaseline(id));
  }

  @Transactional
  public CoverageBaselineOutput updateBaseline(String id, BaselineRequest req) {
    validateBaseline(req);
    CoverageBaseline b = loadBaseline(id);
    b.setName(req.name());
    b.setCoverageType(parseCoverageType(req.coverageType()));
    b.setCaseIds(req.caseIds() == null ? List.of() : List.copyOf(req.caseIds()));
    b.setAssetGroupId(req.assetGroupId());
    b.setDescription(req.description());
    if (req.socQueryDelaySeconds() != null) {
      b.setSocQueryDelaySeconds(req.socQueryDelaySeconds());
    }
    return toBaselineOutput(baselineRepository.save(b));
  }

  @Transactional
  public void deleteBaseline(String id) {
    CoverageBaseline b = loadBaseline(id);
    baselineRepository.delete(b);
  }

  // ============================================================
  // Run trigger / list / detail / matrix
  // ============================================================

  @Transactional
  public CoverageRunCreateOutput triggerRun(String baselineId) {
    loadBaseline(baselineId); // validate exists
    // 异步：先创建 pending run，让 runner 异步推进；返回 run_id 以便前端轮询
    CoverageRun run = runner.createRun(baselineId);
    runner.runAsync(baselineId); // fire-and-forget；runner 内部使用新 run 实例.
    return new CoverageRunCreateOutput(run.getId(), run.getStatus().name());
  }

  @Transactional(readOnly = true)
  public CoverageRunPageOutput listRuns(
      Optional<String> baselineId, Optional<CoverageRunStatus> status, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<CoverageRun> p;
    if (baselineId.isPresent() && status.isPresent()) {
      p = runRepository.findAllByBaselineIdAndStatus(baselineId.get(), status.get(), pageable);
    } else if (baselineId.isPresent()) {
      p = runRepository.findAllByBaselineId(baselineId.get(), pageable);
    } else if (status.isPresent()) {
      p = runRepository.findAllByStatus(status.get(), pageable);
    } else {
      p = runRepository.findAll(pageable);
    }
    return new CoverageRunPageOutput(
        p.getContent().stream().map(this::toRunOutput).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public CoverageRunOutput getRun(String id) {
    return toRunOutput(loadRun(id));
  }

  @Transactional(readOnly = true)
  public CoverageMatrixPageOutput listMatrix(
      String runId, Optional<CoverageHitState> hitState, int page, int size) {
    loadRun(runId);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "assetId"));
    Page<CoverageResult> p =
        hitState
            .map(h -> resultRepository.findAllByRunIdAndHitState(runId, h, pageable))
            .orElseGet(() -> resultRepository.findAllByRunId(runId, pageable));
    return new CoverageMatrixPageOutput(
        p.getContent().stream().map(this::toCellOutput).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  @Transactional(readOnly = true)
  public CoverageDiffOutput diffRuns(String runIdA, String runIdB) {
    CoverageDiffReport report = diffCalculator.compare(runIdA, runIdB);
    List<CoverageCellDeltaOutput> deltas = new ArrayList<>(report.deltas().size());
    for (CellDelta d : report.deltas()) {
      deltas.add(
          new CoverageCellDeltaOutput(
              d.assetId(),
              d.policyId(),
              d.oldState() == null ? null : d.oldState().name(),
              d.newState() == null ? null : d.newState().name(),
              d.changeType().name()));
    }
    return new CoverageDiffOutput(
        report.runIdA(),
        report.runIdB(),
        deltas,
        new CoverageDiffSummaryOutput(
            report.summary().unchanged(),
            report.summary().newHit(),
            report.summary().newMiss(),
            report.summary().droppedHit(),
            report.summary().droppedMiss(),
            report.summary().stateChanged()));
  }

  // ============================================================
  // Policy CRUD (subset)
  // ============================================================

  @Transactional
  public PolicyOutput createPolicy(PolicyRequest req) {
    if (req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("policy_name must not be blank");
    }
    if (req.deviceType() == null) {
      throw new IllegalArgumentException("policy_device_type must not be null");
    }
    Policy p = new Policy();
    p.setName(req.name());
    p.setDeviceType(parsePolicyDeviceType(req.deviceType()));
    p.setDeviceId(req.deviceId());
    p.setExternalRuleId(req.externalRuleId());
    p.setDescription(req.description());
    return toPolicyOutput(policyRepository.save(p));
  }

  @Transactional(readOnly = true)
  public PolicyPageOutput listPolicies(
      Optional<PolicyDeviceType> deviceType, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
    Page<Policy> p =
        deviceType
            .map(t -> policyRepository.findAllByDeviceType(t, pageable))
            .orElseGet(() -> policyRepository.findAll(pageable));
    return new PolicyPageOutput(
        p.getContent().stream().map(this::toPolicyOutput).toList(),
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages());
  }

  // ============================================================
  // Helpers
  // ============================================================

  private CoverageBaseline loadBaseline(String id) {
    return baselineRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Unknown baseline id: " + id));
  }

  private CoverageRun loadRun(String id) {
    return runRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + id));
  }

  private void validateBaseline(BaselineRequest req) {
    if (req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("coverage_baseline_name must not be blank");
    }
    if (req.coverageType() == null) {
      throw new IllegalArgumentException("coverage_baseline_coverage_type must not be null");
    }
    if (req.assetGroupId() == null || req.assetGroupId().isBlank()) {
      throw new IllegalArgumentException("coverage_baseline_asset_group_id must not be blank");
    }
    if (assetGroupRepository.findById(req.assetGroupId()).isEmpty()) {
      throw new IllegalArgumentException("Unknown asset_group_id: " + req.assetGroupId());
    }
    if (req.caseIds() != null && req.caseIds().size() > MAX_CASE_IDS) {
      throw new IllegalArgumentException(
          "case_ids size exceeds MAX_CASE_IDS=" + MAX_CASE_IDS);
    }
    if (req.socQueryDelaySeconds() != null
        && (req.socQueryDelaySeconds() < MIN_SOC_DELAY
            || req.socQueryDelaySeconds() > MAX_SOC_DELAY)) {
      throw new IllegalArgumentException(
          "soc_query_delay_seconds must be in [" + MIN_SOC_DELAY + ", " + MAX_SOC_DELAY + "]");
    }
  }

  private static CoverageType parseCoverageType(String raw) {
    try {
      return CoverageType.valueOf(raw);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException(
          "Unknown coverage_type: " + raw + " (expected: boundary / traffic)");
    }
  }

  private static PolicyDeviceType parsePolicyDeviceType(String raw) {
    try {
      return PolicyDeviceType.valueOf(raw);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException(
          "Unknown device_type: " + raw + " (expected: waf / ips / ids / nta / hids)");
    }
  }

  private CoverageBaselineOutput toBaselineOutput(CoverageBaseline b) {
    return new CoverageBaselineOutput(
        b.getId(),
        b.getName(),
        b.getCoverageType().name(),
        b.getCaseIds(),
        b.getAssetGroupId(),
        b.getDescription(),
        b.getSocQueryDelaySeconds(),
        b.getCreatedAt(),
        b.getUpdatedAt());
  }

  private CoverageRunOutput toRunOutput(CoverageRun r) {
    int total = r.getTotalCells();
    int done = r.getHitCount() + r.getMissCount() + r.getTimeoutCount() + r.getOutOfScopeCount();
    double progress = total == 0 ? 0.0 : 100.0 * done / total;
    Map<String, Long> counts = new java.util.LinkedHashMap<>();
    counts.put(CoverageHitState.hit.name(), (long) r.getHitCount());
    counts.put(CoverageHitState.miss.name(), (long) r.getMissCount());
    counts.put(CoverageHitState.timeout.name(), (long) r.getTimeoutCount());
    counts.put(CoverageHitState.out_of_scope.name(), (long) r.getOutOfScopeCount());
    return new CoverageRunOutput(
        r.getId(),
        r.getBaselineId(),
        r.getStatus().name(),
        r.getTotalCells(),
        r.getHitCount(),
        r.getMissCount(),
        r.getTimeoutCount(),
        r.getOutOfScopeCount(),
        progress,
        counts,
        r.getStartedAt(),
        r.getFinishedAt(),
        r.getErrorMessage(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }

  private CoverageCellOutput toCellOutput(CoverageResult c) {
    return new CoverageCellOutput(
        c.getId(),
        c.getRunId(),
        c.getAssetId(),
        c.getPolicyId(),
        c.getCaseId(),
        c.getHitState().name(),
        c.getAlertRuleId(),
        c.getObservedAt(),
        c.getErrorMessage(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }

  private PolicyOutput toPolicyOutput(Policy p) {
    return new PolicyOutput(
        p.getId(),
        p.getName(),
        p.getDeviceType().name(),
        p.getDeviceId(),
        p.getExternalRuleId(),
        p.getDescription(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }

  // ============================================================
  // Request records
  // ============================================================

  public record BaselineRequest(
      String name,
      String coverageType,
      List<String> caseIds,
      String assetGroupId,
      String description,
      Integer socQueryDelaySeconds) {}

  public record PolicyRequest(
      String name,
      String deviceType,
      String deviceId,
      String externalRuleId,
      String description) {}
}
