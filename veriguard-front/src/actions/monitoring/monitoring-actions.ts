import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';

// ============================================================
// PR C4 边界策略常态化监控 actions —— 招标 §3.2
// 所有 wire 字段保持后端 snake_case 一致.
// ============================================================

const MONITORING_URI = '/api/monitoring';
const JOBS_URI = `${MONITORING_URI}/jobs`;

export type MonitoringRunStatus = 'triggered' | 'completed' | 'failed';
export type MonitoringAggregation = 'hour' | 'day';

// ===================== Job =====================

export interface MonitoringJobOutput {
  monitoring_job_id: string;
  monitoring_job_name: string;
  monitoring_job_baseline_id: string;
  monitoring_job_cron_expression: string;
  monitoring_job_enabled: boolean;
  monitoring_job_description: string | null;
  monitoring_job_last_triggered_at: string | null;
  monitoring_job_next_fire_at: string | null;
  monitoring_job_created_at: string;
  monitoring_job_updated_at: string;
}

export interface MonitoringJobPageOutput {
  content: MonitoringJobOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface CreateMonitoringJobRequest {
  name: string;
  baseline_id: string;
  cron_expression: string;
  enabled?: boolean;
  description?: string;
}

// ===================== History =====================

export interface MonitoringRunHistoryOutput {
  monitoring_run_history_id: string;
  monitoring_run_history_job_id: string;
  monitoring_run_history_coverage_run_id: string | null;
  monitoring_run_history_scheduled_at: string;
  monitoring_run_history_finished_at: string | null;
  monitoring_run_history_status: MonitoringRunStatus;
  monitoring_run_history_hit_count: number | null;
  monitoring_run_history_miss_count: number | null;
  monitoring_run_history_timeout_count: number | null;
  monitoring_run_history_out_of_scope_count: number | null;
  monitoring_run_history_total_count: number | null;
  monitoring_run_history_hit_rate: number | null;
  monitoring_run_history_error_message: string | null;
  monitoring_run_history_created_at: string;
}

export interface MonitoringHistoryPageOutput {
  content: MonitoringRunHistoryOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface MonitoringTriggerOutput {
  monitoring_run_history_id: string;
  monitoring_run_history_status: MonitoringRunStatus;
  monitoring_run_history_coverage_run_id: string | null;
}

// ===================== Trend =====================

export interface MonitoringTrendBucket {
  bucket: string;
  avg_hit_rate: number;
  hit_sum: number;
  miss_sum: number;
  timeout_sum: number;
  out_of_scope_sum: number;
  total_sum: number;
  run_count: number;
}

export interface MonitoringTrendOutput {
  monitoring_job_id: string;
  aggregation: MonitoringAggregation;
  buckets: MonitoringTrendBucket[];
}

// ===================== Helpers =====================

const buildQueryString = (
  params: Record<string, string | number | boolean | undefined | null>,
): string => {
  const entries = Object.entries(params).filter(
    ([, v]) => v !== undefined && v !== null && v !== '',
  );
  if (entries.length === 0) {
    return '';
  }
  const query = entries
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&');
  return `?${query}`;
};

// ===================== Job calls =====================

export const fetchMonitoringJobs = async (
  query: {
    enabled?: boolean;
    page?: number;
    size?: number;
  } = {},
): Promise<MonitoringJobPageOutput> => {
  const qs = buildQueryString({
    enabled: query.enabled,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${JOBS_URI}${qs}`);
  return response.data as MonitoringJobPageOutput;
};

export const fetchMonitoringJob = async (id: string): Promise<MonitoringJobOutput> => {
  const response = await simpleCall(`${JOBS_URI}/${encodeURIComponent(id)}`);
  return response.data as MonitoringJobOutput;
};

export const createMonitoringJob = async (
  request: CreateMonitoringJobRequest,
): Promise<MonitoringJobOutput> => {
  const response = await simplePostCall(JOBS_URI, request);
  return response.data as MonitoringJobOutput;
};

export const updateMonitoringJob = async (
  id: string,
  request: CreateMonitoringJobRequest,
): Promise<MonitoringJobOutput> => {
  const response = await simplePutCall(`${JOBS_URI}/${encodeURIComponent(id)}`, request);
  return response.data as MonitoringJobOutput;
};

export const deleteMonitoringJob = async (id: string): Promise<void> => {
  await simpleDelCall(`${JOBS_URI}/${encodeURIComponent(id)}`);
};

export const enableMonitoringJob = async (id: string): Promise<MonitoringJobOutput> => {
  const response = await simplePostCall(`${JOBS_URI}/${encodeURIComponent(id)}/enable`);
  return response.data as MonitoringJobOutput;
};

export const disableMonitoringJob = async (id: string): Promise<MonitoringJobOutput> => {
  const response = await simplePostCall(`${JOBS_URI}/${encodeURIComponent(id)}/disable`);
  return response.data as MonitoringJobOutput;
};

export const triggerMonitoringJobNow = async (
  id: string,
): Promise<MonitoringTriggerOutput> => {
  const response = await simplePostCall(`${JOBS_URI}/${encodeURIComponent(id)}/trigger-now`);
  return response.data as MonitoringTriggerOutput;
};

// ===================== History calls =====================

export const fetchMonitoringHistory = async (
  jobId: string,
  query: {
    from?: string;
    to?: string;
    status?: MonitoringRunStatus;
    page?: number;
    size?: number;
  } = {},
): Promise<MonitoringHistoryPageOutput> => {
  const qs = buildQueryString({
    from: query.from,
    to: query.to,
    status: query.status,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(
    `${JOBS_URI}/${encodeURIComponent(jobId)}/history${qs}`,
  );
  return response.data as MonitoringHistoryPageOutput;
};

// ===================== Trend calls =====================

export const fetchMonitoringTrend = async (
  jobId: string,
  query: {
    aggregation: MonitoringAggregation;
    from: string;
    to: string;
  },
): Promise<MonitoringTrendOutput> => {
  const qs = buildQueryString(query);
  const response = await simpleCall(`${JOBS_URI}/${encodeURIComponent(jobId)}/trend${qs}`);
  return response.data as MonitoringTrendOutput;
};
