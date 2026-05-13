package io.veriguard.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.veriguard.coverage.CoverageDiffCalculator.CellDelta;
import io.veriguard.coverage.CoverageDiffCalculator.ChangeType;
import io.veriguard.coverage.CoverageDiffCalculator.CoverageDiffReport;
import io.veriguard.database.model.coverage.CoverageHitState;
import io.veriguard.database.model.coverage.CoverageResult;
import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.repository.CoverageResultRepository;
import io.veriguard.database.repository.CoverageRunRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoverageDiffCalculatorTest {

  private CoverageRunRepository runRepository;
  private CoverageResultRepository resultRepository;
  private CoverageDiffCalculator calculator;

  @BeforeEach
  void setUp() {
    runRepository = mock(CoverageRunRepository.class);
    resultRepository = mock(CoverageResultRepository.class);
    calculator = new CoverageDiffCalculator(runRepository, resultRepository);

    CoverageRun runA = new CoverageRun();
    runA.setId("run-a");
    CoverageRun runB = new CoverageRun();
    runB.setId("run-b");
    when(runRepository.findById("run-a")).thenReturn(Optional.of(runA));
    when(runRepository.findById("run-b")).thenReturn(Optional.of(runB));
  }

  private CoverageResult cell(String runId, String assetId, String policyId, CoverageHitState state) {
    CoverageResult r = new CoverageResult();
    r.setRunId(runId);
    r.setAssetId(assetId);
    r.setPolicyId(policyId);
    r.setHitState(state);
    return r;
  }

  @Test
  void unchanged_when_states_match() {
    when(resultRepository.findAllByRunId("run-a"))
        .thenReturn(List.of(cell("run-a", "asset-1", "policy-1", CoverageHitState.hit)));
    when(resultRepository.findAllByRunId("run-b"))
        .thenReturn(List.of(cell("run-b", "asset-1", "policy-1", CoverageHitState.hit)));

    CoverageDiffReport report = calculator.compare("run-a", "run-b");
    assertThat(report.deltas()).hasSize(1);
    assertThat(report.deltas().get(0).changeType()).isEqualTo(ChangeType.unchanged);
    assertThat(report.summary().unchanged()).isEqualTo(1);
  }

  @Test
  void new_hit_when_old_miss_to_new_hit() {
    when(resultRepository.findAllByRunId("run-a"))
        .thenReturn(List.of(cell("run-a", "asset-1", "policy-1", CoverageHitState.miss)));
    when(resultRepository.findAllByRunId("run-b"))
        .thenReturn(List.of(cell("run-b", "asset-1", "policy-1", CoverageHitState.hit)));

    CoverageDiffReport report = calculator.compare("run-a", "run-b");
    CellDelta d = report.deltas().get(0);
    assertThat(d.changeType()).isEqualTo(ChangeType.new_hit);
    assertThat(report.summary().newHit()).isEqualTo(1);
  }

  @Test
  void new_miss_when_old_hit_to_new_miss() {
    when(resultRepository.findAllByRunId("run-a"))
        .thenReturn(List.of(cell("run-a", "asset-1", "policy-1", CoverageHitState.hit)));
    when(resultRepository.findAllByRunId("run-b"))
        .thenReturn(List.of(cell("run-b", "asset-1", "policy-1", CoverageHitState.miss)));

    CoverageDiffReport report = calculator.compare("run-a", "run-b");
    assertThat(report.deltas().get(0).changeType()).isEqualTo(ChangeType.new_miss);
    assertThat(report.summary().newMiss()).isEqualTo(1);
  }

  @Test
  void dropped_hit_when_old_hit_cell_missing_in_new() {
    when(resultRepository.findAllByRunId("run-a"))
        .thenReturn(List.of(cell("run-a", "asset-1", "policy-1", CoverageHitState.hit)));
    when(resultRepository.findAllByRunId("run-b")).thenReturn(List.of());

    CoverageDiffReport report = calculator.compare("run-a", "run-b");
    assertThat(report.deltas().get(0).changeType()).isEqualTo(ChangeType.dropped_hit);
    assertThat(report.summary().droppedHit()).isEqualTo(1);
  }

  @Test
  void dropped_miss_when_old_miss_cell_missing_in_new() {
    when(resultRepository.findAllByRunId("run-a"))
        .thenReturn(List.of(cell("run-a", "asset-1", "policy-1", CoverageHitState.miss)));
    when(resultRepository.findAllByRunId("run-b")).thenReturn(List.of());

    CoverageDiffReport report = calculator.compare("run-a", "run-b");
    assertThat(report.deltas().get(0).changeType()).isEqualTo(ChangeType.dropped_miss);
    assertThat(report.summary().droppedMiss()).isEqualTo(1);
  }

  @Test
  void state_changed_when_timeout_to_out_of_scope() {
    when(resultRepository.findAllByRunId("run-a"))
        .thenReturn(List.of(cell("run-a", "asset-1", "policy-1", CoverageHitState.timeout)));
    when(resultRepository.findAllByRunId("run-b"))
        .thenReturn(List.of(cell("run-b", "asset-1", "policy-1", CoverageHitState.out_of_scope)));

    CoverageDiffReport report = calculator.compare("run-a", "run-b");
    assertThat(report.deltas().get(0).changeType()).isEqualTo(ChangeType.state_changed);
    assertThat(report.summary().stateChanged()).isEqualTo(1);
  }

  @Test
  void unknown_run_throws() {
    when(runRepository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> calculator.compare("missing", "run-b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown run id");
  }
}
