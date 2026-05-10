import { describe, expect, it } from 'vitest';

import { type AttackChainLinkExpectationWire } from '../../../../../../actions/attack_chain_runs/attack_chain_run-action';
import { toLinkExpectationItem, toLinkExpectationItems } from '../linkExpectationsAdapter';

const baseWire = (
  overrides: Partial<AttackChainLinkExpectationWire> = {},
): AttackChainLinkExpectationWire => ({
  link_expectation_id: 'le-1',
  attack_chain_run_id: 'run-1',
  connector_id: 'elastic',
  rule_id: 'rule-abc',
  display_name: 'Brute force',
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

describe('toLinkExpectationItem', () => {
  it('SUCCESS with trace → incidentRef 取首条 incident_id + score 透传', () => {
    const item = toLinkExpectationItem(
      baseWire({
        status: 'SUCCESS',
        score: 100,
        traces: [
          {
            trace_id: 't1',
            incident_id: 'inc-7',
            correlation_rule_name: 'r',
            triggered_at: '2026-05-10T10:30:00Z',
            score_delta: 100,
            raw_payload: null,
            created_at: '2026-05-10T10:30:00Z',
          },
        ],
      }),
    );

    expect(item.status).toBe('SUCCESS');
    expect(item.incidentRef).toBe('#inc-7');
    expect(item.score).toBe(100);
    expect(item.expectedScore).toBe(100);
  });

  it('SUCCESS without trace → incidentRef undefined（不崩）', () => {
    const item = toLinkExpectationItem(baseWire({
      status: 'SUCCESS',
      score: 100,
    }));
    expect(item.incidentRef).toBeUndefined();
    expect(item.score).toBe(100);
  });

  it('PARTIAL → score / expectedScore 透传，无 incidentRef', () => {
    const item = toLinkExpectationItem(
      baseWire({
        status: 'PARTIAL',
        score: 60,
        expected_score: 100,
      }),
    );
    expect(item.status).toBe('PARTIAL');
    expect(item.score).toBe(60);
    expect(item.expectedScore).toBe(100);
    expect(item.incidentRef).toBeUndefined();
  });

  it('FAILED → failureReason 含 match_window_seconds', () => {
    const item = toLinkExpectationItem(
      baseWire({
        status: 'FAILED',
        match_window_seconds: 7200,
      }),
    );
    expect(item.status).toBe('FAILED');
    expect(item.failureReason).toBe('0 命中（窗口 7200s）');
  });

  it('UNKNOWN → unknownReason 兜底文案', () => {
    const item = toLinkExpectationItem(baseWire({ status: 'UNKNOWN' }));
    expect(item.status).toBe('UNKNOWN');
    expect(item.unknownReason).toBe('查询失败 / 已过期');
  });

  it('PENDING → 仅基础字段', () => {
    const item = toLinkExpectationItem(baseWire({ status: 'PENDING' }));
    expect(item.status).toBe('PENDING');
    expect(item.incidentRef).toBeUndefined();
    expect(item.score).toBeUndefined();
    expect(item.failureReason).toBeUndefined();
  });

  it('display_name 为 null → 回退 "connector / rule"', () => {
    const item = toLinkExpectationItem(
      baseWire({
        display_name: null,
        connector_id: 'splunk',
        rule_id: 'r-99',
      }),
    );
    expect(item.ruleDisplayName).toBe('splunk / r-99');
  });

  it('display_name 非空 → 直接用', () => {
    const item = toLinkExpectationItem(baseWire({ display_name: 'Lateral movement' }));
    expect(item.ruleDisplayName).toBe('Lateral movement');
  });
});

describe('toLinkExpectationItems', () => {
  it('null / undefined / 空数组 → 空列表', () => {
    expect(toLinkExpectationItems(null)).toEqual([]);
    expect(toLinkExpectationItems(undefined)).toEqual([]);
    expect(toLinkExpectationItems([])).toEqual([]);
  });

  it('保持顺序，逐条映射', () => {
    const items = toLinkExpectationItems([
      baseWire({
        link_expectation_id: 'a',
        status: 'SUCCESS',
        score: 100,
      }),
      baseWire({
        link_expectation_id: 'b',
        status: 'FAILED',
      }),
      baseWire({
        link_expectation_id: 'c',
        status: 'PENDING',
      }),
    ]);
    expect(items.map(i => i.id)).toEqual(['a', 'b', 'c']);
    expect(items.map(i => i.status)).toEqual(['SUCCESS', 'FAILED', 'PENDING']);
  });
});
