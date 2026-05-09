import { removeAttackChainRunTeams, replaceAttackChainRunTeams, searchAttackChainRunTeams } from '../../../../../actions/attack_chain_runs/attack_chain_run-teams-action';
import { addAttackChainRunTeamPlayers, disableAttackChainRunTeamPlayers, enableAttackChainRunTeamPlayers, fetchAttackChainRunTeams, removeAttackChainRunTeamPlayers } from '../../../../../actions/AttackChainRun';
import { addTeam } from '../../../../../actions/teams/team-actions';
import { type Page } from '../../../../../components/common/queryable/Page';
import { type AttackChainRun, type AttackChainRunTeamUser, type SearchPaginationInput, type Team, type TeamCreateInput, type TeamOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { type TeamContextType } from '../../../common/Context';
import { type UserStore } from '../../../teams/players/Player';

const teamContextForAttackChainRun = (exerciseId: AttackChainRun['attack_chain_run_id'], exerciseTeamsUsers: AttackChainRun['attack_chain_run_teams_users'], allUsersNumber = 0, allUsersEnabledNumber = 0): TeamContextType => {
  const dispatch = useAppDispatch();

  return {
    async onAddUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      await dispatch(addAttackChainRunTeamPlayers(exerciseId, teamId, { attack_chain_run_team_players: userIds }));
      return dispatch(fetchAttackChainRunTeams(exerciseId));
    },
    async onRemoveUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      await dispatch(removeAttackChainRunTeamPlayers(exerciseId, teamId, { attack_chain_run_team_players: userIds }));
      return dispatch(fetchAttackChainRunTeams(exerciseId));
    },
    onCreateTeam(team: TeamCreateInput): Promise<{ result: string }> {
      return dispatch(addTeam({
        ...team,
        team_attack_chain_runs: [exerciseId],
      }));
    },
    allUsersEnabledNumber: allUsersEnabledNumber,
    allUsersNumber: allUsersNumber,
    checkUserEnabled(teamId: Team['team_id'], userId: UserStore['user_id']): boolean {
      return (exerciseTeamsUsers ?? []).filter((o: AttackChainRunTeamUser) => o.attack_chain_run_id === exerciseId && o.team_id === teamId && userId === o.user_id).length > 0;
    },
    computeTeamUsersEnabled(teamId: Team['team_id']) {
      return (exerciseTeamsUsers ?? []).filter((o: AttackChainRunTeamUser) => o.team_id === teamId).length;
    },
    onRemoveTeam(teamId: Team['team_id']): Promise<{
      result: string[];
      entities: { teams: Record<string, Team> };
    }> {
      return dispatch(removeAttackChainRunTeams(exerciseId, { attack_chain_run_teams: [teamId] }));
    },
    onReplaceTeam(teamIds: Team['team_id'][]): Promise<{
      result: string[];
      entities: { teams: Record<string, Team> };
    }> {
      return dispatch(replaceAttackChainRunTeams(exerciseId, { attack_chain_run_teams: teamIds }));
    },
    onToggleUser(teamId: Team['team_id'], userId: UserStore['user_id'], userEnabled: boolean): void {
      if (userEnabled) {
        dispatch(disableAttackChainRunTeamPlayers(exerciseId, teamId, { attack_chain_run_team_players: [userId] }));
      } else {
        dispatch(enableAttackChainRunTeamPlayers(exerciseId, teamId, { attack_chain_run_team_players: [userId] }));
      }
    },
    searchTeams(input: SearchPaginationInput, contextualOnly?: boolean): Promise<{ data: Page<TeamOutput> }> {
      return searchAttackChainRunTeams(exerciseId, input, contextualOnly);
    },
  };
};

export default teamContextForAttackChainRun;
