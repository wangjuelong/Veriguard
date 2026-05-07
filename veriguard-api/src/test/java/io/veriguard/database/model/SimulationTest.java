package io.veriguard.database.model;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.veriguard.IntegrationTest;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.utils.fixtures.AttackChainRunFixture;
import io.veriguard.utils.fixtures.composers.AttackChainRunComposer;
import io.veriguard.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(PER_CLASS)
@Transactional
class SimulationTest extends IntegrationTest {
  @Autowired private AttackChainRunComposer attackChainRunComposer;
  @Autowired private AttackChainRunRepository attackChainRunRepository;
  @Autowired private EntityManager entityManager;

  private final Instant attackChainRunStartTime = Instant.parse("2012-11-21T04:00:00Z");

  @Test
  @DisplayName("Given a persisted exercise, current pause from raw query is correctly persisted.")
  void GivenAnExercise_CurrentPauseFromRawQueryIsCorrectlyPersisted() {
    Instant expectedCurrentPauseTime = Instant.parse("2012-11-21T04:05:00Z");
    AttackChainRunComposer.Composer wrapper =
        attackChainRunComposer.forAttackChainRun(
            AttackChainRunFixture.createDefaultAttackAttackChainRun(attackChainRunStartTime));
    wrapper.get().setCurrentPause(expectedCurrentPauseTime); // current pause at T+5 minutes

    AttackChainRun expected = wrapper.persist().get();

    // reset JPA
    entityManager.flush();
    entityManager.clear();

    AttackChainRun dbAttackChainRun = attackChainRunRepository.findById(expected.getId()).orElseThrow();

    Assertions.assertTrue(
        expected.getCurrentPause().isPresent(),
        "Current pause should be present for expected exercise");
    Assertions.assertEquals(expected.getCurrentPause().get(), dbAttackChainRun.getCurrentPause().get());
  }
}
