import { getReferential, postReferential, putReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchAttackChainRunEvaluations = (exerciseId, objectiveId) => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/objectives/${objectiveId}/evaluations`;
  return getReferential(schema.arrayOfEvaluations, uri)(dispatch);
};

export const updateAttackChainRunEvaluation = (exerciseId, objectiveId, evaluationId, data) => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/objectives/${objectiveId}/evaluations/${evaluationId}`;
  return putReferential(schema.evaluation, uri, data)(dispatch);
};

export const addAttackChainRunEvaluation = (exerciseId, objectiveId, data) => (dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/objectives/${objectiveId}/evaluations`;
  return postReferential(schema.evaluation, uri, data)(dispatch);
};

export const fetchAttackChainEvaluations = (scenarioId, objectiveId) => (dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/objectives/${objectiveId}/evaluations`;
  return getReferential(schema.arrayOfEvaluations, uri)(dispatch);
};

export const updateAttackChainEvaluation = (scenarioId, objectiveId, evaluationId, data) => (dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/objectives/${objectiveId}/evaluations/${evaluationId}`;
  return putReferential(schema.evaluation, uri, data)(dispatch);
};

export const addAttackChainEvaluation = (scenarioId, objectiveId, data) => (dispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/objectives/${objectiveId}/evaluations`;
  return postReferential(schema.evaluation, uri, data)(dispatch);
};
