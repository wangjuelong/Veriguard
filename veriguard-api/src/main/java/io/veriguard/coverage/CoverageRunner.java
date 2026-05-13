package io.veriguard.coverage;

import io.veriguard.coverage.soc.SocAdapter;
import io.veriguard.coverage.soc.SocAdapterRouter;
import io.veriguard.coverage.soc.SocAlert;
import io.veriguard.coverage.soc.SocAlertQuery;
import io.veriguard.coverage.soc.SocQueryTimeoutException;
import io.veriguard.database.model.Asset;
import io.veriguard.database.model.AssetGroup;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.coverage.CoverageBaseline;
import io.veriguard.database.model.coverage.CoverageHitState;
import io.veriguard.database.model.coverage.CoverageResult;
import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.model.coverage.CoverageRunStatus;
import io.veriguard.database.model.coverage.Policy;
import io.veriguard.database.model.coverage.PolicyDeviceType;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.database.repository.CoverageBaselineRepository;
import io.veriguard.database.repository.CoverageResultRepository;
import io.veriguard.database.repository.CoverageRunRepository;
import io.veriguard.database.repository.PolicyRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 覆盖度评估执行器 —— PR C3.
 *
 * <p>流程：
 * <ol>
 *   <li>加载 baseline（含 case_ids[] + asset_group_id）</li>
 *   <li>展开 asset_group → assets 集合</li>
 *   <li>笛卡尔积 (assets × policies) 派发查询（policies 当前为全表，未来可按 baseline 过滤）</li>
 *   <li>对每个单元格调 {@link SocAdapter#queryAlerts}（注：本 PR 不真实派发 inject —— 直接构造
 *       mock {@link SocAlertQuery} 让 stub 返回随机告警，端到端能跑通；真实 inject 派发留给 PR A1）</li>
 *   <li>4 态映射 → batch save coverage_results</li>
 *   <li>更新 run 计数 + 标 completed</li>
 * </ol>
 *
 * <p>4 态映射：
 * <ul>
 *   <li>{@link CoverageHitState#hit}          SOC 返回有匹配告警 → 记录 alert_rule_id</li>
 *   <li>{@link CoverageHitState#miss}         SOC 返回空 → "无覆盖"（招标关注重点）</li>
 *   <li>{@link CoverageHitState#timeout}      SOC 查询抛 {@link SocQueryTimeoutException}</li>
 *   <li>{@link CoverageHitState#out_of_scope} (asset_type, policy.device_type) 不匹配（如 ICS 资产 vs WAF 策略）</li>
 * </ul>
 */
@Slf4j
@Component
public class CoverageRunner {

  /** 单 cell 异常隔离：单 cell 失败标 timeout，不污染整个 run. */
  private static final int RESULT_FLUSH_BATCH = 100;

  private final CoverageBaselineRepository baselineRepository;
  private final CoverageRunRepository runRepository;
  private final CoverageResultRepository resultRepository;
  private final PolicyRepository policyRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final SocAdapterRouter socAdapterRouter;
  private final TransactionTemplate transactionTemplate;
  private final boolean honorSocDelay;

  public CoverageRunner(
      CoverageBaselineRepository baselineRepository,
      CoverageRunRepository runRepository,
      CoverageResultRepository resultRepository,
      PolicyRepository policyRepository,
      AssetGroupRepository assetGroupRepository,
      SocAdapterRouter socAdapterRouter,
      TransactionTemplate transactionTemplate,
      @Value("${veriguard.coverage.honor-soc-delay:false}") boolean honorSocDelay) {
    this.baselineRepository = baselineRepository;
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.policyRepository = policyRepository;
    this.assetGroupRepository = assetGroupRepository;
    this.socAdapterRouter = socAdapterRouter;
    this.transactionTemplate = transactionTemplate;
    // 默认关闭 sleep（测试环境必须关闭；生产运行画布按 baseline.socQueryDelaySeconds 等待）
    this.honorSocDelay = honorSocDelay;
  }

  /** 创建 run 并异步执行；返回 run id 的 future. */
  @Async
  public CompletableFuture<String> runAsync(String baselineId) {
    CoverageRun run = createRun(baselineId);
    try {
      execute(run);
    } catch (Exception e) {
      log.error("Coverage run {} failed: {}", run.getId(), e.getMessage(), e);
      transactionTemplate.executeWithoutResult(
          s -> {
            CoverageRun fresh = runRepository.findById(run.getId()).orElse(run);
            fresh.setStatus(CoverageRunStatus.failed);
            fresh.setErrorMessage(e.getMessage());
            fresh.setFinishedAt(Instant.now());
            runRepository.save(fresh);
          });
    }
    return CompletableFuture.completedFuture(run.getId());
  }

  /** 同步入口：创建 run + 执行（测试 / 手动调用）. */
  public String runSynchronously(String baselineId) {
    CoverageRun run = createRun(baselineId);
    execute(run);
    return run.getId();
  }

  /** 创建 run（pending 状态）；用于 REST 触发后异步执行。 */
  public CoverageRun createRun(String baselineId) {
    if (baselineRepository.findById(baselineId).isEmpty()) {
      throw new IllegalArgumentException("Unknown baseline id: " + baselineId);
    }
    CoverageRun run = new CoverageRun();
    run.setId(UUID.randomUUID().toString());
    run.setBaselineId(baselineId);
    run.setStatus(CoverageRunStatus.pending);
    return transactionTemplate.execute(s -> runRepository.save(run));
  }

  // ============================================================
  // 内部
  // ============================================================

  /** 同步执行已存在的 run（用于测试）；不创建新 run. */
  public void execute(CoverageRun run) {
    CoverageBaseline baseline =
        baselineRepository
            .findById(run.getBaselineId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unknown baseline id: " + run.getBaselineId()));

    transactionTemplate.executeWithoutResult(
        s -> {
          CoverageRun fresh = runRepository.findById(run.getId()).orElse(run);
          fresh.setStatus(CoverageRunStatus.running);
          fresh.setStartedAt(Instant.now());
          runRepository.save(fresh);
        });

    AssetGroup assetGroup =
        assetGroupRepository
            .findById(baseline.getAssetGroupId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unknown asset group id: " + baseline.getAssetGroupId()));

    List<Asset> assets = expandAssetGroup(assetGroup);
    List<Policy> policies = loadAllPolicies();

    if (assets.isEmpty() || policies.isEmpty()) {
      log.info(
          "Coverage run {} has empty grid (assets={} policies={}), marking completed with 0 cells",
          run.getId(),
          assets.size(),
          policies.size());
      finalizeRun(run.getId(), 0, 0, 0, 0, 0);
      return;
    }

    SocAdapter adapter = socAdapterRouter.select();
    log.info(
        "Coverage run {} dispatching grid {} assets × {} policies via {} adapter",
        run.getId(),
        assets.size(),
        policies.size(),
        adapter.name());

    if (honorSocDelay && baseline.getSocQueryDelaySeconds() > 0) {
      sleepQuietly(Duration.ofSeconds(baseline.getSocQueryDelaySeconds()));
    }

    String firstCaseId =
        baseline.getCaseIds().isEmpty() ? null : baseline.getCaseIds().get(0);

    List<CoverageResult> batch = new ArrayList<>();
    int hit = 0;
    int miss = 0;
    int timeout = 0;
    int outOfScope = 0;
    Instant from = Instant.now().minus(Duration.ofMinutes(5));
    Instant to = Instant.now().plus(Duration.ofSeconds(1));

    for (Asset asset : assets) {
      List<String> assetIps = collectAssetIps(asset);
      for (Policy policy : policies) {
        CoverageResult cell = new CoverageResult();
        cell.setRunId(run.getId());
        cell.setAssetId(asset.getId());
        cell.setPolicyId(policy.getId());
        cell.setCaseId(firstCaseId);

        if (!isApplicable(asset, policy)) {
          cell.setHitState(CoverageHitState.out_of_scope);
          outOfScope++;
        } else if (assetIps.isEmpty()) {
          // 无 IP 可查 → 视为 timeout（通信问题）
          cell.setHitState(CoverageHitState.timeout);
          cell.setErrorMessage("asset has no usable IP for SOC query");
          timeout++;
        } else {
          try {
            SocAlertQuery query =
                new SocAlertQuery(
                    assetIps,
                    from,
                    to,
                    policy.getExternalRuleId() == null
                        ? null
                        : List.of(policy.getExternalRuleId()));
            List<SocAlert> alerts = adapter.queryAlerts(query);
            if (alerts.isEmpty()) {
              cell.setHitState(CoverageHitState.miss);
              miss++;
            } else {
              SocAlert first = alerts.get(0);
              cell.setHitState(CoverageHitState.hit);
              cell.setAlertRuleId(first.ruleId());
              cell.setObservedAt(first.observedAt());
              hit++;
            }
          } catch (SocQueryTimeoutException e) {
            cell.setHitState(CoverageHitState.timeout);
            cell.setErrorMessage(e.getMessage());
            timeout++;
          } catch (RuntimeException e) {
            // 单格异常隔离：标 timeout 继续，不污染整个 run
            log.warn(
                "Coverage cell ({}, {}) failed: {}", asset.getId(), policy.getId(), e.getMessage());
            cell.setHitState(CoverageHitState.timeout);
            cell.setErrorMessage("adapter error: " + e.getMessage());
            timeout++;
          }
        }
        batch.add(cell);
        if (batch.size() >= RESULT_FLUSH_BATCH) {
          flushBatch(batch);
        }
      }
    }
    if (!batch.isEmpty()) {
      flushBatch(batch);
    }

    int total = assets.size() * policies.size();
    finalizeRun(run.getId(), total, hit, miss, timeout, outOfScope);
  }

  private void finalizeRun(String runId, int total, int hit, int miss, int timeout, int oos) {
    transactionTemplate.executeWithoutResult(
        s ->
            runRepository
                .findById(runId)
                .ifPresent(
                    r -> {
                      r.setStatus(CoverageRunStatus.completed);
                      r.setTotalCells(total);
                      r.setHitCount(hit);
                      r.setMissCount(miss);
                      r.setTimeoutCount(timeout);
                      r.setOutOfScopeCount(oos);
                      r.setFinishedAt(Instant.now());
                      runRepository.save(r);
                    }));
  }

  private void flushBatch(List<CoverageResult> batch) {
    List<CoverageResult> snapshot = new ArrayList<>(batch);
    batch.clear();
    transactionTemplate.executeWithoutResult(s -> resultRepository.saveAll(snapshot));
  }

  /** 展开 asset_group：含静态 assets，再加 dynamicAssets（如有），去重. */
  private List<Asset> expandAssetGroup(AssetGroup group) {
    Set<String> seen = new HashSet<>();
    List<Asset> out = new ArrayList<>();
    for (Asset a : group.getAssets()) {
      if (seen.add(a.getId())) {
        out.add(a);
      }
    }
    for (Asset a : group.getDynamicAssets()) {
      if (seen.add(a.getId())) {
        out.add(a);
      }
    }
    return out;
  }

  private List<Policy> loadAllPolicies() {
    List<Policy> out = new ArrayList<>();
    policyRepository.findAll().forEach(out::add);
    return out;
  }

  /** 资产 IP 收集 —— 当前仅 Endpoint 子类型有 IP 数组；其它类型返回空列表. */
  private List<String> collectAssetIps(Asset asset) {
    if (asset instanceof Endpoint endpoint) {
      String[] ips = endpoint.getIps();
      if (ips == null || ips.length == 0) {
        return List.of();
      }
      return Arrays.stream(ips).filter(ip -> ip != null && !ip.isBlank()).toList();
    }
    return List.of();
  }

  /**
   * (asset, policy) 适用性判定 —— 简化规则.
   *
   * <p>当前：HIDS 策略仅适用 Endpoint；其它（waf/ips/ids/nta）适用所有非 SecurityPlatform 资产.
   * 真实场景需读 asset.tags 或 policy.applicable_asset_tags（PR A1 完善）.
   */
  private boolean isApplicable(Asset asset, Policy policy) {
    if (policy.getDeviceType() == PolicyDeviceType.hids) {
      return asset instanceof Endpoint;
    }
    return true;
  }

  private void sleepQuietly(Duration d) {
    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** 仅供测试：暴露包内辅助方法. */
  Collection<Asset> debugExpandAssetGroup(AssetGroup group) {
    return expandAssetGroup(group);
  }
}
