import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import SocConnectorRulePicker from '../SocConnectorRulePicker';
import { type SocConnectorRuleOption } from '../socConnectorTypes';

afterEach(cleanup);

const sampleRules: SocConnectorRuleOption[] = [
  {
    ruleId: 'r1',
    displayName: 'Lateral Movement Detected',
    description: 'PsExec / WMI / SMB 横向',
    category: 'correlation',
  },
  {
    ruleId: 'r2',
    displayName: 'Privilege Escalation Sequence',
    category: 'correlation',
  },
  {
    ruleId: 'r3',
    displayName: 'Ransomware Kill Chain',
    description: '加密 + 删除影子',
    category: 'detection',
  },
];

describe('SocConnectorRulePicker', () => {
  test('渲染 connector header + 全部规则', () => {
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={sampleRules}
        onCancel={() => {}}
        onSelect={() => {}}
      />,
    );
    expect(screen.getByText('elastic')).toBeInTheDocument();
    expect(screen.getByText('Elastic SIEM')).toBeInTheDocument();
    expect(screen.getByTestId('soc-rule-r1')).toBeInTheDocument();
    expect(screen.getByTestId('soc-rule-r2')).toBeInTheDocument();
    expect(screen.getByTestId('soc-rule-r3')).toBeInTheDocument();
  });

  test('搜索框过滤 → 仅匹配 displayName / ruleId / category 的行', () => {
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={sampleRules}
        onCancel={() => {}}
        onSelect={() => {}}
      />,
    );
    fireEvent.change(screen.getByTestId('soc-rule-picker-search').querySelector('input')!, { target: { value: 'ransomware' } });
    expect(screen.getByTestId('soc-rule-r3')).toBeInTheDocument();
    expect(screen.queryByTestId('soc-rule-r1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('soc-rule-r2')).not.toBeInTheDocument();
  });

  test('搜索无匹配 → 显示 "未找到匹配" 提示', () => {
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={sampleRules}
        onCancel={() => {}}
        onSelect={() => {}}
      />,
    );
    fireEvent.change(screen.getByTestId('soc-rule-picker-search').querySelector('input')!, { target: { value: 'zzz' } });
    expect(screen.getByText(/未找到匹配「zzz」的规则/)).toBeInTheDocument();
  });

  test('点击规则 → 触发 onSelect 携带 rule 对象', () => {
    const onSelect = vi.fn();
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={sampleRules}
        onCancel={() => {}}
        onSelect={onSelect}
      />,
    );
    fireEvent.click(screen.getByTestId('soc-rule-r2'));
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({
        ruleId: 'r2',
        displayName: 'Privilege Escalation Sequence',
      }),
    );
  });

  test('initialSelectedRuleId → 该行 selected', () => {
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={sampleRules}
        initialSelectedRuleId="r2"
        onCancel={() => {}}
        onSelect={() => {}}
      />,
    );
    expect(screen.getByTestId('soc-rule-r2')).toHaveClass('Mui-selected');
    expect(screen.getByTestId('soc-rule-r1')).not.toHaveClass('Mui-selected');
  });

  test('loadError → 显示错误 Alert + 隐藏搜索 + 隐藏列表（spec §3.6 失败显式）', () => {
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={[]}
        loadError="connector unhealthy: auth failed"
        onCancel={() => {}}
        onSelect={() => {}}
      />,
    );
    expect(screen.getByTestId('soc-rule-picker-error')).toBeInTheDocument();
    expect(screen.getByText(/connector unhealthy: auth failed/)).toBeInTheDocument();
    expect(screen.queryByTestId('soc-rule-picker-search')).not.toBeInTheDocument();
    expect(screen.queryByTestId('soc-rule-picker-list')).not.toBeInTheDocument();
  });

  test('空 rules + 无 loadError → 提示 "connector 没有返回任何规则"', () => {
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={[]}
        onCancel={() => {}}
        onSelect={() => {}}
      />,
    );
    expect(screen.getByText(/没有返回任何规则/)).toBeInTheDocument();
  });

  test('cancel → onCancel', () => {
    const onCancel = vi.fn();
    render(
      <SocConnectorRulePicker
        open
        connectorId="elastic"
        connectorDisplayName="Elastic SIEM"
        rules={sampleRules}
        onCancel={onCancel}
        onSelect={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /取消/ }));
    expect(onCancel).toHaveBeenCalled();
  });
});
