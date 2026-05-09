import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential } from '../../utils/Action';
import { type AttackChainRun, type Report, type ReportAttackChainNodeComment, type ReportInput } from '../../utils/api-types';
import * as schema from '../Schema';

export const fetchReportsForAttackChainRun = (exerciseId: AttackChainRun['attack_chain_run_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/reports`;
  return getReferential(schema.arrayOfReports, uri)(dispatch);
};

export const fetchReport = (reportId: Report['report_id']) => (dispatch: Dispatch) => {
  const uri = `/api/reports/${reportId}`;
  return getReferential(schema.report, uri)(dispatch);
};

export const fetchReportFromSimulation = (exerciseId: AttackChainRun['attack_chain_run_id'], reportId: Report['report_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/reports/${reportId}`;
  return getReferential(schema.report, uri)(dispatch);
};

export const addReportForAttackChainRun = (exerciseId: AttackChainRun['attack_chain_run_id'], data: ReportInput) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/reports`;
  return postReferential(schema.report, uri, data)(dispatch);
};

export const updateReportForAttackChainRun = (
  exerciseId: AttackChainRun['attack_chain_run_id'],
  reportId: Report['report_id'],
  data: ReportInput,
) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/reports/${reportId}`;
  return putReferential(schema.report, uri, data)(dispatch);
};

export const updateReportAttackChainNodeCommentForAttackChainRun = (
  exerciseId: AttackChainRun['attack_chain_run_id'],
  reportId: Report['report_id'],
  data: ReportAttackChainNodeComment,
) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/reports/${reportId}/node-comments`;
  return putReferential(schema.report, uri, data)(dispatch);
};

export const deleteReportForAttackChainRun = (exerciseId: AttackChainRun['attack_chain_run_id'], reportId: Report['report_id']) => (dispatch: Dispatch) => {
  const uri = `/api/attack_chain_runs/${exerciseId}/reports/${reportId}`;
  return delReferential(uri, 'reports', reportId)(dispatch);
};
