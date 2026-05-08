package io.veriguard.rest.attack_chain_node.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Team;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeTeamsInput;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeUpdateActivationInput;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import io.veriguard.utils.fixtures.AttackChainRunFixture;
import io.veriguard.utils.fixtures.TeamFixture;
import io.veriguard.utils.fixtures.composers.AttackChainNodeComposer;
import io.veriguard.utils.fixtures.composers.AttackChainRunComposer;
import io.veriguard.utils.fixtures.composers.TeamComposer;
import io.veriguard.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(PER_CLASS)
@Transactional
class SimulationAttackChainNodeServiceTest extends IntegrationTest {

  @Autowired private SimulationAttackChainNodeService simulationAttackChainNodeService;
  @Autowired private AttackChainNodeRepository attackChainNodeRepository;
  @Autowired private AttackChainRunComposer attackChainRunComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private TeamComposer teamComposer;

  private AttackChainRun simulationA;
  private AttackChainRun simulationB;
  private AttackChainNode attackChainNodeInA;
  private AttackChainNode attackChainNodeInB;

  @BeforeEach
  void setUp() {
    AttackChainNodeComposer.Composer attackChainNodeComposerA =
        attackChainNodeComposer.forAttackChainNode(
            AttackChainNodeFixture.getDefaultAttackChainNode());
    AttackChainNodeComposer.Composer attackChainNodeComposerB =
        attackChainNodeComposer.forAttackChainNode(
            AttackChainNodeFixture.getDefaultAttackChainNode());

    attackChainRunComposer
        .forAttackChainRun(AttackChainRunFixture.createDefaultAttackChainRun())
        .withAttackChainNode(attackChainNodeComposerA)
        .persist();
    simulationA = attackChainNodeComposerA.get().getAttackChainRun();
    attackChainNodeInA = attackChainNodeComposerA.get();

    attackChainRunComposer
        .forAttackChainRun(AttackChainRunFixture.createDefaultAttackChainRun())
        .withAttackChainNode(attackChainNodeComposerB)
        .persist();
    simulationB = attackChainNodeComposerB.get().getAttackChainRun();
    attackChainNodeInB = attackChainNodeComposerB.get();
  }

  // -- READ --

  @Nested
  class FindAttackChainNodeForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToSimulation_should_returnAttackChainNode() {
      // -- ACT --
      AttackChainNode result =
          simulationAttackChainNodeService.findAttackChainNodeForSimulation(
              simulationA.getId(), attackChainNodeInA.getId());

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(attackChainNodeInA.getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationAttackChainNodeService.findAttackChainNodeForSimulation(
                      simulationA.getId(), attackChainNodeInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  class FindAttackChainNodeTeamsForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToSimulation_should_returnTeams() {
      // -- ARRANGE --
      TeamComposer.Composer teamWrapper =
          teamComposer.forTeam(TeamFixture.createTeamWithName(null));
      teamWrapper.persist();
      attackChainNodeInA.setTeams(List.of(teamWrapper.get()));
      attackChainNodeRepository.save(attackChainNodeInA);

      // -- ACT --
      List<Team> result =
          simulationAttackChainNodeService.findAttackChainNodeTeamsForSimulation(
              simulationA.getId(), attackChainNodeInA.getId());

      // -- ASSERT --
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().getId()).isEqualTo(teamWrapper.get().getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationAttackChainNodeService.findAttackChainNodeTeamsForSimulation(
                      simulationA.getId(), attackChainNodeInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- UPDATE --

  @Nested
  class UpdateAttackChainNodeActivationForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void
        given_attackChainNodeBelongsToSimulation_should_updateActivationAndReturnAttackChainNode() {
      // -- ARRANGE --
      AttackChainNodeUpdateActivationInput input = new AttackChainNodeUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT --
      AttackChainNode result =
          simulationAttackChainNodeService.updateAttackChainNodeActivationForSimulation(
              simulationA.getId(), attackChainNodeInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(attackChainNodeInA.getId());
      assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ARRANGE --
      AttackChainNodeUpdateActivationInput input = new AttackChainNodeUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationAttackChainNodeService.updateAttackChainNodeActivationForSimulation(
                      simulationA.getId(), attackChainNodeInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  class TriggerAttackChainNodeForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void
        given_attackChainNodeBelongsToSimulation_should_setTriggerNowDateAndReturnAttackChainNode() {
      // -- ACT --
      AttackChainNode result =
          simulationAttackChainNodeService.triggerAttackChainNodeForSimulation(
              simulationA.getId(), attackChainNodeInA.getId());

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(attackChainNodeInA.getId());
      assertThat(result.getTriggerNowDate()).isNotNull();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationAttackChainNodeService.triggerAttackChainNodeForSimulation(
                      simulationA.getId(), attackChainNodeInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  class UpdateAttackChainNodeTeamsForSimulation {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToSimulation_should_updateTeamsAndReturnAttackChainNode() {
      // -- ARRANGE --
      TeamComposer.Composer teamWrapper =
          teamComposer.forTeam(TeamFixture.createTeamWithName(null));
      teamWrapper.persist();
      AttackChainNodeTeamsInput input = new AttackChainNodeTeamsInput();
      input.setTeamIds(List.of(teamWrapper.get().getId()));

      // -- ACT --
      AttackChainNode result =
          simulationAttackChainNodeService.updateAttackChainNodeTeamsForSimulation(
              simulationA.getId(), attackChainNodeInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(attackChainNodeInA.getId());
      assertThat(result.getTeams()).hasSize(1);
      assertThat(result.getTeams().getFirst().getId()).isEqualTo(teamWrapper.get().getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ARRANGE --
      AttackChainNodeTeamsInput input = new AttackChainNodeTeamsInput();
      input.setTeamIds(List.of());

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationAttackChainNodeService.updateAttackChainNodeTeamsForSimulation(
                      simulationA.getId(), attackChainNodeInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- DELETE --

  @Nested
  class DeleteAttackChainNode {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToSimulation_should_deleteAttackChainNode() {
      // -- ACT --
      simulationAttackChainNodeService.deleteAttackChainNode(
          simulationA.getId(), attackChainNodeInA.getId());

      // -- ASSERT --
      assertThat(attackChainNodeRepository.findById(attackChainNodeInA.getId())).isEmpty();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherSimulation_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  simulationAttackChainNodeService.deleteAttackChainNode(
                      simulationA.getId(), attackChainNodeInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }
}
