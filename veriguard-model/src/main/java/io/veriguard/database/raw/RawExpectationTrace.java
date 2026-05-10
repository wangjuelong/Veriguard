package io.veriguard.database.raw;

import java.time.Instant;

/**
 * Spring Data projection interface for expectation trace data.
 *
 * <p>This interface defines a projection for retrieving expectation trace information. Traces
 * represent evidence from security platforms (SIEM, EDR, etc.) that supports or refutes
 * attackChainNode expectation results.
 *
 * @see io.veriguard.database.model.NodeExpectationTrace
 */
public interface RawExpectationTrace {

  /**
   * Returns the unique identifier of the expectation trace.
   *
   * @return the trace ID
   */
  String getNode_expectation_trace_id();

  /**
   * Returns the ID of the expectation this trace is associated with.
   *
   * @return the expectation ID
   */
  String getNode_expectation_trace_expectation();

  /**
   * Returns the source system identifier that generated this trace.
   *
   * @return the source ID
   */
  String getNode_expectation_trace_source_id();

  /**
   * Returns the name of the alert from the security platform.
   *
   * @return the alert name
   */
  String getNode_expectation_trace_alert_name();

  /**
   * Returns the URL link to the alert in the source security platform.
   *
   * @return the alert link URL
   */
  String getNode_expectation_trace_alert_link();

  /**
   * Returns the timestamp when the trace event occurred.
   *
   * @return the event timestamp
   */
  Instant getNode_expectation_trace_date();

  /**
   * Returns the creation timestamp of the trace record.
   *
   * @return the creation timestamp
   */
  Instant getNode_expectation_trace_created_at();

  /**
   * Returns the last update timestamp of the trace record.
   *
   * @return the update timestamp
   */
  Instant getNode_expectation_trace_updated_at();
}
