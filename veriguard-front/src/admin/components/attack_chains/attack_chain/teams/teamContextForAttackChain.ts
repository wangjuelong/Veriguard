import {
  addAttackChainTeamPlayers,
  disableAttackChainTeamPlayers,
  enableAttackChainTeamPlayers,
  fetchAttackChainTeams,
  removeAttackChainTeamPlayers,
} from '../../../../../actions/attack_chains/attack_chain-actions';
import { removeAttackChainTeams, replaceAttackChainTeams, searchAttackChainTeams } from '../../../../../actions/attack_chains/attack_chain-teams-action';
import { addTeam } from '../../../../../actions/teams/team-actions';
import { type Page } from '../../../../../components/common/queryable/Page';
import { type AttackChain, type AttackChainTeamUser, type SearchPaginationInput, type Team, type TeamCreateInput, type TeamOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { type UserStore } from '../../../teams/players/Player';

const teamContextForAttackChain = (scenarioId: AttackChain['attack_chain_id'], scenarioTeamsUsers: AttackChain['attack_chain_teams_users'], allUsersNumber = 0, allUsersEnabledNumber = 0) => {
  const dispatch = useAppDispatch();

  return {
    async onAddUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      await dispatch(addAttackChainTeamPlayers(scenarioId, teamId, { attack_chain_team_players: userIds }));
      return dispatch(fetchAttackChainTeams(scenarioId));
    },
    async onRemoveUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      await dispatch(removeAttackChainTeamPlayers(scenarioId, teamId, { attack_chain_team_players: userIds }));
      return dispatch(fetchAttackChainTeams(scenarioId));
    },
    onCreateTeam(team: TeamCreateInput): Promise<{ result: string }> {
      return dispatch(addTeam({
        ...team,
        team_scenarios: [scenarioId],
      }));
    },
    allUsersEnabledNumber: allUsersEnabledNumber,
    allUsersNumber: allUsersNumber,
    checkUserEnabled(teamId: Team['team_id'], userId: UserStore['user_id']): boolean {
      return (scenarioTeamsUsers ?? [])?.filter((o: AttackChainTeamUser) => o.attack_chain_id === scenarioId && o.team_id === teamId && userId === o.user_id).length > 0;
    },
    computeTeamUsersEnabled(teamId: Team['team_id']) {
      return scenarioTeamsUsers?.filter((o: AttackChainTeamUser) => o.team_id === teamId).length ?? 0;
    },
    onRemoveTeam(teamId: Team['team_id']): Promise<{
      result: string[];
      entities: { teams: Record<string, Team> };
    }> {
      return dispatch(removeAttackChainTeams(scenarioId, { attack_chain_teams: [teamId] }));
    },
    onReplaceTeam(teamIds: Team['team_id'][]): Promise<{
      result: string[];
      entities: { teams: Record<string, Team> };
    }> {
      return dispatch(replaceAttackChainTeams(scenarioId, { attack_chain_teams: teamIds }));
    },
    onToggleUser(teamId: Team['team_id'], userId: UserStore['user_id'], userEnabled: boolean): void {
      if (userEnabled) {
        dispatch(disableAttackChainTeamPlayers(scenarioId, teamId, { attack_chain_team_players: [userId] }));
      } else {
        dispatch(enableAttackChainTeamPlayers(scenarioId, teamId, { attack_chain_team_players: [userId] }));
      }
    },
    searchTeams(input: SearchPaginationInput, contextualOnly?: boolean): Promise<{ data: Page<TeamOutput> }> {
      return searchAttackChainTeams(scenarioId, input, contextualOnly);
    },
  };
};

export default teamContextForAttackChain;
