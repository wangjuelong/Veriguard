import { describe, expect, it } from 'vitest';

import { type AttackChainLinkExpectationWire } from '../../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { type AttackChainRun } from '../../../../../../utils/api-types';
import { type DoubleLayerNodeData } from '../attackChainRuntimeTypes';
import toVerdictBannerData from '../verdictBannerAdapter';

const run = (overrides: Partial<AttackChainRun> = {}): AttackChainRun => ({
  attack_chain_run_id: 'r1',
  attack_chain_run_name: 'Run 1',
  attack_chain_run_status: 'FINISHED',
  attack_chain_run_global_score: [],
  ...overrides,
} as AttackChainRun);

const dl = (overrides: Partial<DoubleLayerNodeData> = {}): DoubleLayerNodeData => ({
  title: 'n',
  preventionStatus: 'SUCCESS',
  detectionStatus: 'SUCCESS',
  ...overrides,
});

const link = (
  overrides: Partial<AttackChainLinkExpectationWire> = {},
): AttackChainLinkExpectationWire => ({
  link_expectation_id: 'le-1',
  attack_chain_run_id: 'r1',
  connector_id: 'elastic',
  rule_id: 'r',
  display_name: 'Rule',
  match_window_seconds: 1800,
  score: 0,
  expected_score: 100,
  status: 'PENDING',
  expiration_time: '2026-05-10T12:00:00Z',
  created_at: '2026-05-10T10:00:00Z',
  updated_at: '2026-05-10T10:00:00Z',
  traces: [],
  ...overrides,
});

describe('toVerdictBannerData', () => {
  it('verdict 字段透传，缺失 → PENDING', () => {
    const map = new Map<string, DoubleLayerNodeData>();
    const data = toVerdictBannerData(run(), map, []);
    expect(data.prevention).toBe('PENDING');
    expect(data.detection).toBe('PENDING');
  });

  it('verdict 显式赋值时直接采用', () => {
    const data = toVerdictBannerData(
      run({
        attack_chain_run_verdict_prevention: 'FULL_BLOCKED',
        attack_chain_run_verdict_detection: 'PARTIAL',
      }),
      new Map(),
      [],
    );
    expect(data.prevention).toBe('FULL_BLOCKED');
    expect(data.detection).toBe('PARTIAL');
  });

  it('节点级 stats：blocked / detected 计数 + 总数', () => {
    const map = new Map<string, DoubleLayerNodeData>([
      ['a', dl({
        preventionStatus: 'SUCCESS',
        detectionStatus: 'SUCCESS',
      })],
      ['b', dl({
        preventionStatus: 'FAILED',
        detectionStatus: 'SUCCESS',
      })],
      ['c', dl({
        preventionStatus: 'SUCCESS',
        detectionStatus: 'FAILED',
      })],
    ]);
    const data = toVerdictBannerData(run(), map, []);
    expect(data.preventionStats).toEqual({
      blocked: 2,
      total: 3,
    });
    expect(data.detectionStats?.nodesDetected).toBe(2);
    expect(data.detectionStats?.nodeTotal).toBe(3);
  });

  it('链路级 stats：linksMatched (SUCCESS 计) / linkTotal', () => {
    const map = new Map<string, DoubleLayerNodeData>([['a', dl()]]);
    const links = [
      link({ status: 'SUCCESS' }),
      link({ status: 'SUCCESS' }),
      link({ status: 'FAILED' }),
      link({ status: 'PENDING' }),
    ];
    const data = toVerdictBannerData(run(), map, links);
    expect(data.detectionStats?.linksMatched).toBe(2);
    expect(data.detectionStats?.linkTotal).toBe(4);
  });

  it('节点 map 空 → 不输出 stats（避免 0/0 误导）', () => {
    const data = toVerdictBannerData(run(), new Map(), []);
    expect(data.preventionStats).toBeUndefined();
    expect(data.detectionStats).toBeUndefined();
  });

  it('RUNNING 状态 → running=true + runningStats', () => {
    const map = new Map<string, DoubleLayerNodeData>([
      ['a', dl({
        preventionStatus: 'SUCCESS',
        detectionStatus: 'SUCCESS',
      })],
      ['b', dl({
        preventionStatus: 'PENDING',
        detectionStatus: 'PENDING',
      })],
    ]);
    const data = toVerdictBannerData(run({ attack_chain_run_status: 'RUNNING' }), map, []);
    expect(data.running).toBe(true);
    expect(data.runningStats).toEqual({
      settled: 1,
      total: 2,
      blocked: 1,
      detected: 1,
    });
  });

  it('SCHEDULED 状态也走 running 单行模式', () => {
    const data = toVerdictBannerData(
      run({ attack_chain_run_status: 'SCHEDULED' }),
      new Map(),
      [],
    );
    expect(data.running).toBe(true);
  });

  it('FINISHED / CANCELED 等终态 → running undefined', () => {
    const data = toVerdictBannerData(
      run({ attack_chain_run_status: 'FINISHED' }),
      new Map(),
      [],
    );
    expect(data.running).toBeUndefined();
    expect(data.runningStats).toBeUndefined();
  });

  it('settled 计数：同时 prevention + detection 都 ≠ PENDING', () => {
    const map = new Map<string, DoubleLayerNodeData>([
      ['a', dl({
        preventionStatus: 'SUCCESS',
        detectionStatus: 'PENDING',
      })], // 不计
      ['b', dl({
        preventionStatus: 'SUCCESS',
        detectionStatus: 'FAILED',
      })], // 计
      ['c', dl({
        preventionStatus: 'PARTIAL',
        detectionStatus: 'N_A',
      })], // 计（PENDING 之外都行）
      ['d', dl({
        preventionStatus: 'PENDING',
        detectionStatus: 'PENDING',
      })], // 不计
    ]);
    const data = toVerdictBannerData(run({ attack_chain_run_status: 'RUNNING' }), map, []);
    expect(data.runningStats?.settled).toBe(2);
  });
});
