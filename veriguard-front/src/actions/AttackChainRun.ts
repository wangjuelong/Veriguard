import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import type {
  CreateExerciseInput,
  ExerciseTeamPlayersEnableInput,
  ExerciseUpdateStartDateInput,
  ExerciseUpdateStatusInput,
  ExerciseUpdateTagsInput,
  ExpectationUpdateInput,
  LessonsInput,
  SearchPaginationInput,
  UpdateExerciseInput,
} from '../utils/api-types';
import * as schema from './Schema';

type AppDispatch = Dispatch;

export const fetchExercises = () => (dispatch: AppDispatch) => getReferential(schema.arrayOfExercises, '/api/attack_chain_runs')(dispatch);

export const fetchExercisesById = (exerciseIds: string[]) => (dispatch: AppDispatch) => postReferential(schema.arrayOfExercises, '/api/attack_chain_runs/search-by-id', exerciseIds, undefined, false)(dispatch);

export const searchExercises = (paginationInput: SearchPaginationInput) => simplePostCall('/api/attack_chain_runs/search', paginationInput);

export const fetchExercise = (exerciseId: string) => (dispatch: AppDispatch) => getReferential(schema.exercise, `/api/attack_chain_runs/${exerciseId}`)(dispatch);

export const fetchExerciseInjectExpectations = (exerciseId: string) => (dispatch: AppDispatch) => getReferential(
  schema.arrayOfInjectexpectations,
  `/api/attack_chain_runs/${exerciseId}/expectations`,
)(dispatch);

export const addExercise = (data: CreateExerciseInput) => (dispatch: AppDispatch) => postReferential(schema.exercise, '/api/attack_chain_runs', data)(dispatch);

export const duplicateExercise = (exerciseId: string) => (dispatch: AppDispatch) => postReferential(schema.exercise, `/api/attack_chain_runs/${exerciseId}`, null)(dispatch);

export const updateExercise = (exerciseId: string, data: UpdateExerciseInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}`,
  data,
)(dispatch);

export const updateExerciseStartDate = (exerciseId: string, data: ExerciseUpdateStartDateInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/start-date`,
  data,
)(dispatch);

export const updateExerciseLessons = (exerciseId: string, data: LessonsInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/lessons`,
  data,
)(dispatch);

export const fetchExerciseTeams = (exerciseId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const enableExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/enable`,
  data,
)(dispatch);

export const disableExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/disable`,
  data,
)(dispatch);

export const addExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/add`,
  data,
)(dispatch);

export const removeExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/teams/${teamId}/players/remove`,
  data,
)(dispatch);

export const updateExerciseTags = (exerciseId: string, data: ExerciseUpdateTagsInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/tags`,
  data,
)(dispatch);

export const updateExerciseStatus = (exerciseId: string, status: ExerciseUpdateStatusInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/attack_chain_runs/${exerciseId}/status`,
  status,
)(dispatch);

export const updateInjectExpectation = (injectExpectationId: string, data: ExpectationUpdateInput) => (dispatch: AppDispatch) => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}`,
  data,
)(dispatch);

export const deleteInjectExpectationResult = (injectExpectationId: string, sourceId: string) => (dispatch: AppDispatch) => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}/${sourceId}/delete`,
  {},
)(dispatch);

export const deleteExercise = (exerciseId: string) => (dispatch: AppDispatch) => delReferential(
  `/api/attack_chain_runs/${exerciseId}`,
  'exercises',
  exerciseId,
)(dispatch);

export const importingExercise = (data: FormData) => (dispatch: AppDispatch) => {
  const uri = '/api/attack_chain_runs/import';
  return postReferential(null, uri, data)(dispatch);
};

export const fetchPlayerExercise = (exerciseId: string, userId: string | null) => (dispatch: AppDispatch) => {
  const uri = `/api/player/exercises/${exerciseId}${userId ? `?userId=${userId}` : ''}`;
  return getReferential(schema.exercise, uri)(dispatch);
};
