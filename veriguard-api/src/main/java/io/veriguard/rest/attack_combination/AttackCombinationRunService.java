package io.veriguard.rest.attack_combination;

import io.veriguard.combination.BaseAttackPayloadCatalog;
import io.veriguard.combination.scheduler.CombinationScheduler;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.database.model.combination.AttackCombinationResult;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.repository.AttackCombinationResultRepository;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.BypassDimensionRepository;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationResultOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationResultPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationRunOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.AttackCombinationRunPageOutput;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// (Transactional retained for the @Transactional(readOnly = true) read methods below)

/**
 * 攻击组合任务（run）业务服务 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 */
@Service
public class AttackCombinationRunService {

  /** 单任务 asset 数上限. */
  public static final int MAX_ASSETS_PER_RUN = 100;

  /** 单任务总 result 行（combinations × assets）上限. */
  public static final int MAX_TOTAL_RESULTS = 300_000;

  /** 限流配置允许范围. */
  public static final int MIN_RATE_LIMIT = 25;

  public static final int MAX_RATE_LIMIT = 500;

  /** 并发配置允许范围. */
  public static final int MIN_CONCURRENCY = 4;

  public static final int MAX_CONCURRENCY = 64;

  /** 超时配置允许范围（小时）. */
  public static final int MIN_TIMEOUT_HOURS = 1;

  public static final int MAX_TIMEOUT_HOURS = 72;

  private final AttackCombinationRunRepository runRepository;
  private final AttackCombinationResultRepository resultRepository;
  private final BypassDimensionRepository dimensionRepository;
  private final BaseAttackPayloadCatalog payloadCatalog;
  private final CombinationScheduler scheduler;
  private final TransactionTemplate transactionTemplate;

