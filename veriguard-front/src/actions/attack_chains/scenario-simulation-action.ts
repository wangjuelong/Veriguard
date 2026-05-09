import { simpleCall } from '../../utils/Action';
import type { AttackChain } from '../../utils/api-types';
import { SCENARIO_URI } from './attack_chain-actions';

// -- OPTION --

// eslint-disable-next-line import/prefer-default-export
export const searchAttackChainSimulationsAsOption = (scenarioId: AttackChain['attack_chain_id'], searchText: string = '') => {
  const params = { searchText };
  const uri = `${SCENARIO_URI}/${scenarioId}/attack_chain_runs/options`;
  return simpleCall(uri, { params });
};
