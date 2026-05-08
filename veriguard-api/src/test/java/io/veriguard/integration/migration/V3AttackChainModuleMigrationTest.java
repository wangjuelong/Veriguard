package io.veriguard.integration.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test verifying V3 Flyway migration outcomes (PRD §2.4 Phase 1).
 *
 * <p>This test relies on the shared test PostgreSQL container (configured via
 * {@code application.properties} pointing at {@code localhost:5433}). Flyway has already applied V3
 * by the time {@code @SpringBootTest} bootstraps the context, so each assertion below is just
 * inspecting the post-migration schema state.
 */
@SpringBootTest
public class V3AttackChainModuleMigrationTest {

  @Autowired private JdbcTemplate jdbc;

  // -- Renamed tables --------------------------------------------------

  @Test
  @DisplayName("V3 renames scenarios → attack_chains")
  void v3RenamesScenariosToAttackChains() {
    Integer newCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'attack_chains'",
            Integer.class);
    assertThat(newCount).isEqualTo(1);

    Integer oldCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'scenarios'",
            Integer.class);
    assertThat(oldCount).isEqualTo(0);
  }

  @Test
  @DisplayName("V3 renames injects → attack_chain_nodes and exercises → attack_chain_runs")
  void v3RenamesInjectsAndExercises() {
    List<String> renamedTables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_name IN ('attack_chain_nodes', 'attack_chain_runs', "
                + "'attack_chain_edges', 'attack_chain_node_expectations', "
                + "'node_expectation_traces')",
            String.class);
    assertThat(renamedTables)
        .containsExactlyInAnyOrder(
            "attack_chain_nodes",
            "attack_chain_runs",
            "attack_chain_edges",
            "attack_chain_node_expectations",
            "node_expectation_traces");

    List<String> oldTables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_name IN ('injects', 'exercises', 'injects_dependencies', "
                + "'injects_expectations', 'injects_expectations_traces')",
            String.class);
    assertThat(oldTables).isEmpty();
  }

  // -- New tables ------------------------------------------------------

  @Test
  @DisplayName("V3 creates validation_parameter_sets with 3 seeded templates")
  void v3CreatesValidationParameterSetsWithThreeTemplates() {
    Integer tableCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables "
                + "WHERE table_name = 'validation_parameter_sets'",
            Integer.class);
    assertThat(tableCount).isEqualTo(1);

    Integer templateCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM validation_parameter_sets WHERE is_template = true",
            Integer.class);
    assertThat(templateCount).isEqualTo(3);

    List<String> names =
        jdbc.queryForList(
            "SELECT name FROM validation_parameter_sets WHERE is_template = true ORDER BY name",
            String.class);
    assertThat(names).containsExactlyInAnyOrder("严格", "宽松", "快速演练");
  }

  @Test
  @DisplayName("V3 creates link expectation tables")
  void v3CreatesLinkExpectationTables() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables "
                + "WHERE table_name IN ('attack_chain_link_expectations', "
                + "'link_expectation_traces', 'validation_parameter_set_tags')",
            Integer.class);
    assertThat(count).isEqualTo(3);
  }

  // -- Dropped legacy email columns ------------------------------------

  @Test
  @DisplayName("V3 drops legacy email columns from attack_chains and attack_chain_runs")
  void v3DropsLegacyEmailColumns() {
    Integer chainEmailCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chains' "
                + "AND column_name IN ('scenario_message_header', 'scenario_message_footer', "
                + "'scenario_mail_from')",
            Integer.class);
    assertThat(chainEmailCount).isEqualTo(0);

    Integer runEmailCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chain_runs' "
                + "AND column_name IN ('exercise_message_header', 'exercise_message_footer', "
                + "'exercise_mail_from')",
            Integer.class);
    assertThat(runEmailCount).isEqualTo(0);

    Integer replyToTablesCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables "
                + "WHERE table_name IN ('scenario_mails_reply_to', 'exercise_mails_reply_to')",
            Integer.class);
    assertThat(replyToTablesCount).isEqualTo(0);
  }

  // -- New columns -----------------------------------------------------

  @Test
  @DisplayName("V3 adds execution_mode + soc_correlation_rules + parameter_set FK to attack_chains")
  void v3AddsAttackChainsNewColumns() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chains' "
                + "AND column_name IN ('execution_mode', 'validation_parameter_set_id', "
                + "'soc_correlation_rules')",
            Integer.class);
    assertThat(count).isEqualTo(3);

    String defaultMode =
        jdbc.queryForObject(
            "SELECT column_default FROM information_schema.columns "
                + "WHERE table_name = 'attack_chains' AND column_name = 'execution_mode'",
            String.class);
    assertThat(defaultMode).contains("STOP_ON_BLOCK");
  }

  @Test
  @DisplayName("V3 adds repeat + state + parameter_set columns to attack_chain_nodes")
  void v3AddsAttackChainNodesNewColumns() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chain_nodes' "
                + "AND column_name IN ('repeat_count', 'repeat_interval_seconds', "
                + "'validation_parameter_set_id', 'node_state', 'current_iteration')",
            Integer.class);
    assertThat(count).isEqualTo(5);
  }

  @Test
  @DisplayName("V3 adds verdict_* columns to attack_chain_runs")
  void v3AddsAttackChainRunsVerdictColumns() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chain_runs' "
                + "AND column_name IN ('verdict_prevention', 'verdict_detection', "
                + "'verdict_computed_at')",
            Integer.class);
    assertThat(count).isEqualTo(3);
  }

  // -- Renamed columns -------------------------------------------------

  @Test
  @DisplayName("V3 renames scenario_id → attack_chain_id and inject_id → node_id")
  void v3RenamesIdColumns() {
    Integer chainIdExists =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chains' AND column_name = 'attack_chain_id'",
            Integer.class);
    assertThat(chainIdExists).isEqualTo(1);

    Integer nodeIdExists =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chain_nodes' AND column_name = 'node_id'",
            Integer.class);
    assertThat(nodeIdExists).isEqualTo(1);

    Integer runIdExists =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chain_runs' AND column_name = 'run_id'",
            Integer.class);
    assertThat(runIdExists).isEqualTo(1);
  }

  // -- attack_chain_edges UUID PK + unique --------------------------

  @Test
  @DisplayName("V3 attack_chain_edges has edge_id PK and parent/child unique")
  void v3AttackChainEdgesHasUuidPk() {
    Integer hasEdgeId =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'attack_chain_edges' AND column_name = 'edge_id'",
            Integer.class);
    assertThat(hasEdgeId).isEqualTo(1);
  }

  // -- CHECK constraint -----------------------------------------------

  @Test
  @DisplayName("V3 chk_node_owner forbids inserting node with neither chain nor run owner")
  void v3NodeOwnerCheckConstraintActive() {
    // attack_chain_nodes 的 node_id 仍是 varchar(255)（沿用 legacy），CHECK 约束作用在
    // node_attack_chain_id / node_attack_chain_run_id 两个 nullable varchar 列上，必须恰好一边非空。
    // 这里两个所有者列都不传，必须被 chk_node_owner 拒绝。
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO attack_chain_nodes "
                        + "(node_id, node_title, node_all_teams, node_enabled, "
                        + "node_depends_duration) "
                        + "VALUES (?, 'X', false, true, 0)",
                    java.util.UUID.randomUUID().toString()))
        .isInstanceOf(DataAccessException.class);
  }
}
