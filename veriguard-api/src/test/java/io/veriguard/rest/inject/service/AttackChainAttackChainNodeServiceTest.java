package io.veriguard.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import io.veriguard.rest.inject.form.AttackChainNodeInput;
import io.veriguard.rest.inject.form.AttackChainNodeUpdateActivationInput;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import io.veriguard.utils.fixtures.AttackChainFixture;
import io.veriguard.utils.fixtures.composers.AttackChainNodeComposer;
import io.veriguard.utils.fixtures.composers.AttackChainComposer;
import io.veriguard.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(PER_CLASS)
@Transactional
class AttackChainAttackChainNodeServiceTest extends IntegrationTest {

  @Autowired private AttackChainAttackChainNodeService attackChainAttackChainNodeService;
  @Autowired private AttackChainNodeRepository attackChainNodeRepository;
  @Autowired private AttackChainComposer attackChainComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;

  private AttackChain attackChainA;
  private AttackChain attackChainB;
  private AttackChainNode attackChainNodeInA;
  private AttackChainNode attackChainNodeInB;

  @BeforeEach
  void setUp() {
    AttackChainNodeComposer.Composer attackChainNodeComposerA =
        attackChainNodeComposer.forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode());
    AttackChainNodeComposer.Composer attackChainNodeComposerB =
        attackChainNodeComposer.forAttackChainNode(AttackChainNodeFixture.getDefaultAttackChainNode());

    attackChainComposer
        .forAttackChain(AttackChainFixture.createDefaultCrisisAttackChain())
        .withAttackChainNode(attackChainNodeComposerA)
        .persist();
    attackChainA = attackChainNodeComposerA.get().getAttackChain();
    attackChainNodeInA = attackChainNodeComposerA.get();

    attackChainComposer
        .forAttackChain(AttackChainFixture.createDefaultCrisisAttackChain())
        .withAttackChainNode(attackChainNodeComposerB)
        .persist();
    attackChainB = attackChainNodeComposerB.get().getAttackChain();
    attackChainNodeInB = attackChainNodeComposerB.get();
  }

  // -- READ --

  @Nested
  class FindAttackChainNodeForAttackChain {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToScenario_should_returnAttackChainNode() {
      // -- ACT --
      AttackChainNode result =
          attackChainAttackChainNodeService.findAttackChainNodeForAttackChain(attackChainA.getId(), attackChainNodeInA.getId());

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(attackChainNodeInA.getId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  attackChainAttackChainNodeService.findAttackChainNodeForAttackChain(attackChainA.getId(), attackChainNodeInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- UPDATE --

  @Nested
  class UpdateAttackChainNodeForAttackChain {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToScenario_should_updateAndReturnAttackChainNode() {
      // -- ARRANGE --
      AttackChainNodeInput input = new AttackChainNodeInput();
      input.setTitle("Updated title");
      input.setDependsDuration(0L);

      // -- ACT --
      AttackChainNode result =
          attackChainAttackChainNodeService.updateAttackChainNodeForAttackChain(
              attackChainA.getId(), attackChainNodeInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(attackChainNodeInA.getId());
      assertThat(result.getTitle()).isEqualTo("Updated title");
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ARRANGE --
      AttackChainNodeInput input = new AttackChainNodeInput();
      input.setTitle("Updated title");
      input.setDependsDuration(0L);

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  attackChainAttackChainNodeService.updateAttackChainNodeForAttackChain(
                      attackChainA.getId(), attackChainNodeInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  @Nested
  class UpdateAttackChainNodeActivationForAttackChain {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToScenario_should_updateActivationAndReturnAttackChainNode() {
      // -- ARRANGE --
      AttackChainNodeUpdateActivationInput input = new AttackChainNodeUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT --
      AttackChainNode result =
          attackChainAttackChainNodeService.updateAttackChainNodeActivationForAttackChain(
              attackChainA.getId(), attackChainNodeInA.getId(), input);

      // -- ASSERT --
      assertThat(result.getId()).isEqualTo(attackChainNodeInA.getId());
      assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ARRANGE --
      AttackChainNodeUpdateActivationInput input = new AttackChainNodeUpdateActivationInput();
      input.setEnabled(false);

      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  attackChainAttackChainNodeService.updateAttackChainNodeActivationForAttackChain(
                      attackChainA.getId(), attackChainNodeInB.getId(), input))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }

  // -- DELETE --

  @Nested
  class DeleteAttackChainNode {

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToScenario_should_deleteAttackChainNode() {
      // -- ACT --
      attackChainAttackChainNodeService.deleteAttackChainNode(attackChainA.getId(), attackChainNodeInA.getId());

      // -- ASSERT --
      assertThat(attackChainNodeRepository.findById(attackChainNodeInA.getId())).isEmpty();
    }

    @Test
    @WithMockUser(isAdmin = true)
    void given_attackChainNodeBelongsToAnotherScenario_should_throwElementNotFoundException() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () -> attackChainAttackChainNodeService.deleteAttackChainNode(attackChainA.getId(), attackChainNodeInB.getId()))
          .isInstanceOf(ElementNotFoundException.class);
    }
  }
}
