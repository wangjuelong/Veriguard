package io.veriguard.scheduler.jobs;

import static io.veriguard.helper.StreamHelper.fromIterable;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChain;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.service.scenario.AttackChainService;
import io.veriguard.utils.fixtures.AttackChainFixture;
import io.veriguard.utils.fixtures.composers.AttackChainComposer;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttackChainExecutionJobTest extends IntegrationTest {

  @Autowired private AttackChainExecutionJob job;
  @Autowired private AttackChainComposer attackChainComposer;

  @Autowired private AttackChainService attackChainService;
  @Autowired private AttackChainRunRepository attackChainRunRepository;

  static String SCENARIO_ID_1;
  static String SCENARIO_ID_2;
  static String SCENARIO_ID_3;
  static String EXERCISE_ID;

  @Nested
  @DisplayName("When using cron-based recurrence")
  public class WhenUSingCronBasedRecurrence {
    @DisplayName("Not create simulation based on recurring scenario in one hour")
    @Test
    void given_cron_in_one_hour_should_not_create_simulation() throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));
      int hourToStart = (zonedDateTime.getHour() + 1) % 24;

      AttackChain attackChain = AttackChainFixture.getAttackChain();
      attackChain.setRecurrence(
          "0 "
              + zonedDateTime.getMinute()
              + " "
              + hourToStart
              + " * * *"); // Every day now + 1 hour
      AttackChain attackChainSaved = attackChainService.createAttackChain(attackChain);
      SCENARIO_ID_1 = attackChainSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- ASSERT --
      List<AttackChainRun> createdAttackChainRuns =
          fromIterable(attackChainRunRepository.findAll()).stream()
              .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
              .filter(attackChainRun -> SCENARIO_ID_1.equals(attackChainRun.getAttackChain().getId()))
              .toList();
      assertEquals(0, createdAttackChainRuns.size());
    }

    @DisplayName("Create simulation based on recurring scenario now")
    @Test
    void given_cron_in_one_minute_should_create_simulation() throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));

      AttackChain attackChain = AttackChainFixture.getAttackChain();
      int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
      int hourToStart = zonedDateTime.getHour() + ((zonedDateTime.getMinute() + 1) / 60);
      hourToStart = hourToStart % 24;

      attackChain.setRecurrence(
          "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
      AttackChain attackChainSaved = attackChainService.createAttackChain(attackChain);
      SCENARIO_ID_2 = attackChainSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- ASSERT --
      List<AttackChainRun> createdAttackChainRuns =
          fromIterable(attackChainRunRepository.findAll()).stream()
              .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
              .filter(attackChainRun -> SCENARIO_ID_2.equals(attackChainRun.getAttackChain().getId()))
              .toList();
      assertEquals(1, createdAttackChainRuns.size());
      AttackChainRun createdAttackChainRun = createdAttackChainRuns.getFirst();
      assertNotNull(createdAttackChainRun.getStart());

      EXERCISE_ID = createdAttackChainRun.getId();
    }

    @DisplayName("Already created simulation based on recurring scenario")
    @Test
    void given_cron_in_one_minute_should_not_create_second_simulation()
        throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));

      AttackChain attackChain = AttackChainFixture.getAttackChain();
      int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
      int hourToStart = zonedDateTime.getHour() + ((zonedDateTime.getMinute() + 1) / 60);
      hourToStart = hourToStart % 24;

      attackChain.setRecurrence(
          "0 " + minuteToStart + " " + hourToStart + " * * *"); // Every day now + 1 minute
      AttackChain attackChainSaved = attackChainService.createAttackChain(attackChain);
      SCENARIO_ID_2 = attackChainSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- EXECUTE AGAIN --
      job.execute(null);

      // -- ASSERT --
      List<AttackChainRun> createdAttackChainRuns =
          fromIterable(attackChainRunRepository.findAll()).stream()
              .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
              .filter(attackChainRun -> SCENARIO_ID_2.equals(attackChainRun.getAttackChain().getId()))
              .toList();
      assertEquals(1, createdAttackChainRuns.size());
    }

    @DisplayName("Not create simulation based on end date before now")
    @Test
    void given_end_date_before_now_should_not_create_second_simulation()
        throws JobExecutionException {
      // -- PREPARE --
      ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));

      AttackChain attackChain = AttackChainFixture.getAttackChain();
      int minuteToStart = (zonedDateTime.getMinute() + 1) % 60;
      attackChain.setRecurrence(
          "0 "
              + minuteToStart
              + " "
              + zonedDateTime.getHour()
              + " * * *"); // Every day now + 1 minute
      attackChain.setRecurrenceEnd(Instant.now().minus(0, ChronoUnit.DAYS));
      AttackChain attackChainSaved = attackChainService.createAttackChain(attackChain);
      SCENARIO_ID_3 = attackChainSaved.getId();

      // -- EXECUTE --
      job.execute(null);

      // -- ASSERT --
      List<AttackChainRun> createdAttackChainRuns =
          fromIterable(attackChainRunRepository.findAll()).stream()
              .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
              .filter(attackChainRun -> SCENARIO_ID_3.equals(attackChainRun.getAttackChain().getId()))
              .toList();
      assertEquals(0, createdAttackChainRuns.size());
    }
  }

  @Nested
  @DisplayName("When using ISO 8601 period-based recurrence")
  public class WhenUsingISO8601PeriodBasedRecurrence {
    private final Instant attackChainStartTime = Instant.parse("2022-05-27T10:43:22Z");

    private AttackChain getAttackChain() {
      AttackChain attackChain = AttackChainFixture.getAttackChain();
      attackChain.setRecurrenceStart(attackChainStartTime);
      attackChain.setRecurrence("P1D");
      return attackChain;
    }

    private void setMockedInstant(MockedStatic<Instant> mockedInstant, Instant target) {
      mockedInstant.when(Instant::now).thenReturn(target);
    }

    @Test
    @DisplayName("When next occurrence is set in the future, do not create simulation")
    public void whenNextOccurrenceIsSetInTheFuture_doNotCreateSimulation()
        throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        AttackChain attackChain = getAttackChain();

        setMockedInstant(mockedInstant, attackChainStartTime.plus(6, ChronoUnit.HOURS));

        AttackChainComposer.Composer attackChainWrapper =
            attackChainComposer.forAttackChain(attackChain).persist();

        // -- EXECUTE --
        job.execute(null);

        // -- ASSERT --
        List<AttackChainRun> createdAttackChainRuns =
            fromIterable(attackChainRunRepository.findAll()).stream()
                .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
                .filter(
                    attackChainRun ->
                        attackChainWrapper.get().getId().equals(attackChainRun.getAttackChain().getId()))
                .toList();
        assertThat(createdAttackChainRuns).isEmpty();
      }
    }

    @Test
    @DisplayName("When next occurrence is due now, do create simulation")
    public void whenNextOccurrenceIsDueNow_doCreateSimulation() throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        AttackChain attackChain = getAttackChain();

        setMockedInstant(
            mockedInstant, attackChainStartTime.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES));

        AttackChainComposer.Composer attackChainWrapper =
            attackChainComposer.forAttackChain(attackChain).persist();

        // -- EXECUTE --
        job.execute(null);

        // -- ASSERT --
        List<AttackChainRun> createdAttackChainRuns =
            fromIterable(attackChainRunRepository.findAll()).stream()
                .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
                .filter(
                    attackChainRun ->
                        attackChainWrapper.get().getId().equals(attackChainRun.getAttackChain().getId()))
                .toList();
        assertThat(createdAttackChainRuns)
            .singleElement()
            .satisfies(
                attackChainRun -> assertThat(attackChainRun.getAttackChain()).isEqualTo(attackChainWrapper.get()));
      }
    }

    @Test
    @DisplayName("When due simulation is already created, do not create it twice")
    public void whenDueSimulationIsAlreadyCreated_doNotCreateItTwice()
        throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        AttackChain attackChain = getAttackChain();

        setMockedInstant(
            mockedInstant, attackChainStartTime.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES));

        AttackChainComposer.Composer attackChainWrapper =
            attackChainComposer.forAttackChain(attackChain).persist();

        // -- EXECUTE --
        job.execute(null);
        // twice
        job.execute(null);

        // -- ASSERT --
        List<AttackChainRun> createdAttackChainRuns =
            fromIterable(attackChainRunRepository.findAll()).stream()
                .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
                .filter(
                    attackChainRun ->
                        attackChainWrapper.get().getId().equals(attackChainRun.getAttackChain().getId()))
                .toList();
        assertThat(createdAttackChainRuns)
            .singleElement()
            .satisfies(
                attackChainRun -> assertThat(attackChainRun.getAttackChain()).isEqualTo(attackChainWrapper.get()));
      }
    }

    @Test
    @DisplayName("When recurrence end date is past, do not create simulation")
    public void whenRecurrenceEndDateIsPast_doNotCreateSimulation() throws JobExecutionException {
      try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
        AttackChain attackChain = getAttackChain();
        attackChain.setRecurrenceEnd(attackChainStartTime.plus(10, ChronoUnit.DAYS));

        setMockedInstant(
            mockedInstant,
            attackChainStartTime.plus(11, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES));

        AttackChainComposer.Composer attackChainWrapper =
            attackChainComposer.forAttackChain(attackChain).persist();

        // -- EXECUTE --
        job.execute(null);

        // -- ASSERT --
        List<AttackChainRun> createdAttackChainRuns =
            fromIterable(attackChainRunRepository.findAll()).stream()
                .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
                .filter(
                    attackChainRun ->
                        attackChainWrapper.get().getId().equals(attackChainRun.getAttackChain().getId()))
                .toList();
        assertThat(createdAttackChainRuns).isEmpty();
      }
    }
  }

  @Test
  @DisplayName("When recurrence expression cannot be handled, do not create simulation")
  public void whenRecurrenceExpressionCannotBeHandled_doNotCreateSimulation()
      throws JobExecutionException {
    AttackChain attackChain = AttackChainFixture.getAttackChain();
    attackChain.setRecurrence("can not handle this expression!");
    AttackChainComposer.Composer attackChainWrapper = attackChainComposer.forAttackChain(attackChain).persist();

    // -- EXECUTE --
    job.execute(null);

    // -- ASSERT --
    List<AttackChainRun> createdAttackChainRuns =
        fromIterable(attackChainRunRepository.findAll()).stream()
            .filter(attackChainRun -> attackChainRun.getAttackChain() != null)
            .filter(
                attackChainRun -> attackChainWrapper.get().getId().equals(attackChainRun.getAttackChain().getId()))
            .toList();
    assertThat(createdAttackChainRuns).isEmpty();
  }
}
