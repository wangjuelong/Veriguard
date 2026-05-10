import { describe, expect, it } from 'vitest';

import { type AttackChainNode, type NodeContract } from '../../../../../utils/api-types';
import { toAttackPatternResultsForEditor } from '../attackPatternMatrixAdapter';

const contract = (
  attackPatterns: string[],
  contractId = 'c1',
): Pick<NodeContract, 'injector_contract_id' | 'injector_contract_attack_patterns'> => ({
  injector_contract_id: contractId,
  injector_contract_attack_patterns: attackPatterns,
});

const node = (
  overrides: Partial<AttackChainNode> = {},
): AttackChainNode => ({
  node_id: `n-${Math.random()}`,
  node_title: 'node',
  node_depends_duration: 0,
  ...overrides,
} as AttackChainNode);

describe('toAttackPatternResultsForEditor', () => {
  it('空节点 → 空数组', () => {
    expect(toAttackPatternResultsForEditor([])).toEqual([]);
  });

  it('节点无 injector_contract → 跳过（不抛错）', () => {
    expect(toAttackPatternResultsForEditor([node()])).toEqual([]);
  });

  it('节点 contract.attack_patterns 为空 → 跳过', () => {
    const n = node({ node_injector_contract: contract([]) as NodeContract });
    expect(toAttackPatternResultsForEditor([n])).toEqual([]);
  });

  it('单节点单 pattern → 输出 1 条 result', () => {
    const n = node({
      node_id: 'n1',
      node_title: 'phishing',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n]);
    expect(result).toHaveLength(1);
    expect(result[0].node_attack_pattern).toBe('T1566');
    expect(result[0].node_expectation_results).toHaveLength(1);
    expect(result[0].node_expectation_results![0].node_id).toBe('n1');
    expect(result[0].node_expectation_results![0].node_title).toBe('phishing');
  });

  it('单节点多 patterns → 每个 pattern 一条 result，节点同时出现在每个 result 的 expectation_results', () => {
    const n = node({
      node_id: 'n1',
      node_injector_contract: contract(['T1566', 'T1059']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n]);
    expect(result.map(r => r.node_attack_pattern).sort()).toEqual(['T1059', 'T1566']);
    result.forEach((r) => {
      expect(r.node_expectation_results).toHaveLength(1);
      expect(r.node_expectation_results![0].node_id).toBe('n1');
    });
  });

  it('多节点同 pattern → 聚合到同一 result.node_expectation_results 数组', () => {
    const n1 = node({
      node_id: 'n1',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const n2 = node({
      node_id: 'n2',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n1, n2]);
    expect(result).toHaveLength(1);
    expect(result[0].node_attack_pattern).toBe('T1566');
    expect(result[0].node_expectation_results).toHaveLength(2);
    expect(result[0].node_expectation_results!.map(r => r.node_id).sort()).toEqual(['n1', 'n2']);
  });

  it('多节点不同 pattern → 各自一条 result', () => {
    const n1 = node({
      node_id: 'n1',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const n2 = node({
      node_id: 'n2',
      node_injector_contract: contract(['T1059']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n1, n2]);
    expect(result).toHaveLength(2);
    const byPattern = new Map(result.map(r => [r.node_attack_pattern, r.node_expectation_results!.map(x => x.node_id).sort()]));
    expect(byPattern.get('T1566')).toEqual(['n1']);
    expect(byPattern.get('T1059')).toEqual(['n2']);
  });

  it('混合：n1 (T1566+T1059), n2 (T1566) → T1566 含 [n1,n2]，T1059 含 [n1]', () => {
    const n1 = node({
      node_id: 'n1',
      node_injector_contract: contract(['T1566', 'T1059']) as NodeContract,
    });
    const n2 = node({
      node_id: 'n2',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n1, n2]);
    const byPattern = new Map(result.map(r => [r.node_attack_pattern, r.node_expectation_results!.map(x => x.node_id).sort()]));
    expect(byPattern.get('T1566')).toEqual(['n1', 'n2']);
    expect(byPattern.get('T1059')).toEqual(['n1']);
  });

  it('节点 expectation_results 数组中每条 results 字段都为 [] 占位（编辑器无 verdict）', () => {
    const n = node({
      node_id: 'n1',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n]);
    expect(result[0].node_expectation_results![0].results).toEqual([]);
  });

  it('保持节点顺序：第一个出现的节点排在 expectation_results 前面', () => {
    const n1 = node({
      node_id: 'n1',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const n2 = node({
      node_id: 'n2',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const n3 = node({
      node_id: 'n3',
      node_injector_contract: contract(['T1566']) as NodeContract,
    });
    const result = toAttackPatternResultsForEditor([n3, n1, n2]);
    expect(result[0].node_expectation_results!.map(r => r.node_id)).toEqual(['n3', 'n1', 'n2']);
  });

  it('null / undefined attack_patterns 字段安全处理', () => {
    const n = node({
      node_id: 'n1',
      node_injector_contract: { injector_contract_id: 'c1' } as NodeContract,
    });
    expect(toAttackPatternResultsForEditor([n])).toEqual([]);
  });
});
