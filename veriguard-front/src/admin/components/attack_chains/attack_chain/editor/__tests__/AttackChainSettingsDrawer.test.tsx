import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import { type AttackChainSettingsValue } from '../attackChainEditorTypes';
import AttackChainSettingsDrawer from '../AttackChainSettingsDrawer';

afterEach(cleanup);

const baseValue: AttackChainSettingsValue = {
  execution_mode: 'STOP_ON_BLOCK',
  validation_parameter_set_id: null,
  soc_correlation_rules: [],
};

const paramSets = [
  {
    id: 'ps1',
    name: '严格',
    is_template: false,
  },
  {
    id: 'ps2',
    name: '宽松',
    is_template: true,
  },
];

describe('AttackChainSettingsDrawer', () => {
  test('显示当前 execution_mode 与 hint 文案', () => {
    render(
      <AttackChainSettingsDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /截停模式/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText(/任一节点 PREVENTION 被防御工具拦下/)).toBeInTheDocument();
  });

  test('保存 → onSubmit 拿到 form 当前值', () => {
    const onSubmit = vi.fn();
    render(
      <AttackChainSettingsDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={onSubmit}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /保存/ }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ execution_mode: 'STOP_ON_BLOCK' }));
  });

  test('availableConnectorIds 为空 → 新增按钮禁用 + 警告文案', () => {
    render(
      <AttackChainSettingsDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        availableConnectorIds={[]}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /新增规则/ })).toBeDisabled();
    expect(screen.getByText(/未注册任何 SOC connector/)).toBeInTheDocument();
  });

  test('点击新增规则 → 列表多一行带默认 connector_id', () => {
    const onSubmit = vi.fn();
    render(
      <AttackChainSettingsDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        availableConnectorIds={['elastic', 'splunk']}
        onCancel={() => {}}
        onSubmit={onSubmit}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /新增规则/ }));
    expect(screen.getByLabelText(/规则 ID/)).toBeInTheDocument();
    // 必填字段未填 → 保存禁用
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('cancel 调用 onCancel', () => {
    const onCancel = vi.fn();
    render(
      <AttackChainSettingsDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        availableConnectorIds={['elastic']}
        onCancel={onCancel}
        onSubmit={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /取消/ }));
    expect(onCancel).toHaveBeenCalled();
  });

  test('rules 必填字段缺失 → 保存禁用', () => {
    render(
      <AttackChainSettingsDrawer
        open
        initialValue={{
          ...baseValue,
          soc_correlation_rules: [
            {
              connector_id: 'elastic',
              rule_id: '',
              display_name: '',
              match_window_seconds: 7200,
            },
          ],
        }}
        parameterSetOptions={paramSets}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('rules 完整填写 → 保存可用', () => {
    render(
      <AttackChainSettingsDrawer
        open
        initialValue={{
          ...baseValue,
          soc_correlation_rules: [
            {
              connector_id: 'elastic',
              rule_id: 'r1',
              display_name: 'Lateral Movement',
              match_window_seconds: 7200,
            },
          ],
        }}
        parameterSetOptions={paramSets}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /保存/ })).toBeEnabled();
  });
});
