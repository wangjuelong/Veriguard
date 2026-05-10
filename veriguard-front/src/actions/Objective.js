import { delReferential, getReferential, postReferential, putReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchAttackChainRunObjectives = exerciseId => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/objectives`;
  return getReferential(schema.arrayOfObjectives, uri)(dispatch);
};

export const updateAttackChainRunObjective = (exerciseId, objectiveId, data) => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/objectives/${objectiveId}`;
  return putReferential(schema.objective, uri, data)(dispatch);
};

export const addAttackChainRunObjective = (exerciseId, data) => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/objectives`;
  return postReferential(schema.objective, uri, data)(dispatch);
};

export const deleteAttackChainRunObjective = (exerciseId, objectiveId) => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/objectives/${objectiveId}`;
  return delReferential(uri, 'objectives', objectiveId)(dispatch);
};

export const fetchAttackChainObjectives = scenarioId => (dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/objectives`;
  return getReferential(schema.arrayOfObjectives, uri)(dispatch);
};

export const updateAttackChainObjective = (scenarioId, objectiveId, data) => (dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/objectives/${objectiveId}`;
  return putReferential(schema.objective, uri, data)(dispatch);
};

export const addAttackChainObjective = (scenarioId, data) => (dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/objectives`;
  return postReferential(schema.objective, uri, data)(dispatch);
};

export const deleteAttackChainObjective = (scenarioId, objectiveId) => (dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/objectives/${objectiveId}`;
  return delReferential(uri, 'objectives', objectiveId)(dispatch);
};
