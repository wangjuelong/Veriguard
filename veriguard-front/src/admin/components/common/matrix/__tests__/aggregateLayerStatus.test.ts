import { describe, expect, it } from 'vitest';

import { type NodeExpectationResultsByType } from '../../../../../utils/api-types';
import aggregateLayerStatus from '../aggregateLayerStatus';

type AvgResult = 'FAILED' | 'PENDING' | 'PARTIAL' | 'UNKNOWN' | 'SUCCESS';

const result = (
  resultEntries: {
    type: string;
    avgResult: AvgResult;
  }[],
): NodeExpectationResultsByType => ({
  node_id: `n-${Math.random()}`,
  node_title: 'node',
  results: resultEntries.map(e => ({
    type: e.type as 'DETECTION' | 'HUMAN_RESPONSE' | 'PREVENTION' | 'VULNERABILITY',
    avgResult: e.avgResult,
    distribution: [],
  })),
});

describe('aggregateLayerStatus', () => {
  it('空 results → N_A', () => {
    expect(aggregateLayerStatus([], 'prevention')).toBe('N_A');
  });

  it('该维度无 expectation → N_A', () => {
    const r = result([{
      type: 'HUMAN_RESPONSE',
      avgResult: 'SUCCESS',
    }]);
    expect(aggregateLayerStatus([r], 'prevention')).toBe('N_A');
  });

  it('全 SUCCESS → SUCCESS', () => {
    const r1 = result([{
      type: 'PREVENTION',
      avgResult: 'SUCCESS',
    }]);
    const r2 = result([{
      type: 'PREVENTION',
      avgResult: 'SUCCESS',
    }]);
    expect(aggregateLayerStatus([r1, r2], 'prevention')).toBe('SUCCESS');
  });

  it('全 FAILED → FAILED', () => {
    const r1 = result([{
      type: 'DETECTION',
      avgResult: 'FAILED',
    }]);
    expect(aggregateLayerStatus([r1], 'detection')).toBe('FAILED');
  });

  it('全 PENDING / UNKNOWN → PENDING（运行未完成）', () => {
    const r1 = result([{
      type: 'PREVENTION',
      avgResult: 'PENDING',
    }]);
    const r2 = result([{
      type: 'PREVENTION',
      avgResult: 'UNKNOWN',
    }]);
    expect(aggregateLayerStatus([r1, r2], 'prevention')).toBe('PENDING');
  });

  it('SUCCESS + FAILED 混合 → PARTIAL', () => {
    const r1 = result([{
      type: 'PREVENTION',
      avgResult: 'SUCCESS',
    }]);
    const r2 = result([{
      type: 'PREVENTION',
      avgResult: 'FAILED',
    }]);
    expect(aggregateLayerStatus([r1, r2], 'prevention')).toBe('PARTIAL');
  });

  it('SUCCESS + PENDING（无 FAILED）→ PENDING（保守，运行未完成）', () => {
    const r1 = result([{
      type: 'PREVENTION',
      avgResult: 'SUCCESS',
    }]);
    const r2 = result([{
      type: 'PREVENTION',
      avgResult: 'PENDING',
    }]);
    expect(aggregateLayerStatus([r1, r2], 'prevention')).toBe('PENDING');
  });

  it('FAILED + PENDING（无 SUCCESS）→ FAILED', () => {
    const r1 = result([{
      type: 'PREVENTION',
      avgResult: 'FAILED',
    }]);
    const r2 = result([{
      type: 'PREVENTION',
      avgResult: 'PENDING',
    }]);
    expect(aggregateLayerStatus([r1, r2], 'prevention')).toBe('FAILED');
  });

  it('PREVENTION 与 DETECTION 维度独立过滤', () => {
    const r = result([
      {
        type: 'PREVENTION',
        avgResult: 'SUCCESS',
      },
      {
        type: 'DETECTION',
        avgResult: 'FAILED',
      },
    ]);
    expect(aggregateLayerStatus([r], 'prevention')).toBe('SUCCESS');
    expect(aggregateLayerStatus([r], 'detection')).toBe('FAILED');
  });

  it('其他类型 expectation 忽略', () => {
    const r = result([
      {
        type: 'HUMAN_RESPONSE',
        avgResult: 'SUCCESS',
      },
      {
        type: 'VULNERABILITY',
        avgResult: 'FAILED',
      },
    ]);
    expect(aggregateLayerStatus([r], 'prevention')).toBe('N_A');
  });

  it('results 字段为 undefined 的节点 → 跳过（不抛错），按剩余节点聚合', () => {
    const r1: NodeExpectationResultsByType = {
      node_id: 'n1',
      node_title: 'node',
      results: undefined,
    };
    const r2 = result([{
      type: 'PREVENTION',
      avgResult: 'SUCCESS',
    }]);
    expect(aggregateLayerStatus([r1, r2], 'prevention')).toBe('SUCCESS');
  });
});
