package io.veriguard.rest.attack_combination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.combination.severity.SeverityClassifier;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.SeverityConfig;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.SeverityConfigRepository;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.SeverityConfigOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.SeverityRecomputeOutput;
import io.veriguard.rest.attack_combination.SeverityConfigService.UpdateRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 单测 —— PR D4 SeverityConfigService GET/PUT 校验 + recompute 状态检查. */
@ExtendWith(MockitoExtension.class)
class SeverityConfigServiceTest {

  @Mock SeverityConfigRepository configRepository;
  @Mock AttackCombinationRunRepository runRepository;
  @Mock SeverityClassifier severityClassifier;

  private SeverityConfigService service;

  @BeforeEach
  void setUp() {
    service = new SeverityConfigService(configRepository, runRepository, severityClassifier);
  }

  @Test
  void get_returns_defaults_when_no_row_persisted() {
    when(configRepository.findSingleton()).thenReturn(Optional.empty());

    SeverityConfigOutput output = service.get();

    // 默认权重 0.5/0.3/0.2 + 默认阈值 70/40/10 + 默认标签
    assertThat(output.missCountWeight()).isEqualByComparingTo("0.500");
    assertThat(output.attackTypeWeight()).isEqualByComparingTo("0.300");
    assertThat(output.assetSensitivityWeight()).isEqualByComparingTo("0.200");
    assertThat(output.criticalThreshold()).isEqualByComparingTo("70.00");
    assertThat(output.criticalLabel()).isEqualTo("高");
    assertThat(output.infoLabel()).isEqualTo("信息");
  }

  @Test
  void update_with_valid_weights_persists() {
    when(configRepository.findSingleton()).thenReturn(Optional.empty());
    when(configRepository.save(any(SeverityConfig.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    UpdateRequest req = sampleRequest();
    SeverityConfigOutput out = service.update(req);

    assertThat(out.criticalLabel()).isEqualTo("Critical-custom");
    verify(configRepository, times(1)).save(any(SeverityConfig.class));
  }

  @Test
  void update_with_weights_not_summing_to_1_throws_400() {
    UpdateRequest req =
        new UpdateRequest(
            new BigDecimal("0.500"),
            new BigDecimal("0.500"),
            new BigDecimal("0.500"), // sum=1.5
            new BigDecimal("70.00"),
            new BigDecimal("40.00"),
            new BigDecimal("10.00"),
            "C",
            "H",
            "M",
            "I",
            "#000",
            "#111",
            "#222",
            "#333");

    assertThatThrownBy(() -> service.update(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("weights must sum to 1.0");
  }

  @Test
  void update_with_thresholds_not_strictly_decreasing_throws() {
    UpdateRequest req =
        new UpdateRequest(
            new BigDecimal("0.500"),
            new BigDecimal("0.300"),
            new BigDecimal("0.200"),
            new BigDecimal("50.00"), // critical
            new BigDecimal("60.00"), // high > critical → invalid
            new BigDecimal("10.00"),
            "C",
            "H",
            "M",
            "I",
            "#000",
            "#111",
            "#222",
            "#333");

    assertThatThrownBy(() -> service.update(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("critical > high > medium");
  }

  @Test
  void recompute_when_run_completed_dispatches_async() {
    String runId = "run-1";
    AttackCombinationRun run = new AttackCombinationRun();
    run.setId(runId);
    run.setStatus(AttackCombinationRunStatus.completed);
    when(runRepository.findById(runId)).thenReturn(Optional.of(run));

    SeverityRecomputeOutput out = service.recompute(runId);

    assertThat(out.status()).isEqualTo("accepted");
    assertThat(out.runId()).isEqualTo(runId);
    verify(severityClassifier, times(1)).classifyAsync(runId, true);
  }

  @Test
  void recompute_when_run_not_completed_throws_409() {
    String runId = "run-1";
    AttackCombinationRun run = new AttackCombinationRun();
    run.setId(runId);
    run.setStatus(AttackCombinationRunStatus.running);
    when(runRepository.findById(runId)).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.recompute(runId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("requires completed run");
  }

  @Test
  void recompute_when_run_missing_throws_404() {
    when(runRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.recompute("missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown run id");
  }

  // ============================================================
  // helpers
  // ============================================================

  private static UpdateRequest sampleRequest() {
    return new UpdateRequest(
        new BigDecimal("0.500"),
        new BigDecimal("0.300"),
        new BigDecimal("0.200"),
        new BigDecimal("75.00"),
        new BigDecimal("45.00"),
        new BigDecimal("15.00"),
        "Critical-custom",
        "High-custom",
        "Medium-custom",
        "Info-custom",
        "#ff0000",
        "#ffaa00",
        "#ffff00",
        "#0000ff");
  }
}
