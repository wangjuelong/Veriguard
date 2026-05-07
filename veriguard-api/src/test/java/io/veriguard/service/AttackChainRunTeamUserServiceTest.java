package io.veriguard.service;

import static io.veriguard.utils.fixtures.AttackChainRunFixture.createDefaultCrisisAttackChainRun;
import static io.veriguard.utils.fixtures.AttackChainRunTeamUserFixture.createAttackChainRunTeamUser;
import static io.veriguard.utils.fixtures.TeamFixture.getDefaultTeam;
import static io.veriguard.utils.fixtures.UserFixture.getUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import io.veriguard.IntegrationTest;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.AttackChainRunTeamUser;
import io.veriguard.database.model.Team;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.AttackChainRunTeamUserRepository;
import io.veriguard.utilstest.RabbitMQTestListener;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class AttackChainRunTeamUserServiceTest extends IntegrationTest {

  @Mock private AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;

  @InjectMocks private AttackChainRunTeamUserService attackChainRunTeamUserService;

  @Captor private ArgumentCaptor<AttackChainRunTeamUser> teamUserCaptor;

  @Captor private ArgumentCaptor<List<AttackChainRunTeamUser>> teamUserListCaptor;

  @Test
  void given_validParameters_should_createAttackChainRunTeamUserSuccessfully() {
    // -- ARRANGE --
    AttackChainRun attackChainRun = createDefaultCrisisAttackChainRun();
    Team team = getDefaultTeam();
    User user = getUser();

    // -- ACT --
    attackChainRunTeamUserService.createAttackChainRunTeamUser(attackChainRun, team, user);

    // -- ASSERT --
    verify(attackChainRunTeamUserRepository).save(teamUserCaptor.capture());
    AttackChainRunTeamUser captured = teamUserCaptor.getValue();
    assertEquals(attackChainRun, captured.getAttackChainRun());
    assertEquals(team, captured.getTeam());
    assertEquals(user, captured.getUser());
  }

  @Test
  void given_multipleSourceTeamUsers_should_duplicateAllForTargetAttackChainRun() {
    // -- ARRANGE --
    AttackChainRun sourceAttackChainRun = createDefaultCrisisAttackChainRun();
    AttackChainRun targetAttackChainRun = createDefaultCrisisAttackChainRun();
    Team team = getDefaultTeam();
    User user1 = getUser("User1", "Last1", "user1@test.invalid");
    User user2 = getUser("User2", "Last2", "user2@test.invalid");

    AttackChainRunTeamUser source1 = createAttackChainRunTeamUser(sourceAttackChainRun, team, user1);
    AttackChainRunTeamUser source2 = createAttackChainRunTeamUser(sourceAttackChainRun, team, user2);

    // -- ACT --
    attackChainRunTeamUserService.duplicateTeamUsers(
        targetAttackChainRun, List.of(source1, source2), new HashMap<>());

    // -- ASSERT --
    verify(attackChainRunTeamUserRepository).saveAll(teamUserListCaptor.capture());
    List<AttackChainRunTeamUser> captured = teamUserListCaptor.getValue();
    assertEquals(2, captured.size());
    captured.forEach(etu -> assertEquals(targetAttackChainRun, etu.getAttackChainRun()));
    assertEquals(user1, captured.get(0).getUser());
    assertEquals(user2, captured.get(1).getUser());
  }
}
