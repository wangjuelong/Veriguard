package io.veriguard.attackchain.execution;

import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.FAILED;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.PARTIAL;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.PENDING;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.SUCCESS;
import static io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS.UNKNOWN;
import static io.veriguard.database.model.EdgeCondition.ExpectationDimension.DETECTION;
import static io.veriguard.database.model.EdgeCondition.ExpectationDimension.PREVENTION;
import static io.veriguard.database.model.EdgeCondition.ExpectationStatusGroup.ALL_FAILED;
import static io.veriguard.database.model.EdgeCondition.ExpectationStatusGroup.ALL_SUCCESS;
import static io.veriguard.database.model.EdgeCondition.ExpectationStatusGroup.ANY_FAILED;
import static io.veriguard.database.model.EdgeCondition.ExpectationStatusGroup.ANY_SUCCESS;
import static io.veriguard.database.model.EdgeCondition.ExpectationStatusGroup.SETTLED;
import static org.assertj.core.api.Assertions.assertThat;

import io.veriguard.database.model.EdgeCondition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdgeConditionEvaluatorTest {

  private final EdgeConditionEvaluator evaluator = new EdgeConditionEvaluator();

  @Test
  @DisplayName("null condition is always traversable")
  void nullCondition_traversable() {
    assertThat(evaluator.evaluate(null, status(SUCCESS, FAILED, PENDING))).isTrue();
    assertThat(evaluator.evaluate(null, null)).isTrue();
  }

  @Test
  @DisplayName("Eq PREVENTION = ANY_SUCCESS matches node SUCCESS")
  void eq_anySuccess_matches() {
    var cond = new EdgeCondition.Eq(PREVENTION, ANY_SUCCESS);
    assertThat(evaluator.evaluate(cond, status(SUCCESS, null, null))).isTrue();
    assertThat(evaluator.evaluate(cond, status(FAILED, null, null))).isFalse();
    assertThat(evaluator.evaluate(cond, status(PARTIAL, null, null))).isFalse();
    assertThat(evaluator.evaluate(cond, status(PENDING, null, null))).isFalse();
  }

  @Test
  @DisplayName("Eq PREVENTION = ANY_FAILED matches node FAILED")
  void eq_anyFailed_matches() {
    var cond = new EdgeCondition.Eq(PREVENTION, ANY_FAILED);
    assertThat(evaluator.evaluate(cond, status(FAILED, null, null))).isTrue();
    assertThat(evaluator.evaluate(cond, status(SUCCESS, null, null))).isFalse();
  }

  @Test
  @DisplayName("ALL_SUCCESS / ANY_SUCCESS are equivalent at node level")
  void allSuccess_equiv_anySuccess() {
    var any = new EdgeCondition.Eq(PREVENTION, ANY_SUCCESS);
    var all = new EdgeCondition.Eq(PREVENTION, ALL_SUCCESS);
    var stat = status(SUCCESS, null, null);
    assertThat(evaluator.evaluate(any, stat)).isEqualTo(evaluator.evaluate(all, stat));
  }

  @Test
  @DisplayName("SETTLED matches SUCCESS / FAILED / PARTIAL but not PENDING / UNKNOWN")
  void settled_group() {
    var cond = new EdgeCondition.Eq(PREVENTION, SETTLED);
    assertThat(evaluator.evaluate(cond, status(SUCCESS, null, null))).isTrue();
    assertThat(evaluator.evaluate(cond, status(FAILED, null, null))).isTrue();
    assertThat(evaluator.evaluate(cond, status(PARTIAL, null, null))).isTrue();
    assertThat(evaluator.evaluate(cond, status(PENDING, null, null))).isFalse();
    assertThat(evaluator.evaluate(cond, status(UNKNOWN, null, null))).isFalse();
  }

  @Test
  @DisplayName("Eq across dimensions: PREVENTION vs DETECTION are independent")
  void dimensions_independent() {
    var prev = new EdgeCondition.Eq(PREVENTION, ANY_SUCCESS);
    var det = new EdgeCondition.Eq(DETECTION, ANY_SUCCESS);
    var stat = status(SUCCESS, FAILED, null);
    assertThat(evaluator.evaluate(prev, stat)).isTrue();
    assertThat(evaluator.evaluate(det, stat)).isFalse();
  }

  @Test
  @DisplayName("And requires ALL children to be true")
  void and_requires_all_true() {
    var prevSucc = new EdgeCondition.Eq(PREVENTION, ANY_SUCCESS);
    var detSucc = new EdgeCondition.Eq(DETECTION, ANY_SUCCESS);
    var both = new EdgeCondition.And(List.of(prevSucc, detSucc));

    assertThat(evaluator.evaluate(both, status(SUCCESS, SUCCESS, null))).isTrue();
    assertThat(evaluator.evaluate(both, status(SUCCESS, FAILED, null))).isFalse();
    assertThat(evaluator.evaluate(both, status(FAILED, SUCCESS, null))).isFalse();
  }

  @Test
  @DisplayName("Empty And is vacuously true")
  void emptyAnd_vacuouslyTrue() {
    assertThat(evaluator.evaluate(new EdgeCondition.And(List.of()), status(FAILED, FAILED, FAILED)))
        .isTrue();
  }

  @Test
  @DisplayName("Or accepts ANY child true")
  void or_accepts_any() {
    var prevSucc = new EdgeCondition.Eq(PREVENTION, ANY_SUCCESS);
    var detFail = new EdgeCondition.Eq(DETECTION, ANY_FAILED);
    var either = new EdgeCondition.Or(List.of(prevSucc, detFail));

    assertThat(evaluator.evaluate(either, status(SUCCESS, SUCCESS, null))).isTrue(); // prev OK
    assertThat(evaluator.evaluate(either, status(FAILED, FAILED, null))).isTrue(); // det OK
    assertThat(evaluator.evaluate(either, status(FAILED, SUCCESS, null))).isFalse();
  }

  @Test
  @DisplayName("Empty Or is vacuously false")
  void emptyOr_vacuouslyFalse() {
    assertThat(evaluator.evaluate(new EdgeCondition.Or(List.of()), status(SUCCESS, SUCCESS, SUCCESS)))
        .isFalse();
  }

  @Test
  @DisplayName("Nested And/Or evaluates correctly")
  void nested_andOr() {
    // (PREVENTION = SUCCESS) AND (DETECTION = SUCCESS OR DETECTION = PARTIAL → SETTLED)
    var inner =
        new EdgeCondition.Or(
            List.of(
                new EdgeCondition.Eq(DETECTION, ANY_SUCCESS),
                new EdgeCondition.Eq(DETECTION, SETTLED)));
    var nested =
        new EdgeCondition.And(List.of(new EdgeCondition.Eq(PREVENTION, ANY_SUCCESS), inner));

    assertThat(evaluator.evaluate(nested, status(SUCCESS, SUCCESS, null))).isTrue();
    assertThat(evaluator.evaluate(nested, status(SUCCESS, PARTIAL, null))).isTrue();
    assertThat(evaluator.evaluate(nested, status(SUCCESS, PENDING, null))).isFalse();
    assertThat(evaluator.evaluate(nested, status(FAILED, SUCCESS, null))).isFalse();
  }

  @Test
  @DisplayName("Eq returns false when parent dimension is null (no expectation)")
  void nullDimension_returnsFalse() {
    var cond = new EdgeCondition.Eq(PREVENTION, ANY_SUCCESS);
    assertThat(evaluator.evaluate(cond, status(null, SUCCESS, null))).isFalse();
  }

  @Test
  @DisplayName("ALL_FAILED / ANY_FAILED equivalent at node level")
  void allFailed_equiv_anyFailed() {
    var any = new EdgeCondition.Eq(PREVENTION, ANY_FAILED);
    var all = new EdgeCondition.Eq(PREVENTION, ALL_FAILED);
    var stat = status(FAILED, null, null);
    assertThat(evaluator.evaluate(any, stat)).isEqualTo(evaluator.evaluate(all, stat));
  }

  private static NodeFinalStatus status(
      io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS prev,
      io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS det,
      io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS man) {
    return new NodeFinalStatus(prev, det, man);
  }
}
