package io.veriguard.database.raw.impl;

import io.veriguard.database.model.NodeExpectationTrace;
import io.veriguard.database.raw.RawExpectationTrace;
import java.time.Instant;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimpleRawExpectationTrace implements RawExpectationTrace {
  private String inject_expectation_trace_id;
  private String inject_expectation_trace_expectation;
  private String inject_expectation_trace_source_id;
  private String inject_expectation_trace_alert_name;
  private String inject_expectation_trace_alert_link;
  private Instant inject_expectation_trace_date;
  private Instant inject_expectation_trace_created_at;
  private Instant inject_expectation_trace_updated_at;

  /**
   * Compute object equality. Two traces are equal if they have the same attackChainNode
   * expectation, security platform, name and link. Trace dates are irrelevant for equality for now.
   *
   * @param o object to compare to
   * @return equality result
   */
  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SimpleRawExpectationTrace that = (SimpleRawExpectationTrace) o;
    return Objects.equals(
            getInject_expectation_trace_expectation(),
            that.getInject_expectation_trace_expectation())
        && Objects.equals(
            getInject_expectation_trace_source_id(), that.getInject_expectation_trace_source_id())
        && Objects.equals(
            getInject_expectation_trace_alert_name(), that.getInject_expectation_trace_alert_name())
        && Objects.equals(
            getInject_expectation_trace_alert_link(),
            that.getInject_expectation_trace_alert_link());
  }

  /**
   * Compute object hash following the same rules as equals method.
   *
   * @return computed hashcode
   */
  @Override
  public int hashCode() {
    return Objects.hash(
        getInject_expectation_trace_expectation(),
        getInject_expectation_trace_source_id(),
        getInject_expectation_trace_alert_name(),
        getInject_expectation_trace_alert_link());
  }

  /**
   * Convert an NodeExpectationTrace to a SimpleRawExpectationTrace.
   *
   * @param nodeExpectationTrace NodeExpectationTrace to convert
   * @return converted SimpleRawExpectationTrace
   */
  public static SimpleRawExpectationTrace of(NodeExpectationTrace nodeExpectationTrace) {
    return new SimpleRawExpectationTrace(
        nodeExpectationTrace.getId(),
        nodeExpectationTrace.getAttackChainNodeExpectation().getId(),
        nodeExpectationTrace.getSecurityPlatform().getId(),
        nodeExpectationTrace.getAlertName(),
        nodeExpectationTrace.getAlertLink(),
        nodeExpectationTrace.getAlertDate(),
        nodeExpectationTrace.getCreatedAt(),
        nodeExpectationTrace.getUpdatedAt());
  }
}