  public AttackCombinationRunService(
      AttackCombinationRunRepository runRepository,
      AttackCombinationResultRepository resultRepository,
      BypassDimensionRepository dimensionRepository,
      BaseAttackPayloadCatalog payloadCatalog,
      CombinationScheduler scheduler,
      TransactionTemplate transactionTemplate) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.dimensionRepository = dimensionRepository;
    this.payloadCatalog = payloadCatalog;
    this.scheduler = scheduler;
    this.transactionTemplate = transactionTemplate;
  }

  /** 创建任务 + 入 pending 状态 + 默认异步启动. */
  public AttackCombinationRunOutput create(CreateRunRequest request, boolean autoStart) {
    validate(request);

    int totalCombinations = request.baseAttackTypes().size() * request.bypassDimensionIds().size();
    int totalResults = totalCombinations * request.assetIds().size();
    if (totalResults > MAX_TOTAL_RESULTS) {
      throw new IllegalArgumentException(
          "total_results "
              + totalResults
              + " exceeds limit "
              + MAX_TOTAL_RESULTS
              + " (combinations="
              + totalCombinations
              + " × assets="
              + request.assetIds().size()
              + ")");
    }

    // Persist in its own transaction so the async runner can see it after scheduler.start()
    String savedId =
        transactionTemplate.execute(
            status -> {
              int timeoutHours = request.timeoutHours() == null ? 24 : request.timeoutHours();
              AttackCombinationRun run = new AttackCombinationRun();
              run.setName(request.name());
              run.setBaseAttackTypes(new ArrayList<>(distinctOrdered(request.baseAttackTypes())));
              run.setBypassDimensionIds(
                  new ArrayList<>(distinctOrdered(request.bypassDimensionIds())));
              run.setAssetIds(new ArrayList<>(distinctOrdered(request.assetIds())));
              run.setStatus(AttackCombinationRunStatus.pending);
              run.setTotalCombinations(totalCombinations);
              run.setTotalResults(totalResults);
              run.setRateLimitPerSecond(
                  request.rateLimitPerSecond() == null ? 100 : request.rateLimitPerSecond());
              run.setConcurrency(request.concurrency() == null ? 16 : request.concurrency());
              run.setMaxRetries(request.maxRetries() == null ? 3 : request.maxRetries());
              run.setTimeoutHours(timeoutHours);
              run.setExpiresAt(Instant.now().plus(timeoutHours, ChronoUnit.HOURS));
              return runRepository.save(run).getId();
            });

    if (autoStart) {
      scheduler.start(savedId);
    }
    AttackCombinationRun saved =
        runRepository
            .findById(savedId)
            .orElseThrow(() -> new IllegalStateException("Just-saved run missing: " + savedId));
    return toOutput(saved);
  }

  @Transactional(readOnly = true)
  public AttackCombinationRunPageOutput list(
      Optional<AttackCombinationRunStatus> status, int page, int size) {
    validatePaging(page, size);
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<AttackCombinationRun> result =
        status
            .map(s -> runRepository.findAllByStatus(s, pageable))
            .orElseGet(() -> runRepository.findAll(pageable));
    List<AttackCombinationRunOutput> content =
        result.getContent().stream().map(this::toOutput).toList();
    return new AttackCombinationRunPageOutput(
        content,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AttackCombinationRunOutput detail(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    return toOutput(run);
  }

  public boolean pause(String runId) {
    return scheduler.pause(runId);
  }

  public boolean resume(String runId) {
    return scheduler.resume(runId);
  }

  public boolean cancel(String runId) {
    return scheduler.cancel(runId);
  }

  @Transactional(readOnly = true)
  public AttackCombinationResultPageOutput listResults(
      String runId, Optional<AttackCombinationHitState> hitState, int page, int size) {
    validatePaging(page, size);
    if (!runRepository.existsById(runId)) {
      throw new IllegalArgumentException("Unknown run id: " + runId);
    }
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    Page<AttackCombinationResult> result =
        hitState
            .map(h -> resultRepository.findAllByRunIdAndHitState(runId, h, pageable))
            .orElseGet(() -> resultRepository.findAllByRunId(runId, pageable));
    List<AttackCombinationResultOutput> content =
        result.getContent().stream().map(AttackCombinationRunService::toResultOutput).toList();
    return new AttackCombinationResultPageOutput(
        content,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  // ============================================================
  // 内部
  // ============================================================

  private void validate(CreateRunRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (request.baseAttackTypes() == null || request.baseAttackTypes().isEmpty()) {
      throw new IllegalArgumentException("base_attack_types must not be empty");
    }
    for (String t : request.baseAttackTypes()) {
      if (!payloadCatalog.isKnown(t)) {
        throw new IllegalArgumentException("Unknown base_attack_type: " + t);
      }
    }
    if (request.bypassDimensionIds() == null || request.bypassDimensionIds().isEmpty()) {
      throw new IllegalArgumentException("bypass_dimension_ids must not be empty");
    }
    Set<String> distinctDimIds = new HashSet<>(request.bypassDimensionIds());
    Set<String> foundIds = new HashSet<>();
    dimensionRepository.findAllById(distinctDimIds).forEach(d -> foundIds.add(d.getId()));
    for (String id : distinctDimIds) {
      if (!foundIds.contains(id)) {
        throw new IllegalArgumentException("Unknown bypass_dimension_id: " + id);
      }
    }
    if (request.assetIds() == null || request.assetIds().isEmpty()) {
      throw new IllegalArgumentException("asset_ids must not be empty");
    }
    if (request.assetIds().size() > MAX_ASSETS_PER_RUN) {
      throw new IllegalArgumentException(
          "asset_ids size "
              + request.assetIds().size()
              + " exceeds MAX_ASSETS_PER_RUN="
              + MAX_ASSETS_PER_RUN);
    }
    if (request.rateLimitPerSecond() != null) {
      int r = request.rateLimitPerSecond();
      if (r < MIN_RATE_LIMIT || r > MAX_RATE_LIMIT) {
        throw new IllegalArgumentException(
            "rate_limit_per_second must be between "
                + MIN_RATE_LIMIT
                + " and "
                + MAX_RATE_LIMIT);
      }
    }
    if (request.concurrency() != null) {
      int c = request.concurrency();
      if (c < MIN_CONCURRENCY || c > MAX_CONCURRENCY) {
        throw new IllegalArgumentException(
            "concurrency must be between " + MIN_CONCURRENCY + " and " + MAX_CONCURRENCY);
      }
    }
    if (request.maxRetries() != null) {
      int m = request.maxRetries();
      if (m < 0 || m > 5) {
        throw new IllegalArgumentException("max_retries must be between 0 and 5");
      }
    }
    if (request.timeoutHours() != null) {
      int h = request.timeoutHours();
      if (h < MIN_TIMEOUT_HOURS || h > MAX_TIMEOUT_HOURS) {
        throw new IllegalArgumentException(
            "timeout_hours must be between "
                + MIN_TIMEOUT_HOURS
                + " and "
                + MAX_TIMEOUT_HOURS);
      }
    }
  }

  private static void validatePaging(int page, int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0, got " + page);
    }
    if (size < 1 || size > 500) {
      throw new IllegalArgumentException("size must be between 1 and 500, got " + size);
    }
  }

  private static <T> List<T> distinctOrdered(List<T> items) {
    return items.stream().distinct().toList();
  }

  private AttackCombinationRunOutput toOutput(AttackCombinationRun run) {
    double progress =
        run.getTotalResults() == 0
            ? 0.0
            : Math.min(1.0, (double) run.getCompletedCount() / (double) run.getTotalResults());
    Map<String, Long> counts = loadHitStateCounts(run.getId());
    return new AttackCombinationRunOutput(
        run.getId(),
        run.getName(),
        run.getBaseAttackTypes(),
        run.getBypassDimensionIds(),
        run.getAssetIds(),
        run.getStatus() == null ? null : run.getStatus().name(),
        run.getTotalCombinations(),
        run.getTotalResults(),
        run.getCompletedCount(),
        run.getFailedCount(),
        run.getRateLimitPerSecond(),
        run.getConcurrency(),
        run.getMaxRetries(),
        run.getTimeoutHours(),
        progress,
        counts,
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getExpiresAt(),
        run.getCreatedAt(),
        run.getUpdatedAt());
  }

  private Map<String, Long> loadHitStateCounts(String runId) {
    Map<AttackCombinationHitState, Long> counts = new EnumMap<>(AttackCombinationHitState.class);
    for (AttackCombinationHitState s : AttackCombinationHitState.values()) {
      counts.put(s, 0L);
    }
    for (Object[] row : resultRepository.countByRunIdGroupedByHitState(runId)) {
      String stateName = (String) row[0];
      Long count = ((Number) row[1]).longValue();
      try {
        counts.put(AttackCombinationHitState.valueOf(stateName), count);
      } catch (IllegalArgumentException e) {
        // Skip unknown state strings (defensive)
      }
    }
    Map<String, Long> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<AttackCombinationHitState, Long> e : counts.entrySet()) {
      out.put(e.getKey().name(), e.getValue());
    }
    return out;
  }

  private static AttackCombinationResultOutput toResultOutput(AttackCombinationResult r) {
    return new AttackCombinationResultOutput(
        r.getId(),
        r.getRunId(),
        r.getCombinationId(),
        r.getBaseAttackType(),
        r.getBypassDimensionId(),
        r.getAssetId(),
        r.getHitState() == null ? null : r.getHitState().name(),
        r.getRetryCount(),
        r.getPayloadSample(),
        r.getErrorMessage(),
        r.getExecutedAt(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }

  /** 创建请求体（PR D2 内部使用，REST 层在 AttackCombinationApi 内 record 转换）. */
  public record CreateRunRequest(
      String name,
      List<String> baseAttackTypes,
      List<String> bypassDimensionIds,
      List<String> assetIds,
      Integer rateLimitPerSecond,
      Integer concurrency,
      Integer maxRetries,
      Integer timeoutHours) {}
}
