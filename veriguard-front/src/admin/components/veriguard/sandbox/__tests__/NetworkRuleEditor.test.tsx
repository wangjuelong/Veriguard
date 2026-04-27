import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import NetworkRuleEditor from '../NetworkRuleEditor';

describe('NetworkRuleEditor', () => {
  afterEach(cleanup);

  test('renders empty state when no rules', () => {
    render(<NetworkRuleEditor value={[]} onChange={() => {}} />);
    expect(screen.getByText(/尚未配置规则/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /添加规则/ })).toBeEnabled();
  });

  test('clicking add appends a rule', () => {
    const onChange = vi.fn();
    render(<NetworkRuleEditor value={[]} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button', { name: /添加规则/ }));
    expect(onChange).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ rule_protocol: 'TCP' })]));
  });

  test('removing the last rule yields empty array (allowed)', () => {
    const onChange = vi.fn();
    render(
      <NetworkRuleEditor
        value={[{
          rule_direction: 'EGRESS',
          rule_action: 'DENY',
          rule_protocol: 'TCP',
          rule_cidr: '0.0.0.0/0',
          rule_ports: 'all',
        }]}
        onChange={onChange}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /删除规则/ }));
    expect(onChange).toHaveBeenCalledWith([]);
  });

  test('ICMP protocol disables port input and forces "none"', () => {
    const onChange = vi.fn();
    render(
      <NetworkRuleEditor
        value={[{
          rule_direction: 'EGRESS',
          rule_action: 'ALLOW',
          rule_protocol: 'ICMP',
          rule_cidr: '10.0.0.0/8',
          rule_ports: 'none',
        }]}
        onChange={onChange}
      />,
    );
    expect(screen.getByDisplayValue('none')).toBeDisabled();
  });

  test('invalid CIDR shows inline error', () => {
    render(
      <NetworkRuleEditor
        value={[{
          rule_direction: 'EGRESS',
          rule_action: 'DENY',
          rule_protocol: 'TCP',
          rule_cidr: 'not-a-cidr',
          rule_ports: '80',
        }]}
        onChange={() => {}}
      />,
    );
    expect(screen.getByText(/CIDR 格式无效/)).toBeInTheDocument();
  });
});
