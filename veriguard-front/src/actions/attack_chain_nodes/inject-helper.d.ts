import {
  type AttackChainRun,
  type AttackChainNode,
  type AttackChainNodeExpectation, type AttackChainNodeExpectationAgentOutput,
  type AttackChainNodeTarget,
  type AttackChain,
  type Team,
} from '../../utils/api-types';

export interface AttackChainNodeHelper {
  getAttackChainNode: (injectId: AttackChainNode['node_id']) => AttackChainNode;
  getAttackChainNodesMap: () => Record<string, AttackChainNode>;

  getAttackChainRunAttackChainNodes: (exerciseId: AttackChainRun['attack_chain_run_id']) => AttackChainNode[];
  getAttackChainRunAttackChainNodeExpectations: (scenarioId: AttackChain['attack_chain_id']) => AttackChainNodeExpectation[];
  getTeamAttackChainRunAttackChainNodes: (teamId: Team['team_id']) => AttackChainNode[];

  getAttackChainAttackChainNodes: (scenarioId: AttackChain['attack_chain_id']) => AttackChainNode[];
  getTeamAttackChainAttackChainNodes: (teamId: Team['team_id']) => AttackChainNode[];

  getAttackChainNodeExpectationsByAsset: (targetId: AttackChainNodeTarget['target_id'], expectationType: string) => AttackChainNodeExpectationAgentOutput[];
}
