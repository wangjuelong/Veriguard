package io.veriguard.rest.attack_combination;

import io.veriguard.combination.severity.SeverityClassifier;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.SeverityConfig;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.SeverityConfigRepository;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.SeverityConfigOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.SeverityRecomputeOutput;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分级配置 + 手动重算服务 —— IPv6 安全验证系统 §3.6 ★2 PR D4.
 *
 * <p>Singleton 配置：GET 无行返回默认值（in-memory，不落库）；PUT 强校验后落 fixed id 行.
 */
@Service
public class SeverityConfigService {

  /** 权重总和容差（与 SeverityClassifier 保持一致） */
  private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.001");

  private final SeverityConfigRepository configRepository;
  private final AttackCombinationRunRepository runRepository;
  private final SeverityClassifier severityClassifier;

  public SeverityConfigService(
      SeverityConfigRepository configRepository,
      AttackCombinationRunRepository runRepository,
      SeverityClassifier severityClassifier) {
    this.configRepository = configRepository;
    this.runRepository = runRepository;
    this.severityClassifier = severityClassifier;
  }

  @Transactional(readOnly = true)
  public SeverityConfigOutput get() {
    SeverityConfig config = configRepository.findSingleton().orElseGet(SeverityConfig::new);
    return toOutput(config);
  }

  /** 更新（upsert 到固定 singleton id 行）. */
  @Transactional
  public SeverityConfigOutput update(UpdateRequest body) {
    validate(body);

    SeverityConfig config =
        configRepository
            .findSingleton()
            .orElseGet(
                () -> {
                  SeverityConfig fresh = new SeverityConfig();
                  fresh.setId(SeverityConfig.SINGLETON_ID);
                  return fresh;
                });

    config.setMissCountWeight(body.missCountWeight());
    config.setAttackTypeWeight(body.attackTypeWeight());
    config.setAssetSensitivityWeight(body.assetSensitivityWeight());
    config.setCriticalThreshold(body.criticalThreshold());
    config.setHighThreshold(body.highThreshold());
    config.setMediumThreshold(body.mediumThreshold());
    config.setCriticalLabel(body.criticalLabel());
    config.setHighLabel(body.highLabel());
    config.setMediumLabel(body.mediumLabel());
    config.setInfoLabel(body.infoLabel());
    config.setCriticalColor(body.criticalColor());
    config.setHighColor(body.highColor());
    config.setMediumColor(body.mediumColor());
    config.setInfoColor(body.infoColor());

    SeverityConfig saved = configRepository.save(config);
    return toOutput(saved);
  }

  /**
   * 手动触发某 run 的 cluster 分级重算（仅 completed run）。
   *
   * @throws IllegalArgumentException run 不存在
   * @throws IllegalStateException    run 不在 completed 状态
   */
  public SeverityRecomputeOutput recompute(String runId) {
    AttackCombinationRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown run id: " + runId));
    if (run.getStatus() != AttackCombinationRunStatus.completed) {
      throw new IllegalStateException(
          "Severity recompute requires completed run, got status: " + run.getStatus());
    }
    String jobId = UUID.randomUUID().toString();
    severityClassifier.classifyAsync(runId, true);
    return new SeverityRecomputeOutput(runId, "accepted", jobId);
  }

  // ============================================================
  // helpers
  // ============================================================

  private static void validate(UpdateRequest req) {
    requireNotNull(req.missCountWeight(), "miss_count_weight");
    requireNotNull(req.attackTypeWeight(), "attack_type_weight");
    requireNotNull(req.assetSensitivityWeight(), "asset_sensitivity_weight");
    requireNotNull(req.criticalThreshold(), "critical_threshold");
    requireNotNull(req.highThreshold(), "high_threshold");
    requireNotNull(req.mediumThreshold(), "medium_threshold");

    requireNotBlank(req.criticalLabel(), "critical_label");
    requireNotBlank(req.highLabel(), "high_label");
    requireNotBlank(req.mediumLabel(), "medium_label");
    requireNotBlank(req.infoLabel(), "info_label");
    requireNotBlank(req.criticalColor(), "critical_color");
    requireNotBlank(req.highColor(), "high_color");
    requireNotBlank(req.mediumColor(), "medium_color");
    requireNotBlank(req.infoColor(), "info_color");

    // 权重和必须 = 1.0
    BigDecimal sum =
        req.missCountWeight().add(req.attackTypeWeight()).add(req.assetSensitivityWeight());
    if (sum.subtract(BigDecimal.ONE).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
      throw new IllegalArgumentException(
          "weights must sum to 1.0 (±" + WEIGHT_TOLERANCE + "), got: " + sum);
    }

    // 每项权重必须 ∈ [0, 1]
    requireWithin01(req.missCountWeight(), "miss_count_weight");
    requireWithin01(req.attackTypeWeight(), "attack_type_weight");
    requireWithin01(req.assetSensitivityWeight(), "asset_sensitivity_weight");

    // 阈值必须满足 critical > high > medium ≥ 0 且 ≤ 100
    requireRange0to100(req.criticalThreshold(), "critical_threshold");
    requireRange0to100(req.highThreshold(), "high_threshold");
    requireRange0to100(req.mediumThreshold(), "medium_threshold");
    if (req.criticalThreshold().compareTo(req.highThreshold()) <= 0
        || req.highThreshold().compareTo(req.mediumThreshold()) <= 0) {
      throw new IllegalArgumentException(
          "thresholds must satisfy critical > high > medium, got: "
              + req.criticalThreshold()
              + " / "
              + req.highThreshold()
              + " / "
              + req.mediumThreshold());
    }
  }

  private static void requireNotNull(Object v, String field) {
    if (v == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
  }

  private static void requireNotBlank(String v, String field) {
    if (v == null || v.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }

  private static void requireWithin01(BigDecimal v, String field) {
    if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException(field + " must be within [0, 1], got: " + v);
    }
  }

  private static void requireRange0to100(BigDecimal v, String field) {
    if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException(field + " must be within [0, 100], got: " + v);
    }
  }

  static SeverityConfigOutput toOutput(SeverityConfig c) {
    return new SeverityConfigOutput(
        c.getId(),
        c.getMissCountWeight(),
        c.getAttackTypeWeight(),
        c.getAssetSensitivityWeight(),
        c.getCriticalThreshold(),
        c.getHighThreshold(),
        c.getMediumThreshold(),
        c.getCriticalLabel(),
        c.getHighLabel(),
        c.getMediumLabel(),
        c.getInfoLabel(),
        c.getCriticalColor(),
        c.getHighColor(),
        c.getMediumColor(),
        c.getInfoColor(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }

  /** PUT 请求体 —— snake_case wire. */
  public record UpdateRequest(
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_miss_count_weight")
          BigDecimal missCountWeight,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_attack_type_weight")
          BigDecimal attackTypeWeight,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_asset_sensitivity_weight")
          BigDecimal assetSensitivityWeight,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_critical_threshold")
          BigDecimal criticalThreshold,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_high_threshold")
          BigDecimal highThreshold,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_medium_threshold")
          BigDecimal mediumThreshold,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_critical_label")
          String criticalLabel,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_high_label")
          String highLabel,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_medium_label")
          String mediumLabel,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_info_label")
          String infoLabel,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_critical_color")
          String criticalColor,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_high_color")
          String highColor,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_medium_color")
          String mediumColor,
      @com.fasterxml.jackson.annotation.JsonProperty("severity_config_info_color")
          String infoColor) {}
}
