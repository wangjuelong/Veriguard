import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';

// ============================================================
// IPv6 安全验证系统 §3.6 ★2 攻击组合 —— PR D5 actions 层
// 所有 wire 字段保持后端 snake_case 一致.
// ============================================================

const COMBINATION_URI = '/api/attack_combination';
const RUNS_URI = `${COMBINATION_URI}/runs`;
const DIMENSIONS_URI = `${COMBINATION_URI}/dimensions`;
const PREVIEW_URI = `${COMBINATION_URI}/templates/preview`;
const SEVERITY_CONFIG_URI = `${COMBINATION_URI}/severity-config`;

export type AttackCombinationRunStatus
  = | 'pending'
    | 'running'
    | 'paused'
    | 'completed'
    | 'cancelled'
    | 'failed';

export type AttackCombinationHitState
  = | 'pending'
    | 'running'
    | 'hit'
    | 'miss'
    | 'timeout'
    | 'failed';

export type AttackCombinationClusterDim = 'asset' | 'device';

export interface BypassDimensionOutput {
  bypass_dimension_id: string;
  bypass_dimension_name: string;
  bypass_dimension_category: string;
  bypass_dimension_description: string;
  bypass_dimension_transform_type: string;
  bypass_dimension_transform_config: Record<string, unknown>;
  bypass_dimension_created_at: string;
  bypass_dimension_updated_at: string;
}

