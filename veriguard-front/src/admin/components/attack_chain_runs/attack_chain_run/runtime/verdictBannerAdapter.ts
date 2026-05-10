import { type AttackChainLinkExpectationWire } from '../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRun } from '../../../../../utils/api-types';
import { type DoubleLayerNodeData, type LinkVerdict, type VerdictBannerData } from './attackChainRuntimeTypes';

/**
 * 后端 wire-format `attack_chain_run_verdict_*` 与 frontend `LinkVerdict` 字面量精确对齐 ——
 * 类型上是同一组 union（都是 'FULL_BREACH' | 'FULL_BLOCKED' | 'PARTIAL' | 'PENDING' | 'N_A'）。
 *
 * 缺失（未结算 run 还没写 verdict 列）→ PENDING.
 */
const verdictOrPending = (verdict: AttackChainRun['attack_chain_run_verdict_prevention']): LinkVerdict =>
  verdict ?? 'PENDING';

const isRunningStatus = (status: AttackChainRun['attack_chain_run_status']): boolean =>
  status === 'RUNNING' || status === 'SCHEDULED';

const countByStatus = (
  runtimeMap: ReadonlyMap<string, DoubleLayerNodeData>,
  layer: 'preventionStatus' | 'detectionStatus',
  target: DoubleLayerNodeData['preventionStatus'],
): number => {
  let n = 0;
  runtimeMap.forEach((data) => {
    if (data[layer] === target) {
      n += 1;
    }
  });
  return n;
};

const countSettled = (runtimeMap: ReadonlyMap<string, DoubleLayerNodeData>): number => {
  let n = 0;
  runtimeMap.forEach((data) => {
    if (data.preventionStatus !== 'PENDING' && data.detectionStatus !== 'PENDING') {
      n += 1;
    }
  });
  return n;
};

/**
 * Banner data 派生：
 *
 * - prevention / detection verdict：取 run 字段
 * - preventionStats：节点级 PREVENTION layer = SUCCESS 的节点数 / 节点总数（"X 个被拦下"）
 * - detectionStats：节点级 DETECTION layer = SUCCESS 的节点数 + 链路级 link expectation = SUCCESS
 *   的条数（"M / N 节点检出 · X / Y 链路 SOC 匹配"）
 * - running：run.status RUNNING / SCHEDULED 时切到精简单行（spec §6.3.3）
 *   - runningStats.settled = 节点 prevention/detection 都 ≠ PENDING 的数量近似（粗略）
 */
const toVerdictBannerData = (
  run: AttackChainRun,
  runtimeMap: ReadonlyMap<string, DoubleLayerNodeData>,
  linkExpectations: readonly AttackChainLinkExpectationWire[],
): VerdictBannerData => {
  const totalNodes = runtimeMap.size;
  const blockedNodes = countByStatus(runtimeMap, 'preventionStatus', 'SUCCESS');
  const detectedNodes = countByStatus(runtimeMap, 'detectionStatus', 'SUCCESS');
  const totalLinks = linkExpectations.length;
  const matchedLinks = linkExpectations.filter(e => e.status === 'SUCCESS').length;
  const running = isRunningStatus(run.attack_chain_run_status);

  const data: VerdictBannerData = {
    prevention: verdictOrPending(run.attack_chain_run_verdict_prevention),
    detection: verdictOrPending(run.attack_chain_run_verdict_detection),
  };

  if (totalNodes > 0) {
    data.preventionStats = {
      blocked: blockedNodes,
      total: totalNodes,
    };
    data.detectionStats = {
      nodesDetected: detectedNodes,
      nodeTotal: totalNodes,
      linksMatched: matchedLinks,
      linkTotal: totalLinks,
    };
  }

  if (running) {
    data.running = true;
    data.runningStats = {
      settled: countSettled(runtimeMap),
      total: totalNodes,
      blocked: blockedNodes,
      detected: detectedNodes,
    };
  }

  return data;
};

export default toVerdictBannerData;
