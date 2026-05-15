package io.veriguard.combination.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombinationExecutorRouterTest {

  private final StubCombinationExecutor stub = new StubCombinationExecutor(0.5, 0.3, 0.2);

  @Test
  void specific_executor_takes_precedence_over_stub() {
    SqlOnlyExecutor sqlExec = new SqlOnlyExecutor();
    CombinationExecutorRouter router = new CombinationExecutorRouter(List.of(sqlExec, stub), stub);

    CombinationExecutor selected = router.select("sql_injection");
    assertThat(selected).isSameAs(sqlExec);
  }

  @Test
  void fallback_used_when_no_specific_executor_matches() {
    SqlOnlyExecutor sqlExec = new SqlOnlyExecutor();
    CombinationExecutorRouter router = new CombinationExecutorRouter(List.of(sqlExec, stub), stub);

    CombinationExecutor selected = router.select("xss");
    assertThat(selected).isSameAs(stub);
  }

  @Test
  void dispatch_routes_to_selected_executor() {
    SqlOnlyExecutor sqlExec = new SqlOnlyExecutor();
    CombinationExecutorRouter router = new CombinationExecutorRouter(List.of(sqlExec, stub), stub);

    CombinationInstance sqlInstance =
        new CombinationInstance(
            "run1", "sql_injection:dim1", "sql_injection", "dim1", "asset-1", "p", "p");
    AttackCombinationHitState state = router.dispatch(sqlInstance).hitState();
    assertThat(state).isEqualTo(AttackCombinationHitState.hit);
    assertThat(sqlExec.callCount()).isEqualTo(1);
  }

  @Test
  void blank_base_type_throws() {
    CombinationExecutorRouter router = new CombinationExecutorRouter(List.of(stub), stub);
    assertThatThrownBy(() -> router.select("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> router.select(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stub_alone_handles_any_base_type() {
    CombinationExecutorRouter router = new CombinationExecutorRouter(List.of(stub), stub);
    assertThat(router.select("sql_injection")).isSameAs(stub);
    assertThat(router.select("rce")).isSameAs(stub);
    assertThat(router.select("anything_at_all")).isSameAs(stub);
  }

  @Test
  void stub_probabilities_must_sum_to_one_or_throw() {
    assertThatThrownBy(() -> new StubCombinationExecutor(0.5, 0.5, 0.5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sum to 1.0");
  }

  /** Fake specific executor for routing tests. */
  private static class SqlOnlyExecutor implements CombinationExecutor {
    private int callCount = 0;

    @Override
    public boolean supports(String baseAttackType) {
      return "sql_injection".equals(baseAttackType);
    }

    @Override
    public CombinationExecutionResult execute(CombinationInstance instance) {
      callCount++;
      return CombinationExecutionResult.of(AttackCombinationHitState.hit);
    }

    int callCount() {
      return callCount;
    }
  }
}