export interface BypassDimensionPageOutput {
  content: BypassDimensionOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface CombinationSampleOutput {
  base_attack_type: string;
  bypass_dimension_id: string;
  bypass_dimension_name: string;
  preview_payload: string;
}

export interface CombinationPreviewOutput {
  base_attack_types: string[];
  bypass_dimension_ids: string[];
  total_combinations: number;
  sample_size: number;
  samples: CombinationSampleOutput[];
  preview_base_payload: string;
}

export interface PreviewRequest {
  base_attack_types: string[];
  bypass_dimension_ids: string[];
  preview_base_payload: string;
}

export interface AttackCombinationRunOutput {
  attack_combination_run_id: string;
  attack_combination_run_name: string;
  attack_combination_run_base_attack_types: string[];
  attack_combination_run_bypass_dimension_ids: string[];
  attack_combination_run_asset_ids: string[];
  attack_combination_run_status: AttackCombinationRunStatus;
  attack_combination_run_total_combinations: number;
  attack_combination_run_total_results: number;
  attack_combination_run_completed_count: number;
  attack_combination_run_failed_count: number;
  attack_combination_run_rate_limit_per_second: number;
  attack_combination_run_concurrency: number;
  attack_combination_run_max_retries: number;
  attack_combination_run_timeout_hours: number;
  attack_combination_run_progress_percent: number;
  attack_combination_run_hit_state_counts: Record<string, number>;
  attack_combination_run_started_at: string | null;
  attack_combination_run_completed_at: string | null;
  attack_combination_run_expires_at: string | null;
  attack_combination_run_created_at: string;
  attack_combination_run_updated_at: string;
}

export interface AttackCombinationRunPageOutput {
  content: AttackCombinationRunOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface AttackCombinationResultOutput {
  attack_combination_result_id: string;
  attack_combination_result_run_id: string;
  attack_combination_result_combination_id: string;
  attack_combination_result_base_attack_type: string;
  attack_combination_result_bypass_dimension_id: string;
  attack_combination_result_asset_id: string;
  attack_combination_result_hit_state: AttackCombinationHitState;
  attack_combination_result_retry_count: number;
  attack_combination_result_payload_sample: string;
  attack_combination_result_error_message: string | null;
  attack_combination_result_executed_at: string | null;
  attack_combination_result_created_at: string;
  attack_combination_result_updated_at: string;
}

export interface AttackCombinationResultPageOutput {
  content: AttackCombinationResultOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface AttackCombinationClusterOutput {
  attack_combination_cluster_id: string;
  attack_combination_cluster_run_id: string;
  attack_combination_cluster_dim: AttackCombinationClusterDim;
  attack_combination_cluster_key: string;
  attack_combination_cluster_label: string;
  attack_combination_cluster_miss_count: number;
  attack_combination_cluster_total_in_cluster: number;
  attack_combination_cluster_payload_samples: string[];
  attack_combination_cluster_top_base_attack_types: Array<Record<string, unknown>>;
  attack_combination_cluster_top_bypass_dimensions: Array<Record<string, unknown>>;
  attack_combination_cluster_computed_at: string;
  attack_combination_cluster_created_at: string;
  attack_combination_cluster_updated_at: string;
  attack_combination_cluster_severity_score: number | string | null;
  attack_combination_cluster_severity_level: string | null;
  attack_combination_cluster_severity_label: string | null;
  attack_combination_cluster_severity_color: string | null;
}

export interface AttackCombinationClusterPageOutput {
  content: AttackCombinationClusterOutput[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface AttackCombinationClusterRecomputeOutput {
  attack_combination_run_id: string;
  cluster_recompute_status: string;
  cluster_recompute_job_id: string;
}

export interface SeverityConfigOutput {
  severity_config_id: string;
  severity_config_miss_count_weight: number | string;
  severity_config_attack_type_weight: number | string;
  severity_config_asset_sensitivity_weight: number | string;
  severity_config_critical_threshold: number | string;
  severity_config_high_threshold: number | string;
  severity_config_medium_threshold: number | string;
  severity_config_critical_label: string;
  severity_config_high_label: string;
  severity_config_medium_label: string;
  severity_config_info_label: string;
  severity_config_critical_color: string;
  severity_config_high_color: string;
  severity_config_medium_color: string;
  severity_config_info_color: string;
  severity_config_created_at: string;
  severity_config_updated_at: string;
}

export interface SeverityConfigUpdateRequest {
  miss_count_weight: number;
  attack_type_weight: number;
  asset_sensitivity_weight: number;
  critical_threshold: number;
  high_threshold: number;
  medium_threshold: number;
  critical_label: string;
  high_label: string;
  medium_label: string;
  info_label: string;
  critical_color: string;
  high_color: string;
  medium_color: string;
  info_color: string;
}

export interface SeverityRecomputeOutput {
  attack_combination_run_id: string;
  severity_recompute_status: string;
  severity_recompute_job_id: string;
}

export interface CreateRunRequest {
  name: string;
  base_attack_types: string[];
  bypass_dimension_ids: string[];
  asset_ids: string[];
  rate_limit_per_second?: number;
  concurrency?: number;
  max_retries?: number;
  timeout_hours?: number;
}

export interface ListRunsQuery {
  status?: AttackCombinationRunStatus;
  page?: number;
  size?: number;
}

export interface ListDimensionsQuery {
  category?: string;
  page?: number;
  size?: number;
}

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

// ===================== Dimensions / Preview =====================

export const fetchBypassDimensions = async (
  query: ListDimensionsQuery = {},
): Promise<BypassDimensionPageOutput> => {
  const qs = buildQueryString({
    category: query.category,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${DIMENSIONS_URI}${qs}`);
  return response.data as BypassDimensionPageOutput;
};

export const previewCombinations = async (
  request: PreviewRequest,
): Promise<CombinationPreviewOutput> => {
  const response = await simplePostCall(PREVIEW_URI, request);
  return response.data as CombinationPreviewOutput;
};

// ===================== Runs CRUD =====================

export const fetchCombinationRuns = async (
  query: ListRunsQuery = {},
): Promise<AttackCombinationRunPageOutput> => {
  const qs = buildQueryString({
    status: query.status,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${RUNS_URI}${qs}`);
  return response.data as AttackCombinationRunPageOutput;
};

export const fetchCombinationRun = async (
  id: string,
): Promise<AttackCombinationRunOutput> => {
  const response = await simpleCall(`${RUNS_URI}/${encodeURIComponent(id)}`);
  return response.data as AttackCombinationRunOutput;
};

export const createCombinationRun = async (
  request: CreateRunRequest,
  autoStart: boolean = true,
): Promise<AttackCombinationRunOutput> => {
  const qs = buildQueryString({ auto_start: autoStart ? 'true' : 'false' });
  const response = await simplePostCall(`${RUNS_URI}${qs}`, request);
  return response.data as AttackCombinationRunOutput;
};

export const pauseCombinationRun = async (
  id: string,
): Promise<AttackCombinationRunOutput> => {
  const response = await simplePostCall(`${RUNS_URI}/${encodeURIComponent(id)}/pause`);
  return response.data as AttackCombinationRunOutput;
};

export const resumeCombinationRun = async (
  id: string,
): Promise<AttackCombinationRunOutput> => {
  const response = await simplePostCall(`${RUNS_URI}/${encodeURIComponent(id)}/resume`);
  return response.data as AttackCombinationRunOutput;
};

export const cancelCombinationRun = async (
  id: string,
): Promise<AttackCombinationRunOutput> => {
  const response = await simplePostCall(`${RUNS_URI}/${encodeURIComponent(id)}/cancel`);
  return response.data as AttackCombinationRunOutput;
};

// ===================== Run Results =====================

export interface ListRunResultsQuery {
  hit_state?: AttackCombinationHitState;
  page?: number;
  size?: number;
}

export const fetchRunResults = async (
  id: string,
  query: ListRunResultsQuery = {},
): Promise<AttackCombinationResultPageOutput> => {
  const qs = buildQueryString({
    hit_state: query.hit_state,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${RUNS_URI}/${encodeURIComponent(id)}/results${qs}`);
  return response.data as AttackCombinationResultPageOutput;
};

// ===================== Clusters =====================

export interface ListClustersQuery {
  dim?: AttackCombinationClusterDim;
  page?: number;
  size?: number;
}

export const fetchClusters = async (
  id: string,
  query: ListClustersQuery = {},
): Promise<AttackCombinationClusterPageOutput> => {
  const qs = buildQueryString({
    dim: query.dim,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${RUNS_URI}/${encodeURIComponent(id)}/clusters${qs}`);
  return response.data as AttackCombinationClusterPageOutput;
};

export const fetchClusterDetail = async (
  runId: string,
  clusterId: string,
): Promise<AttackCombinationClusterOutput> => {
  const response = await simpleCall(
    `${RUNS_URI}/${encodeURIComponent(runId)}/clusters/${encodeURIComponent(clusterId)}`,
  );
  return response.data as AttackCombinationClusterOutput;
};

export const fetchClusterMembers = async (
  runId: string,
  clusterId: string,
  query: ListRunResultsQuery = {},
): Promise<AttackCombinationResultPageOutput> => {
  const qs = buildQueryString({
    hit_state: query.hit_state,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(
    `${RUNS_URI}/${encodeURIComponent(runId)}/clusters/${encodeURIComponent(clusterId)}/results${qs}`,
  );
  return response.data as AttackCombinationResultPageOutput;
};

export const recomputeClusters = async (
  id: string,
): Promise<AttackCombinationClusterRecomputeOutput> => {
  const response = await simplePostCall(
    `${RUNS_URI}/${encodeURIComponent(id)}/cluster/recompute`,
  );
  return response.data as AttackCombinationClusterRecomputeOutput;
};

// ===================== Severity Config =====================

export const fetchSeverityConfig = async (): Promise<SeverityConfigOutput> => {
  const response = await simpleCall(SEVERITY_CONFIG_URI);
  return response.data as SeverityConfigOutput;
};

export const updateSeverityConfig = async (
  request: SeverityConfigUpdateRequest,
): Promise<SeverityConfigOutput> => {
  const response = await simplePutCall(SEVERITY_CONFIG_URI, request);
  return response.data as SeverityConfigOutput;
};

export const recomputeSeverity = async (
  id: string,
): Promise<SeverityRecomputeOutput> => {
  const response = await simplePostCall(
    `${RUNS_URI}/${encodeURIComponent(id)}/severity/recompute`,
  );
  return response.data as SeverityRecomputeOutput;
};
