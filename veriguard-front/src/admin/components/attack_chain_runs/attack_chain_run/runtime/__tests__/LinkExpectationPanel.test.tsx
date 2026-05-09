import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test } from 'vitest';

import { type LinkExpectationItem } from '../attackChainRuntimeTypes';
import LinkExpectationPanel from '../LinkExpectationPanel';

afterEach(cleanup);

describe('LinkExpectationPanel', () => {
  test('空列表 → 显示提示 "未配 SOC correlation rule"', () => {
    render(<LinkExpectationPanel items={[]} />);
    expect(screen.getByTestId('link-expectation-panel-empty')).toBeInTheDocument();
    expect(screen.getByText(/未配 SOC correlation rule/)).toBeInTheDocument();
  });

  test('SUCCESS 状态 → 显示 incident 引用 + 分数', () => {
    const items: LinkExpectationItem[] = [
      {
        id: 'le1',
        connectorId: 'elastic',
        ruleDisplayName: 'Lateral Movement Detected',
        status: 'SUCCESS',
        incidentRef: 'incident #4521',
        score: 100,
        expectedScore: 100,
      },
    ];
    render(<LinkExpectationPanel items={items} />);
    expect(screen.getByText('Lateral Movement Detected')).toBeInTheDocument();
    expect(screen.getByText(/elastic/)).toBeInTheDocument();
    expect(screen.getByText(/incident #4521/)).toBeInTheDocument();
    expect(screen.getByText(/100\/100/)).toBeInTheDocument();
  });

  test('FAILED 状态 → 显示原因 "no match (window 2h)"', () => {
    const items: LinkExpectationItem[] = [
      {
        id: 'le2',
        connectorId: 'elastic',
        ruleDisplayName: 'Ransomware Kill Chain',
        status: 'FAILED',
        failureReason: 'no match (window 2h)',
      },
    ];
    render(<LinkExpectationPanel items={items} />);
    expect(screen.getByText(/no match \(window 2h\)/)).toBeInTheDocument();
  });

  test('PENDING 状态 → 显示 "等待 SOC 查询"', () => {
    const items: LinkExpectationItem[] = [
      {
        id: 'le3',
        connectorId: 'elastic',
        ruleDisplayName: 'Privilege Escalation Sequence',
        status: 'PENDING',
      },
    ];
    render(<LinkExpectationPanel items={items} />);
    expect(screen.getByText(/等待 SOC 查询/)).toBeInTheDocument();
  });

  test('UNKNOWN 状态 → 显示 unknownReason', () => {
    const items: LinkExpectationItem[] = [
      {
        id: 'le4',
        connectorId: 'splunk',
        ruleDisplayName: 'C2 Callback',
        status: 'UNKNOWN',
        unknownReason: 'soc query failed: timeout',
      },
    ];
    render(<LinkExpectationPanel items={items} />);
    expect(screen.getByText(/soc query failed: timeout/)).toBeInTheDocument();
  });

  test('多种状态混合 → 各自渲染', () => {
    const items: LinkExpectationItem[] = [
      {
        id: '1',
        connectorId: 'elastic',
        ruleDisplayName: 'A',
        status: 'SUCCESS',
        incidentRef: '#1',
      },
      {
        id: '2',
        connectorId: 'elastic',
        ruleDisplayName: 'B',
        status: 'FAILED',
        failureReason: 'no match',
      },
      {
        id: '3',
        connectorId: 'elastic',
        ruleDisplayName: 'C',
        status: 'PENDING',
      },
    ];
    render(<LinkExpectationPanel items={items} />);
    expect(screen.getByTestId('link-expectation-success')).toBeInTheDocument();
    expect(screen.getByTestId('link-expectation-failed')).toBeInTheDocument();
    expect(screen.getByTestId('link-expectation-pending')).toBeInTheDocument();
  });
});
