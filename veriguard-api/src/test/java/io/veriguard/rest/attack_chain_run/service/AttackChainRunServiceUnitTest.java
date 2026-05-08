package io.veriguard.rest.attack_chain_run.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.Team;
import io.veriguard.database.model.User;
import io.veriguard.database.repository.AttackChainNodeRepository;
import io.veriguard.database.repository.AttackChainRunRepository;
import io.veriguard.database.repository.AttackChainRunTeamUserRepository;
import io.veriguard.database.repository.LessonsCategoryRepository;
import io.veriguard.database.repository.TeamRepository;
import io.veriguard.database.repository.UserRepository;
import io.veriguard.service.TeamService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttackChainRunServiceUnitTest {

  @Mock private TeamService teamService;
  @Mock private AttackChainRunRepository attackChainRunRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private AttackChainRunTeamUserRepository attackChainRunTeamUserRepository;
  @Mock private AttackChainNodeRepository attackChainNodeRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;

  @Spy @InjectMocks private AttackChainRunService mockedAttackChainRunService;

  /* ============================================================
   * replaceTeams
   * ============================================================ */
  @Nested
  class ReplaceTeams {

    @Test
    void shouldFullyRemoveDeselectedTeamAndEnableOnlyNewTeams() {
      String attackChainRunId = "exercise-123";

      Team existingTeam1 = new Team();
      existingTeam1.setId("team-1");
      existingTeam1.setUsers(new ArrayList<>());

      Team existingTeam2 = new Team();
      existingTeam2.setId("team-2");
      existingTeam2.setUsers(new ArrayList<>());

      User newPlayer = new User();
      newPlayer.setId("user-1");

      Team newTeam = new Team();
      newTeam.setId("team-3");
      newTeam.setUsers(List.of(newPlayer));

      AttackChainRun attackChainRun = new AttackChainRun();
      attackChainRun.setId(attackChainRunId);
      attackChainRun.setTeams(new ArrayList<>(List.of(existingTeam1, existingTeam2)));

      when(attackChainRunRepository.findById(attackChainRunId))
          .thenReturn(Optional.of(attackChainRun));
      when(teamRepository.findAllById(any()))
          .thenAnswer(
              invocation -> {
                Iterable<String> ids = invocation.getArgument(0);
                Map<String, Team> teamsById = Map.of("team-2", existingTeam2, "team-3", newTeam);
                List<Team> result = new ArrayList<>();
                ids.forEach(
                    id -> {
                      Team team = teamsById.get(id);
                      if (team != null) {
                        result.add(team);
                      }
                    });
                return result;
              });
      when(userRepository.findById("user-1")).thenReturn(Optional.of(newPlayer));
      when(attackChainRunTeamUserRepository.existsByAttackChainRunIdAndTeamIdAndUserId(
              attackChainRunId, "team-3", "user-1"))
          .thenReturn(false);
      when(teamService.find(any())).thenReturn(List.of());

      mockedAttackChainRunService.replaceTeams(
          attackChainRunId, List.of("team-2", "team-3", "team-3"));

      verify(attackChainRunTeamUserRepository)
          .deleteByAttackChainRunIdAndTeamIds(
              eq(attackChainRunId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
      verify(attackChainNodeRepository)
          .removeTeamsForAttackChainRun(
              eq(attackChainRunId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
      verify(lessonsCategoryRepository)
          .removeTeamsForAttackChainRun(
              eq(attackChainRunId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));

      verify(attackChainRunTeamUserRepository)
          .existsByAttackChainRunIdAndTeamIdAndUserId(attackChainRunId, "team-3", "user-1");
      verify(attackChainRunTeamUserRepository, never())
          .existsByAttackChainRunIdAndTeamIdAndUserId(attackChainRunId, "team-2", "user-1");

      assertEquals(2, attackChainRun.getTeams().size());
      assertTrue(
          attackChainRun.getTeams().stream().anyMatch(team -> "team-2".equals(team.getId())));
      assertTrue(
          attackChainRun.getTeams().stream().anyMatch(team -> "team-3".equals(team.getId())));
    }

    @Test
    void shouldNotCallCleanupWhenNoTeamIsRemoved() {
      String attackChainRunId = "exercise-123";

      Team existingTeam = new Team();
      existingTeam.setId("team-1");
      existingTeam.setUsers(new ArrayList<>());

      AttackChainRun attackChainRun = new AttackChainRun();
      attackChainRun.setId(attackChainRunId);
      attackChainRun.setTeams(new ArrayList<>(List.of(existingTeam)));

      when(attackChainRunRepository.findById(attackChainRunId))
          .thenReturn(Optional.of(attackChainRun));
      when(teamRepository.findAllById(any())).thenReturn(List.of(existingTeam));
      when(teamService.find(any())).thenReturn(List.of());

      mockedAttackChainRunService.replaceTeams(attackChainRunId, List.of("team-1"));

      verify(attackChainRunTeamUserRepository, never())
          .deleteByAttackChainRunIdAndTeamIds(any(), any());
      verify(attackChainNodeRepository, never()).removeTeamsForAttackChainRun(any(), any());
      verify(lessonsCategoryRepository, never()).removeTeamsForAttackChainRun(any(), any());
    }
  }
}
