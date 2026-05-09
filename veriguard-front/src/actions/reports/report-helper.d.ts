import { type AttackChainRun, type Report } from '../../utils/api-types';

export interface ReportsHelper {
  getAttackChainRunReports: (exerciseId: AttackChainRun['attack_chain_run_id']) => Report[];
  getReport: (reportId: Report['report_id']) => Report;
}
