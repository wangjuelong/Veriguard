import { describe, expect, it } from 'vitest';

import { type AttackChainNodeExpectation } from '../../../../../../utils/api-types';
import { buildRuntimeNodeMap, computeRuntimeNodeData, type RuntimeNodeInput } from '../runtimeNodeAdapter';

const node = (overrides: Partial<RuntimeNodeInput> = {}): RuntimeNodeInput => ({
  nodeId: 'n1',
  title: 'Phishing',
  subtitle: 'http_request',
  ...overrides,
});

const expectation = (
  overrides: Partial<AttackChainNodeExpectation> = {},
): AttackChainNodeExpectation => ({
  node_expectation_id: `e-${Math.random()}`,
  node_expectation_node: 'n1',
  node_expectation_type: 'PREVENTION',
  node_expectation_status: 'PENDING',
  node_expectation_expected_score: 100,
  node_expiration_time: 3600,
  ...overrides,
});

describe('computeRuntimeNodeData', () => {
  it('SKIPPED node state → 双层均 SKIPPED（截停优先）', () => {
    const data = computeRuntimeNodeData(node({ nodeState: 'SKIPPED' }), [
      expectation({ node_expectation_status: 'SUCCESS' }),
    ]);
    expect(data.preventionStatus).toBe('SKIPPED');
    expect(data.detectionStatus).toBe('SKIPPED');
  });

  it('无该维度 expectation → N_A（不计入分母）', () => {
    const data = computeRuntimeNodeData(node(), []);
    expect(data.preventionStatus).toBe('N_A');
    expect(data.detectionStatus).toBe('N_A');
  });

  it('全 SUCCESS → SUCCESS', () => {
    const data = computeRuntimeNodeData(node(), [
      expectation({
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'SUCCESS',
      }),
      expectation({
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'SUCCESS',
      }),
    ]);
    expect(data.preventionStatus).toBe('SUCCESS');
    expect(data.detectionStatus).toBe('N_A');
  });

  it('全 FAILED → FAILED', () => {
    const data = computeRuntimeNodeData(node(), [
      expectation({
        node_expectation_type: 'DETECTION',
        node_expectation_status: 'FAILED',
      }),
    ]);
    expect(data.preventionStatus).toBe('N_A');
    expect(data.detectionStatus).toBe('FAILED');
  });

  it('混合 SUCCESS + FAILED → PARTIAL', () => {
    const data = computeRuntimeNodeData(node(), [
      expectation({
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'SUCCESS',
      }),
      expectation({
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'FAILED',
      }),
    ]);
    expect(data.preventionStatus).toBe('PARTIAL');
  });

  it('全 PENDING / UNKNOWN → PENDING', () => {
    const data = computeRuntimeNodeData(node(), [
      expectation({
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'PENDING',
      }),
      expectation({
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'UNKNOWN',
      }),
    ]);
    expect(data.preventionStatus).toBe('PENDING');
  });

  it('PARTIAL expectation 单独 → PARTIAL', () => {
    const data = computeRuntimeNodeData(node(), [
      expectation({
        node_expectation_type: 'DETECTION',
        node_expectation_status: 'PARTIAL',
      }),
    ]);
    expect(data.detectionStatus).toBe('PARTIAL');
  });

  it('PREVENTION + DETECTION 独立聚合（互不干扰）', () => {
    const data = computeRuntimeNodeData(node(), [
      expectation({
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'SUCCESS',
      }),
      expectation({
        node_expectation_type: 'DETECTION',
        node_expectation_status: 'FAILED',
      }),
    ]);
    expect(data.preventionStatus).toBe('SUCCESS');
    expect(data.detectionStatus).toBe('FAILED');
  });

  it('其他类型 expectation（TEXT/CHALLENGE 等）忽略', () => {
    const data = computeRuntimeNodeData(node(), [
      expectation({
        node_expectation_type: 'TEXT',
        node_expectation_status: 'SUCCESS',
      }),
      expectation({
        node_expectation_type: 'MANUAL',
        node_expectation_status: 'SUCCESS',
      }),
    ]);
    expect(data.preventionStatus).toBe('N_A');
    expect(data.detectionStatus).toBe('N_A');
  });

  it('repeatCount / currentIteration 透传', () => {
    const data = computeRuntimeNodeData(
      node({
        repeatCount: 5,
        currentIteration: 2,
      }),
      [],
    );
    expect(data.repeatCount).toBe(5);
    expect(data.currentIteration).toBe(2);
  });
});

describe('buildRuntimeNodeMap', () => {
  it('按 nodeId 索引 expectations，O(N+E) 聚合', () => {
    const nodes: RuntimeNodeInput[] = [
      node({
        nodeId: 'a',
        title: 'Node A',
      }),
      node({
        nodeId: 'b',
        title: 'Node B',
      }),
    ];
    const expectations: AttackChainNodeExpectation[] = [
      expectation({
        node_expectation_node: 'a',
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'SUCCESS',
      }),
      expectation({
        node_expectation_node: 'b',
        node_expectation_type: 'DETECTION',
        node_expectation_status: 'FAILED',
      }),
      // 噪声：未引用节点 c
      expectation({
        node_expectation_node: 'c',
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'SUCCESS',
      }),
    ];

    const map = buildRuntimeNodeMap(nodes, expectations);

    expect(map.size).toBe(2);
    expect(map.get('a')!.preventionStatus).toBe('SUCCESS');
    expect(map.get('a')!.detectionStatus).toBe('N_A');
    expect(map.get('b')!.preventionStatus).toBe('N_A');
    expect(map.get('b')!.detectionStatus).toBe('FAILED');
    // 'c' 不在 nodes 中 → 不进 map
    expect(map.has('c')).toBe(false);
  });

  it('expectation 缺 node_expectation_node → 跳过', () => {
    const nodes: RuntimeNodeInput[] = [node({ nodeId: 'a' })];
    const expectations: AttackChainNodeExpectation[] = [
      expectation({
        node_expectation_node: undefined,
        node_expectation_type: 'PREVENTION',
        node_expectation_status: 'SUCCESS',
      }),
    ];

    const map = buildRuntimeNodeMap(nodes, expectations);

    expect(map.get('a')!.preventionStatus).toBe('N_A');
  });

  it('空输入 → 空 map', () => {
    expect(buildRuntimeNodeMap([], []).size).toBe(0);
  });
});
