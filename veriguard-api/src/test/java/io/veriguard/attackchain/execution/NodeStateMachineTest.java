package io.veriguard.attackchain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.NodeState;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeStateMachineTest {

  private final NodeStateMachine machine = new NodeStateMachine();

  // --- canTransition matrix ----------------------------------------

  @Test
  @DisplayName("null → PENDING is the only allowed initial transition")
  void initial_only_to_pending() {
    assertThat(machine.canTransition(null, NodeState.PENDING)).isTrue();
    assertThat(machine.canTransition(null, NodeState.SCHEDULED)).isFalse();
    assertThat(machine.canTransition(null, NodeState.RUNNING)).isFalse();
    assertThat(machine.canTransition(null, NodeState.SETTLED)).isFalse();
    assertThat(machine.canTransition(null, NodeState.FAILED)).isFalse();
  }

  @Test
  @DisplayName("PENDING → SCHEDULED / SKIPPED / FAILED")
  void pending_transitions() {
    assertThat(machine.canTransition(NodeState.PENDING, NodeState.SCHEDULED)).isTrue();
    assertThat(machine.canTransition(NodeState.PENDING, NodeState.SKIPPED)).isTrue();
    assertThat(machine.canTransition(NodeState.PENDING, NodeState.FAILED)).isTrue();
    assertThat(machine.canTransition(NodeState.PENDING, NodeState.RUNNING)).isFalse();
    assertThat(machine.canTransition(NodeState.PENDING, NodeState.SETTLED)).isFalse();
  }

  @Test
  @DisplayName("SCHEDULED → RUNNING / SKIPPED / FAILED")
  void scheduled_transitions() {
    assertThat(machine.canTransition(NodeState.SCHEDULED, NodeState.RUNNING)).isTrue();
    assertThat(machine.canTransition(NodeState.SCHEDULED, NodeState.SKIPPED)).isTrue();
    assertThat(machine.canTransition(NodeState.SCHEDULED, NodeState.FAILED)).isTrue();
    assertThat(machine.canTransition(NodeState.SCHEDULED, NodeState.SETTLED)).isFalse();
    assertThat(machine.canTransition(NodeState.SCHEDULED, NodeState.PENDING)).isFalse();
  }

  @Test
  @DisplayName("RUNNING → SETTLED / FAILED + RUNNING (repeat self-loop)")
  void running_transitions() {
    assertThat(machine.canTransition(NodeState.RUNNING, NodeState.SETTLED)).isTrue();
    assertThat(machine.canTransition(NodeState.RUNNING, NodeState.FAILED)).isTrue();
    // 节点重复执行：RUNNING → RUNNING 自环
    assertThat(machine.canTransition(NodeState.RUNNING, NodeState.RUNNING)).isTrue();
    assertThat(machine.canTransition(NodeState.RUNNING, NodeState.SKIPPED)).isFalse();
    assertThat(machine.canTransition(NodeState.RUNNING, NodeState.SCHEDULED)).isFalse();
  }

  @Test
  @DisplayName("Terminal states have no outgoing transitions")
  void terminal_states_no_outgoing() {
    for (NodeState terminal :
        new NodeState[] {NodeState.SETTLED, NodeState.SKIPPED, NodeState.FAILED}) {
      for (NodeState target : NodeState.values()) {
        assertThat(machine.canTransition(terminal, target))
            .as("%s should NOT transition to %s", terminal, target)
            .isFalse();
      }
    }
  }

  @Test
  @DisplayName("isTerminal: SETTLED / SKIPPED / FAILED are terminal")
  void isTerminal() {
    assertThat(machine.isTerminal(NodeState.SETTLED)).isTrue();
    assertThat(machine.isTerminal(NodeState.SKIPPED)).isTrue();
    assertThat(machine.isTerminal(NodeState.FAILED)).isTrue();
    assertThat(machine.isTerminal(NodeState.PENDING)).isFalse();
    assertThat(machine.isTerminal(NodeState.SCHEDULED)).isFalse();
    assertThat(machine.isTerminal(NodeState.RUNNING)).isFalse();
  }

  @Test
  @DisplayName("canTransition rejects null target")
  void null_target_rejected() {
    for (NodeState from : NodeState.values()) {
      assertThat(machine.canTransition(from, null)).isFalse();
    }
  }

  // --- transition method side effects ------------------------------

  @Test
  @DisplayName("transition mutates node.nodeState on legal move")
  void transition_legal_mutates() {
    var node = new AttackChainNode();
    node.setId(UUID.randomUUID().toString());
    node.setNodeState(NodeState.PENDING);

    machine.transition(node, NodeState.SCHEDULED);

    assertThat(node.getNodeState()).isEqualTo(NodeState.SCHEDULED);
  }

  @Test
  @DisplayName("transition throws on illegal move + leaves node state untouched")
  void transition_illegal_throws() {
    var node = new AttackChainNode();
    node.setId(UUID.randomUUID().toString());
    node.setNodeState(NodeState.SETTLED);

    assertThatThrownBy(() -> machine.transition(node, NodeState.RUNNING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SETTLED")
        .hasMessageContaining("RUNNING");

    assertThat(node.getNodeState()).isEqualTo(NodeState.SETTLED);
  }
}
