import { simpleCall } from '../../utils/Action';

const STABILITY_URI = '/api/stability';
const TREND_URI = `${STABILITY_URI}/trend`;
const TREND_DAILY_URI = `${STABILITY_URI}/trend/daily`;

export interface StabilityTrendOutput {
  snapshot_id: string;
  snapshot_run_id: string | null;
  snapshot_node_id: string | null;
  snapshot_device_id: string | null;
  snapshot_baseline_id: string | null;
  snapshot_hit_count: number;
  snapshot_total_count: number;
  snapshot_hit_rate: number;
  snapshot_captured_at: string;
}

export interface StabilityTrendListOutput {
  items: StabilityTrendOutput[];
  total: number;
  page: number;
  limit: number;
}

export interface StabilityDailyAggregate {
  day: string;
  avg_hit_rate: number;
  snapshot_count: number;
  total_hit_count: number;
  total_count_sum: number;
}

export interface StabilityTrendQuery {
  deviceId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

const buildQueryString = (params: Record<string, string | number | undefined>): string => {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== '');
  if (entries.length === 0) {
    return '';
  }
  const query = entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&');
  return `?${query}`;
};

export const fetchStabilityTrend = async (query: StabilityTrendQuery = {}): Promise<StabilityTrendListOutput> => {
  const qs = buildQueryString({
    deviceId: query.deviceId,
    from: query.from,
    to: query.to,
    page: query.page,
    size: query.size,
  });
  const response = await simpleCall(`${TREND_URI}${qs}`);
  return response.data as StabilityTrendListOutput;
};

export const fetchStabilityTrendDaily = async (query: Omit<StabilityTrendQuery, 'page' | 'size'> = {}): Promise<StabilityDailyAggregate[]> => {
  const qs = buildQueryString({
    deviceId: query.deviceId,
    from: query.from,
    to: query.to,
  });
  const response = await simpleCall(`${TREND_DAILY_URI}${qs}`);
  return response.data as StabilityDailyAggregate[];
};
