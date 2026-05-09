import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import type {
  CreateAttackChainRunInput,
  AttackChainRunTeamPlayersEnableInput,
  AttackChainRunUpdateStartDateInput,
  AttackChainRunUpdateStatusInput,
  AttackChainRunUpdateTagsInput,
  ExpectationUpdateInput,
  LessonsInput,
  SearchPaginationInput,
  UpdateAttackChainRunInput,
} from '../utils/api-types';
import * as schema from './Schema';

type AppDispatch = Dispatch;

export const fetchAttackChainRuns = () => (dispatch: AppDispatch) => getReferential(schema.arrayOfAttackChainRuns, '/api/attack_chain_runs')(dispatch);

export const fetchAttackChainRunsById = (exerciseIds: string[]) => (dispatch: AppDispatch) => postReferential(schema.arrayOfAttackChainRuns, '/api/attack_chain_runs/search-by-id', exerciseIds, undefined, false)(dispatch);

export const searchAttackChainRuns = (paginationInput: SearchPaginationInput) => simplePostCall('/api/attack_chain_runs/search', paginationInput);

export const fetchAttackChainRun = (exerciseId: string) => (dispatch: AppDispatch) => getReferential(schema.attack_chain_run, `/api/attack_chain_runs/${exerciseId}`)(dispatch);

export const fetchAttackChainRunAttackChainNodeExpectations = (exerciseId: string) => (dispatch: AppDispatch) => getReferential(
  schema.arrayOfInjectexpectations,
  `/api/attack_chain_runs/${exerciseId}/expectations`,
)(dispatch);

export const addAttackChainRun = (data: CreateAttackChainRunInput) => (dispatch: AppDispatch) => postReferential(schema.attack_chain_run, '/api/attack_chain_runs', data)(dispatch);

export const duplicateAttackChainRun = (exerciseId: string) => (dispatch: AppDispatch) => postReferential(schema.attack_chain_run, `/api/attack_chain_runs/${exerciseId}`, null)(dispatch);

export const updateAttackChainRun = (exerciseId: string, data: UpdateAttackChainRunInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}`,
  data,
)(dispatch);

export const updateAttackChainRunStartDate = (exerciseId: string, data: AttackChainRunUpdateStartDateInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/start-date`,
  data,
)(dispatch);

export const updateAttackChainRunLessons = (exerciseId: string, data: LessonsInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/lessons`,
  data,
)(dispatch);

export const fetchAttackChainRunTeams = (exerciseId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const enableAttackChainRunTeamPlayers = (exerciseId: string, teamId: string, data: AttackChainRunTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/enable`,
  data,
)(dispatch);

export const disableAttackChainRunTeamPlayers = (exerciseId: string, teamId: string, data: AttackChainRunTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/disable`,
  data,
)(dispatch);

export const addAttackChainRunTeamPlayers = (exerciseId: string, teamId: string, data: AttackChainRunTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/add`,
  data,
)(dispatch);

export const removeAttackChainRunTeamPlayers = (exerciseId: string, teamId: string, data: AttackChainRunTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/remove`,
  data,
)(dispatch);

export const updateAttackChainRunTags = (exerciseId: string, data: AttackChainRunUpdateTagsInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/tags`,
  data,
)(dispatch);

export const updateAttackChainRunStatus = (exerciseId: string, status: AttackChainRunUpdateStatusInput) => (dispatch: AppDispatch) => putReferential(
  schema.attack_chain_run,
  `/api/attack_chain_runs/${exerciseId}/status`,
  status,
)(dispatch);

export const updateAttackChainNodeExpectation = (injectExpectationId: string, data: ExpectationUpdateInput) => (dispatch: AppDispatch) => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}`,
  data,
)(dispatch);

export const deleteAttackChainNodeExpectationResult = (injectExpectationId: string, sourceId: string) => (dispatch: AppDispatch) => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}/${sourceId}/delete`,
  {},
)(dispatch);

export const deleteAttackChainRun = (exerciseId: string) => (dispatch: AppDispatch) => delReferential(
  `/api/attack_chain_runs/${exerciseId}`,
  'attack_chain_runs',
  exerciseId,
)(dispatch);

export const importingAttackChainRun = (data: FormData) => (dispatch: AppDispatch) => {
  const uri = '/api/attack_chain_runs/import';
  return postReferential(null, uri, data)(dispatch);
};

export const fetchPlayerAttackChainRun = (exerciseId: string, userId: string | null) => (dispatch: AppDispatch) => {
  const uri = `/api/player/attack_chain_runs/${exerciseId}${userId ? `?userId=${userId}` : ''}`;
  return getReferential(schema.attack_chain_run, uri)(dispatch);
};
