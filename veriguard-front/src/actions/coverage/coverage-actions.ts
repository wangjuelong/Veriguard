import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';

// ============================================================
// PR C3 边界覆盖度子模块 actions —— 招标 §3.1 / §4.1
// 所有 wire 字段保持后端 snake_case 一致.
// ============================================================

const COVERAGE_URI = '/api/coverage';
const BASELINES_URI = `${COVERAGE_URI}/baselines`;
const RUNS_URI = `${COVERAGE_URI}/runs`;
const POLICIES_URI = `${COVERAGE_URI}/policies`;

export type CoverageType = 'boundary' | 'traffic';
export type CoverageRunStatus
  = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
export type CoverageHitState = 'hit' | 'miss' | 'timeout' | 'out_of_scope';
export type PolicyDeviceType = 'waf' | 'ips' | 'ids' | 'nta' | 'hids';
export type CoverageChangeType
  = | 'unchanged'
    | 'new_hit'
    | 'new_miss'
    | 'dropped_hit'
    | 'dropped_miss'
    | 'state_changed';

// ===================== Baseline =====================

export interface CoverageBaselineOutput {
  coverage_baseline_id: string;
  coverage_baseline_name: string;
  coverage_baseline_coverage_type: CoverageType;
  coverage_baseline_case_ids: string[];
  coverage_baseline_asset_group_id: string;
  coverage_baseline_description: string | null;
  coverage_baseline_soc_query_delay_seconds: number;
  coverage_baseline_created_at: string;
  coverage_baseline_updated_at: string;
}

