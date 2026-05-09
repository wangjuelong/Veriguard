import { type Dispatch } from 'redux';

import {
  bulkDeleteReferential,
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleDelCall,
  simplePutCall,
} from '../utils/Action';
import type { AttackChainNode, AttackChainNodeBulkProcessingInput, AttackChainNodeBulkUpdateInputs, AttackChainNodeInput, AttackChainNodeUpdateActivationInput } from '../utils/api-types';
import * as schema from './Schema';

type AppDispatch = Dispatch;

// -- INJECTS --

export const fetchAttackChainNode = (injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_nodes/${injectId}`;
  return getReferential(schema.node, uri)(dispatch);
};

export const bulkDeleteAttackChainNodes = (data: AttackChainNodeBulkProcessingInput) => (dispatch: AppDispatch) => {
  const uri = '/api/attack_chain_nodes';
  return bulkDeleteReferential(uri, 'nodes', data)(dispatch);
};

export const bulkDeleteAttackChainNodesSimple = (data: AttackChainNodeBulkProcessingInput) => {
  const uri = '/api/attack_chain_nodes';
  return simpleDelCall(uri, { data });
};

export const bulkUpdateAttackChainNode = (data: AttackChainNodeBulkUpdateInputs) => (dispatch: AppDispatch) => {
  const uri = '/api/attack_chain_nodes';
  return putReferential(schema.node, uri, data)(dispatch);
};

export const bulkUpdateAttackChainNodeSimple = (data: AttackChainNodeBulkUpdateInputs) => {
  const uri = '/api/attack_chain_nodes';
  return simplePutCall(uri, data);
};

// -- EXERCISES --

export const fetchAttackChainRunAttackChainNodes = (exerciseId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes`;
  return getReferential(schema.arrayOfAttackChainNodes, uri)(dispatch);
};

export const fetchAttackChainNodeTeams = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/${injectId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const updateAttackChainNodeForAttackChainRun = (exerciseId: string, injectId: string, data: AttackChainNode | AttackChainNodeInput) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_nodes/${exerciseId}/${injectId}`;
  return putReferential(schema.node, uri, data)(dispatch);
};

export const updateAttackChainNodeTriggerForAttackChainRun = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/${injectId}/trigger`;
  return putReferential(schema.node, uri, {})(dispatch);
};

export const updateAttackChainNodeActivationForAttackChainRun = (exerciseId: string, injectId: string, data: AttackChainNodeUpdateActivationInput) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/${injectId}/activation`;
  return putReferential(schema.node, uri, data)(dispatch);
};

export const addAttackChainNodeForAttackChainRun = (exerciseId: string, data: AttackChainNode | AttackChainNodeInput) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes`;
  return postReferential(schema.node, uri, data)(dispatch);
};

export const duplicateAttackChainNodeForAttackChainRun = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/${injectId}`;
  return postReferential(schema.node, uri, null)(dispatch);
};

export const deleteAttackChainNodeForAttackChainRun = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/${injectId}`;
  return delReferential(uri, 'nodes', injectId)(dispatch);
};

export const executeAttackChainNode = (exerciseId: string, values: AttackChainNodeInput, files: File[] | null) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/node`;
  const formData = new FormData();
  formData.append('file', files && files.length > 0 ? files[0] : '');
  const blob = new Blob([JSON.stringify(values)], { type: 'application/json' });
  formData.append('input', blob);
  return postReferential(schema.injectStatus, uri, formData)(dispatch);
};

export const injectDone = (exerciseId: string, injectId: string) => (dispatch: AppDispatch) => {
  const data = {
    status: 'SUCCESS',
    message: 'Manual validation',
  };
  const uri = `/api/attack_chain_runs/${exerciseId}/nodes/${injectId}/status`;
  return postReferential(schema.node, uri, data)(dispatch);
};

// -- SCENARIOS --

export const addAttackChainNodeForAttackChain = (scenarioId: string, data: AttackChainNode | AttackChainNodeInput) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes`;
  return postReferential(schema.node, uri, data)(dispatch);
};

export const duplicateAttackChainNodeForAttackChain = (scenarioId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/${injectId}`;
  return postReferential(schema.node, uri, null)(dispatch);
};

export const fetchAttackChainAttackChainNodes = (scenarioId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes`;
  return getReferential(schema.arrayOfAttackChainNodes, uri)(dispatch);
};

export const updateAttackChainNodeForAttackChain = (scenarioId: string, injectId: string, data: AttackChainNode | AttackChainNodeInput) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/${injectId}`;
  return putReferential(schema.node, uri, data)(dispatch);
};

export const updateAttackChainNodeActivationForAttackChain = (scenarioId: string, injectId: string, data: AttackChainNodeUpdateActivationInput) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/${injectId}/activation`;
  return putReferential(schema.node, uri, data)(dispatch);
};

export const deleteAttackChainNodeAttackChain = (scenarioId: string, injectId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chains/${scenarioId}/nodes/${injectId}`;
  return delReferential(uri, 'nodes', injectId)(dispatch);
};
