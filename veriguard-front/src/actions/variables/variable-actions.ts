import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential } from '../../utils/Action';
import { type AttackChain, type AttackChainRun, type Variable, type VariableInput } from '../../utils/api-types';
import * as schema from '../Schema';

// -- EXERCISES --

export const addVariableForAttackChainRun = (exerciseId: AttackChainRun['attack_chain_run_id'], data: VariableInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/variables`;
  return postReferential(schema.variable, uri, data)(dispatch);
};

export const updateVariableForAttackChainRun = (
  exerciseId: AttackChainRun['attack_chain_run_id'],
  variableId: Variable['variable_id'],
  data: VariableInput,
) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/variables/${variableId}`;
  return putReferential(schema.variable, uri, data)(dispatch);
};

export const deleteVariableForAttackChainRun = (exerciseId: AttackChainRun['attack_chain_run_id'], variableId: Variable['variable_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/variables/${variableId}`;
  return delReferential(uri, 'variables', variableId)(dispatch);
};

export const fetchVariablesForAttackChainRun = (exerciseId: AttackChainRun['attack_chain_run_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/variables`;
  return getReferential(schema.arrayOfVariables, uri)(dispatch);
};

// -- SCENARIOS --

export const addVariableForAttackChain = (scenarioId: AttackChain['attack_chain_id'], data: VariableInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/variables`;
  return postReferential(schema.variable, uri, data)(dispatch);
};

export const updateVariableForAttackChain = (
  scenarioId: AttackChain['attack_chain_id'],
  variableId: Variable['variable_id'],
  data: VariableInput,
) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/variables/${variableId}`;
  return putReferential(schema.variable, uri, data)(dispatch);
};

export const deleteVariableForAttackChain = (scenarioId: AttackChain['attack_chain_id'], variableId: Variable['variable_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/variables/${variableId}`;
  return delReferential(uri, 'variables', variableId)(dispatch);
};

export const fetchVariablesForAttackChain = (scenarioId: AttackChain['attack_chain_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/variables`;
  return getReferential(schema.arrayOfVariables, uri)(dispatch);
};
