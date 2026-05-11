import { describe, expect, it } from 'vitest';

import { type NodeContract } from '../../../../../../utils/api-types';
import { toDynamicNodeViewModels } from '../dynamicContractsAdapter';

const contract = (overrides: Partial<NodeContract> = {}): NodeContract => ({
  injector_contract_id: `c-${Math.random()}`,
  injector_contract_labels: { en: 'Test contract' },
  ...overrides,
} as NodeContract);

describe('toDynamicNodeViewModels', () => {
  it('空数组 → 空列表', () => {
    expect(toDynamicNodeViewModels([])).toEqual([]);
  });

  it('null / undefined → 空列表', () => {
    expect(toDynamicNodeViewModels(null)).toEqual([]);
    expect(toDynamicNodeViewModels(undefined)).toEqual([]);
  });

  it('单 contract → 1 个节点，id 前缀 dynamic-', () => {
    const c = contract({
      injector_contract_id: 'c-abc',
      injector_contract_labels: { en: 'phishing' },
    });
    const result = toDynamicNodeViewModels([c]);
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('dynamic-c-abc');
    expect(result[0].label).toBe('phishing');
  });

  it('多 contracts → 各自一节点，保持顺序', () => {
    const c1 = contract({ injector_contract_id: 'c-1' });
    const c2 = contract({ injector_contract_id: 'c-2' });
    const c3 = contract({ injector_contract_id: 'c-3' });
    const result = toDynamicNodeViewModels([c1, c2, c3]);
    expect(result.map(r => r.id)).toEqual(['dynamic-c-1', 'dynamic-c-2', 'dynamic-c-3']);
  });

  it('每个动态节点位置 y 固定，x 按 index 递增（避免重叠）', () => {
    const c1 = contract({ injector_contract_id: 'c-1' });
    const c2 = contract({ injector_contract_id: 'c-2' });
    const result = toDynamicNodeViewModels([c1, c2]);
    expect(result[0].position.x).toBeLessThan(result[1].position.x);
    expect(result[0].position.y).toBe(result[1].position.y);
  });

  it('每个动态节点带 isDynamic=true 标记', () => {
    const c = contract();
    const result = toDynamicNodeViewModels([c]);
    expect(result[0].isDynamic).toBe(true);
  });

  it('contract.injector_contract_labels 缺 en → fallback 用 injector_contract_id', () => {
    const c = contract({
      injector_contract_id: 'c-fallback',
      injector_contract_labels: undefined,
    });
    const result = toDynamicNodeViewModels([c]);
    expect(result[0].label).toBe('c-fallback');
  });
});