export interface CoverageBaselinePageOutput {
  content: CoverageBaselineOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface CreateBaselineRequest {
  name: string;
  coverage_type: CoverageType;
  case_ids: string[];
  asset_group_id: string;
  description?: string;
  soc_query_delay_seconds?: number;
}

// ===================== Run =====================

export interface CoverageRunOutput {
  coverage_run_id: string;
  coverage_run_baseline_id: string;
  coverage_run_status: CoverageRunStatus;
  coverage_run_total_cells: number;
  coverage_run_hit_count: number;
  coverage_run_miss_count: number;
  coverage_run_timeout_count: number;
  coverage_run_out_of_scope_count: number;
  coverage_run_progress_percent: number;
  coverage_run_hit_state_counts: Record<string, number>;
  coverage_run_started_at: string | null;
  coverage_run_finished_at: string | null;
  coverage_run_error_message: string | null;
  coverage_run_created_at: string;
  coverage_run_updated_at: string;
}

export interface CoverageRunPageOutput {
  content: CoverageRunOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface CoverageRunCreateOutput {
  coverage_run_id: string;
  coverage_run_status: CoverageRunStatus;
}

// ===================== Matrix Cell =====================

export interface CoverageCellOutput {
  coverage_result_id: string;
  coverage_result_run_id: string;
  coverage_result_asset_id: string;
  coverage_result_policy_id: string;
  coverage_result_case_id: string | null;
  coverage_result_hit_state: CoverageHitState;
  coverage_result_alert_rule_id: string | null;
  coverage_result_observed_at: string | null;
  coverage_result_error_message: string | null;
  coverage_result_created_at: string;
  coverage_result_updated_at: string;
}

export interface CoverageMatrixPageOutput {
  content: CoverageCellOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

// ===================== Diff =====================

export interface CoverageCellDeltaOutput {
  asset_id: string;
  policy_id: string;
  old_state: CoverageHitState | null;
  new_state: CoverageHitState | null;
  change_type: CoverageChangeType;
}

export interface CoverageDiffSummaryOutput {
  unchanged: number;
  new_hit: number;
  new_miss: number;
  dropped_hit: number;
  dropped_miss: number;
  state_changed: number;
}

export interface CoverageDiffOutput {
  run_id_a: string;
  run_id_b: string;
  deltas: CoverageCellDeltaOutput[];
  summary: CoverageDiffSummaryOutput;
}

// ===================== Policy =====================

export interface PolicyOutput {
  policy_id: string;
  policy_name: string;
  policy_device_type: PolicyDeviceType;
  policy_device_id: string | null;
  policy_external_rule_id: string | null;
  policy_description: string | null;
  policy_created_at: string;
  policy_updated_at: string;
}

export interface PolicyPageOutput {
  content: PolicyOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface CreatePolicyRequest {
  name: string;
  device_type: PolicyDeviceType;
  device_id?: string;
  external_rule_id?: string;
  description?: string;
}

// ===================== Helpers =====================

const buildQueryString = (
  params: Record<string, string | number | undefined | null>,
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

// ===================== Baseline calls =====================

export const fetchCoverageBaselines = async (
  query: {
    coverage_type?: CoverageType;
    page?: number;
    size?: number;
  } = {},
): Promise<CoverageBaselinePageOutput> => {
  const qs = buildQueryString({
    coverage_type: query.coverage_type,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${BASELINES_URI}${qs}`);
  return response.data as CoverageBaselinePageOutput;
};

export const fetchCoverageBaseline = async (
  id: string,
): Promise<CoverageBaselineOutput> => {
  const response = await simpleCall(`${BASELINES_URI}/${encodeURIComponent(id)}`);
  return response.data as CoverageBaselineOutput;
};

export const createCoverageBaseline = async (
  request: CreateBaselineRequest,
): Promise<CoverageBaselineOutput> => {
  const response = await simplePostCall(BASELINES_URI, request);
  return response.data as CoverageBaselineOutput;
};

export const updateCoverageBaseline = async (
  id: string,
  request: CreateBaselineRequest,
): Promise<CoverageBaselineOutput> => {
  const response = await simplePutCall(`${BASELINES_URI}/${encodeURIComponent(id)}`, request);
  return response.data as CoverageBaselineOutput;
};

export const deleteCoverageBaseline = async (id: string): Promise<void> => {
  await simpleDelCall(`${BASELINES_URI}/${encodeURIComponent(id)}`);
};

export const triggerCoverageRun = async (
  baselineId: string,
): Promise<CoverageRunCreateOutput> => {
  const response = await simplePostCall(
    `${BASELINES_URI}/${encodeURIComponent(baselineId)}/run`,
  );
  return response.data as CoverageRunCreateOutput;
};

// ===================== Run calls =====================

export const fetchCoverageRuns = async (
  query: {
    baseline_id?: string;
    status?: CoverageRunStatus;
    page?: number;
    size?: number;
  } = {},
): Promise<CoverageRunPageOutput> => {
  const qs = buildQueryString({
    baseline_id: query.baseline_id,
    status: query.status,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${RUNS_URI}${qs}`);
  return response.data as CoverageRunPageOutput;
};

export const fetchCoverageRun = async (id: string): Promise<CoverageRunOutput> => {
  const response = await simpleCall(`${RUNS_URI}/${encodeURIComponent(id)}`);
  return response.data as CoverageRunOutput;
};

export const fetchCoverageMatrix = async (
  runId: string,
  query: {
    hit_state?: CoverageHitState;
    page?: number;
    size?: number;
  } = {},
): Promise<CoverageMatrixPageOutput> => {
  const qs = buildQueryString({
    hit_state: query.hit_state,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(
    `${RUNS_URI}/${encodeURIComponent(runId)}/matrix${qs}`,
  );
  return response.data as CoverageMatrixPageOutput;
};

export const fetchCoverageDiff = async (
  runId: string,
  compareWithRunId: string,
): Promise<CoverageDiffOutput> => {
  const qs = buildQueryString({ compare_with: compareWithRunId });
  const response = await simpleCall(`${RUNS_URI}/${encodeURIComponent(runId)}/diff${qs}`);
  return response.data as CoverageDiffOutput;
};

// ===================== Policy calls =====================

export const fetchPolicies = async (
  query: {
    device_type?: PolicyDeviceType;
    page?: number;
    size?: number;
  } = {},
): Promise<PolicyPageOutput> => {
  const qs = buildQueryString({
    device_type: query.device_type,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${POLICIES_URI}${qs}`);
  return response.data as PolicyPageOutput;
};

export const createPolicy = async (
  request: CreatePolicyRequest,
): Promise<PolicyOutput> => {
  const response = await simplePostCall(POLICIES_URI, request);
  return response.data as PolicyOutput;
};
