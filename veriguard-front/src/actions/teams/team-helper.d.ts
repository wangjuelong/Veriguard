import { type AttackChainRun, type AttackChain, type Team, type User } from '../../utils/api-types';

export interface TeamsHelper {
  getAttackChainRunTeams: (exerciseId: AttackChainRun['attack_chain_run_id']) => Team[];
  getAttackChainTeams: (scenarioId: AttackChain['attack_chain_id']) => Team[];
  getTeam: (teamId: Team['team_id']) => Team;
  getTeams: () => Team[];
  getTeamsMap: () => Record<string, Team>;
  getTeamUsers: (teamId: Team['team_id']) => User[];
}
