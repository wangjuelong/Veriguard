package io.veriguard.database.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository helper for low-level database operations on attackChainNode expectations.
 *
 * <p>This helper provides optimized JDBC-based operations for updating attackChainNode expectation
 * signatures, which are stored as JSONB arrays in PostgreSQL. Direct SQL is used for atomic append
 * operations that would be complex or inefficient through JPA.
 *
 * @see io.veriguard.database.model.AttackChainNodeExpectation
 * @see io.veriguard.database.model.NodeExpectationSignature
 */
@Repository
public class AttackChainNodeExpectationRepositoryHelper {

  @Autowired private DataSource dataSource;

  /**
   * Appends a new signature entry to an attackChainNode expectation's signature array.
   *
   * <p>This method atomically appends a type/value tuple to the JSONB signature array for a
   * specific attackChainNode and agent combination. The operation is performed using PostgreSQL's
   * native JSONB concatenation for optimal performance.
   *
   * @param attackChainNodeId the ID of the attackChainNode
   * @param agentId the ID of the agent
   * @param type the signature type (e.g., "process_name", "command_line", "file_hash")
   * @param value the signature value
   * @throws RuntimeException if the database update fails
   */
  public void insertSignatureForAgentAndAttackChainNode(
      String attackChainNodeId, String agentId, String type, String value) {
    try (Connection conn = dataSource.getConnection()) {

      try (PreparedStatement ps =
          conn.prepareStatement(
              """
                UPDATE injects_expectations
                SET inject_expectation_signatures =
                    COALESCE(inject_expectation_signatures, '[]'::jsonb) ||
                    jsonb_build_array(jsonb_build_object('type', ?, 'value', ?))
                WHERE inject_id = ? AND agent_id = ?
                """)) {

        ps.setString(1, type);
        ps.setString(2, value);
        ps.setString(3, attackChainNodeId);
        ps.setString(4, agentId);

        ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
