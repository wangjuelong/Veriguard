package io.veriguard.database.model;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.veriguard.IntegrationTest;
import io.veriguard.utils.fixtures.AttackChainNodeFixture;
import io.veriguard.utils.fixtures.AttackChainRunFixture;
import io.veriguard.utils.fixtures.PauseFixture;
import io.veriguard.utils.fixtures.composers.AttackChainNodeComposer;
import io.veriguard.utils.fixtures.composers.AttackChainRunComposer;
import io.veriguard.utils.fixtures.composers.PauseComposer;
import io.veriguard.utilstest.RabbitMQTestListener;
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
public class AttackChainNodeTest extends IntegrationTest {

  @Autowired private AttackChainRunComposer attackChainRunComposer;
  @Autowired private AttackChainNodeComposer attackChainNodeComposer;
  @Autowired private PauseComposer pauseComposer;

  @Nested
  @DisplayName("Given valid exercise")
  public class GivenValidAttackChainRunWithTwoPauses {

    private final Instant attackChainRunStartTime = Instant.parse("2012-11-21T04:00:00Z");

    public AttackChainRunComposer.Composer getAttackChainRunComposer() {
      return attackChainRunComposer
          .forAttackChainRun(
              AttackChainRunFixture.createRunningAttackAttackChainRun(attackChainRunStartTime))
          .withAttackChainNode(
              attackChainNodeComposer.forAttackChainNode(
                  AttackChainNodeFixture.getDefaultAttackChainNodeWithDuration(
                      600L))); // run time 04:10
    }

    @Nested
    @DisplayName("With two pauses, one of which starts after original inject time")
    public class WithTwoPauses {

      private final Instant firstPauseStartTime = Instant.parse("2012-11-21T04:02:00Z");
      private final Instant secondPauseStartTime = Instant.parse("2012-11-21T04:15:00Z");

      @Test
      @DisplayName(
          "When the inject was affected by both pauses its starting date should account for both pauses")
      public void
          WhenAttackChainNodeEffectivelyWasPausedTwice_AttackChainNodeDateAccountsForBothPauses() {
        AttackChainRun attackChainRun =
            getAttackChainRunComposer()
                .withPause(
                    pauseComposer.forPause(
                        // first pause duration brings wakeup close to second pause start
                        // so that attackChainNode does not run in between
                        PauseFixture.createPause(firstPauseStartTime, 600L))) // wakeup 04:12
                .withPause(
                    pauseComposer.forPause(
                        PauseFixture.createPause(secondPauseStartTime, 3600L))) // wakeup 05:15
                .get();
        Instant expected_instant = Instant.parse("2012-11-21T05:20:00Z");

        AttackChainNode attackChainNode = attackChainRun.getAttackChainNodes().getFirst();

        Assertions.assertTrue(attackChainNode.getDate().isPresent(), "Inject has no date.");
        Assertions.assertEquals(expected_instant, attackChainNode.getDate().get());
      }

      @Test
      @DisplayName(
          "When the inject was affected by first pause only its starting date should account for first pause only")
      public void
          WhenAttackChainNodeEffectivelyWasPausedOnce_AttackChainNodeDateAccountsForSinglePause() {
        AttackChainRun attackChainRun =
            getAttackChainRunComposer()
                .withPause(
                    pauseComposer.forPause(
                        // first pause is short enough so that attackChainNode runs before second
                        // pause
                        // the pause will effectively delay attackChainNode by a single minute
                        PauseFixture.createPause(
                            firstPauseStartTime, 15L))) // wakeup 04:02:15, effective 04:03:00
                .withPause(
                    pauseComposer.forPause(
                        PauseFixture.createPause(secondPauseStartTime, 3600L))) // wakeup 05:15
                .get();
        Instant expected_instant = Instant.parse("2012-11-21T04:11:00Z");

        AttackChainNode attackChainNode = attackChainRun.getAttackChainNodes().getFirst();

        Assertions.assertTrue(attackChainNode.getDate().isPresent(), "Inject has no date.");
        Assertions.assertEquals(expected_instant, attackChainNode.getDate().get());
      }
    }
  }
}
