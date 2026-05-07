package io.veriguard.database.helper;

import io.veriguard.database.model.ExecutionTrace;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository helper for low-level database operations on execution traces and attackChainNode statuses.
 *
 * <p>This helper provides optimized JDBC-based operations for performance-critical database
 * updates, bypassing JPA overhead when direct SQL execution is more efficient. It is particularly
 * useful for high-volume operations during attackChainNode execution tracking.
 *
 * <p>Operations include:
 *
 * <ul>
 *   <li>Inserting new execution traces
 *   <li>Updating attackChainNode status states
 *   <li>Updating attackChainNode timestamps
 * </ul>
 *
 * @see ExecutionTrace
 */
@Repository
public class ExecutionTraceRepositoryHelper {

  @Autowired private DataSource dataSource;

  /** SQL statement for inserting a new execution trace record. */
  private static final String INSERT_EXECUTION_TRACE =
      """
          INSERT INTO execution_traces (
            execution_trace_id,
            execution_inject_status_id,
            execution_inject_test_status_id,
            execution_agent_id,
            execution_message,
            execution_structured_output,
            execution_action,
            execution_status,
            execution_time,
            execution_context_identifiers,
            execution_created_at,
            execution_updated_at
        ) VALUES (
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?
        )""";

  /**
   * Saves an execution trace using a direct JDBC call for improved performance.
   *
   * <p>This method bypasses JPA to directly insert the execution trace record, which is more
   * efficient for high-volume insert operations during attackChainNode execution.
   *
   * @param executionTrace the execution trace to save
   * @return the generated UUID of the newly created trace
   * @throws RuntimeException if the database insert fails
   */
  public String saveExecutionTrace(ExecutionTrace executionTrace) {
    try (Connection conn = dataSource.getConnection()) {

      try (PreparedStatement ps = conn.prepareStatement(INSERT_EXECUTION_TRACE)) {

        String attackChainNodeStatusId = null;
        if (executionTrace.getAttackChainNodeStatus() != null) {
          attackChainNodeStatusId = executionTrace.getAttackChainNodeStatus().getId();
        }
        String attackChainNodeTestStatusId = null;
        if (executionTrace.getAttackChainNodeTestStatus() != null) {
          attackChainNodeTestStatusId = executionTrace.getAttackChainNodeTestStatus().getId();
        }
        String agentId = null;
        if (executionTrace.getAgent() != null) {
          agentId = executionTrace.getAgent().getId();
        }
        String structuredOutputAsText = null;
        if (executionTrace.getStructuredOutput() != null) {
          structuredOutputAsText = executionTrace.getStructuredOutput().asText();
        }
        String id = UUID.randomUUID().toString();

        ps.setString(1, id);
        ps.setString(2, attackChainNodeStatusId);
        ps.setString(3, attackChainNodeTestStatusId);
        ps.setString(4, agentId);
        ps.setString(5, executionTrace.getMessage());
        ps.setString(6, structuredOutputAsText);
        ps.setString(7, executionTrace.getAction().name());
        ps.setString(8, executionTrace.getStatus().name());
        ps.setTimestamp(9, Timestamp.from(executionTrace.getTime()));
        ps.setArray(10, conn.createArrayOf("text", executionTrace.getIdentifiers().toArray()));
        ps.setTimestamp(11, Timestamp.from(executionTrace.getCreationDate()));
        ps.setTimestamp(12, Timestamp.from(executionTrace.getUpdateDate()));

        ps.executeUpdate();

        return id;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert execution trace", e);
    }
  }

  /**
   * Updates an attackChainNode status with a new status name and end date using direct JDBC.
   *
   * <p>This method is used to efficiently update the status of an attackChainNode execution without loading
   * the full entity through JPA.
   *
   * @param attackChainNodeStatusId the ID of the attackChainNode status to update
   * @param name the new status name (e.g., "SUCCESS", "ERROR", "PENDING")
   * @param endDate the end timestamp for the attackChainNode execution, or {@code null} if not yet completed
   * @throws RuntimeException if the database update fails
   */
  public void updateAttackChainNodeStatus(String attackChainNodeStatusId, String name, Instant endDate) {
    String sql =
        "UPDATE injects_statuses SET status_name = ?, tracking_end_date = ? WHERE status_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, name);
      ps.setTimestamp(2, endDate != null ? Timestamp.from(endDate) : null);
      ps.setString(3, attackChainNodeStatusId);
      ps.executeUpdate();

    } catch (SQLException e) {
      throw new RuntimeException("Failed to update inject status", e);
    }
  }

  /**
   * Updates the last modification timestamp of an attackChainNode using direct JDBC.
   *
   * <p>This lightweight operation updates only the {@code inject_updated_at} column without
   * triggering a full entity update, useful for tracking attackChainNode modifications efficiently.
   *
   * @param id the ID of the attackChainNode to update
   * @param updatedAt the new update timestamp, or {@code null} to clear the value
   * @throws RuntimeException if the database update fails
   */
  public void updateAttackChainNodeUpdateDate(String id, Instant updatedAt) {
    String sql = "UPDATE injects SET inject_updated_at = ? WHERE inject_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setTimestamp(1, updatedAt != null ? Timestamp.from(updatedAt) : null);
      ps.setString(2, id);
      ps.executeUpdate();

    } catch (SQLException e) {
      throw new RuntimeException("Failed to update inject update date", e);
    }
  }
}
