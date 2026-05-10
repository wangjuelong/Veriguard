import { type Dispatch } from 'redux';

import { putReferential, simplePostCall } from '../../utils/Action';
import { type AttackChain, type AttackChainUpdateTeamsInput, type SearchPaginationInput } from '../../utils/api-types';
import * as schema from '../Schema';
import { SCENARIO_URI } from './attack_chain-actions';

export const searchAttackChainTeams = (scenarioId: AttackChain['attack_chain_id'], paginationInput: SearchPaginationInput, contextualOnly: boolean = false) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/teams/search?contextualOnly=${contextualOnly}`;
  return simplePostCall(uri, paginationInput);
};

export const removeAttackChainTeams = (scenarioId: AttackChain['attack_chain_id'], data: AttackChainUpdateTeamsInput) => (dispatch: Dispatch) => putReferential(
  schema.arrayOfTeams,
  `${SCENARIO_URI}/${scenarioId}/teams/remove`,
  data,
)(dispatch);

export const replaceAttackChainTeams = (scenarioId: AttackChain['attack_chain_id'], data: AttackChainUpdateTeamsInput) => (dispatch: Dispatch) => putReferential(
  schema.arrayOfTeams,
  `${SCENARIO_URI}/${scenarioId}/teams/replace`,
  data,
)(dispatch);
