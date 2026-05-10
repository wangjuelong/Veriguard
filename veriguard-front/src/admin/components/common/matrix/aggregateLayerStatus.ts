import { type NodeExpectationResultsByType } from '../../../../utils/api-types';
import { type NodeLayerStatus } from '../../attack_chain_runs/attack_chain_run/runtime/attackChainRuntimeTypes';

/**
 * 多节点同 pattern 同维度 (PREVENTION / DETECTION) expectation 聚合为 NodeLayerStatus.
 *
 * 严格按 spec §4.2：
 * - 无该维度 expectation → N_A
 * - 全 SUCCESS → SUCCESS
 * - 全 FAILED → FAILED
 * - 全 PENDING / UNKNOWN → PENDING（运行未完成）
 * - **包含 SUCCESS + FAILED 同时存在 → PARTIAL**（明确混合状态）
 * - FAILED 且无 SUCCESS → FAILED（部分确定失败）
 * - SUCCESS 但仍有 PENDING → PENDING（保守，运行未完成不能确认全胜）
 * - 全 SUCCESS → SUCCESS
 * - 否则（全 PENDING / UNKNOWN）→ PENDING（运行未完成）
 */
const aggregateLayerStatus = (
  results: NodeExpectationResultsByType[],
  dimension: 'prevention' | 'detection',
): NodeLayerStatus => {
  if (results.length === 0) return 'N_A';
  const targetType = dimension === 'prevention' ? 'PREVENTION' : 'DETECTION';
  const layerResults = results
    .flatMap(r => r.results ?? [])
    .filter(er => er.type === targetType)
    .map(er => er.avgResult);
  if (layerResults.length === 0) return 'N_A';
  const hasSuccess = layerResults.some(s => s === 'SUCCESS');
  const hasFailed = layerResults.some(s => s === 'FAILED');
  const hasPending = layerResults.some(s => s === 'PENDING' || s === 'UNKNOWN' || s === 'PARTIAL');
  if (hasSuccess && hasFailed) return 'PARTIAL';
  if (hasFailed) return 'FAILED';
  // SUCCESS present but run is still in progress → conservative PENDING
  if (hasSuccess && hasPending) return 'PENDING';
  if (hasSuccess) return 'SUCCESS';
  return 'PENDING';
};

export default aggregateLayerStatus;
