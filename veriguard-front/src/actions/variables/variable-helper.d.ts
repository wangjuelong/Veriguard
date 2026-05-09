import { type AttackChainRun, type AttackChain, type Variable } from '../../utils/api-types';

export interface VariablesHelper {
  getAttackChainRunVariables: (exerciseId: AttackChainRun['attack_chain_run_id']) => Variable[];
  getAttackChainVariables: (scenarioId: AttackChain['attack_chain_id']) => Variable[];
}
