import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import ParameterSetEditDialog from '../ParameterSetEditDialog';
import { type ValidationParameterSetValue } from '../validationParameterSetTypes';

afterEach(cleanup);

const baseValue: ValidationParameterSetValue = {
  name: '严格',
  description: '默认 100/30min',
  is_template: false,
  default_targets: [],
  prevention_expected_score: 100,
  prevention_expiration_seconds: 1800,
  detection_expected_score: 100,
  detection_expiration_seconds: 1800,
  soc_correlation_rules: [],
  tags: [],
};

describe('ParameterSetEditDialog', () => {
  test('mode=create 显示 "新建参数集" 标题', () => {
    render(
      <ParameterSetEditDialog
        open
        mode="create"
        initialValue={baseValue}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByText(/新建参数集/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^保存$/ })).toBeInTheDocument();
  });

  test('mode=edit 普通参数集 → 字段可改', () => {
    render(
      <ParameterSetEditDialog
        open
        mode="edit"
        initialValue={baseValue}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByText(/编辑参数集/)).toBeInTheDocument();
    const nameInput = screen.getByLabelText('名称') as HTMLInputElement;
    expect(nameInput).not.toBeDisabled();
  });

  test('mode=edit 模板参数集 → 字段只读 + 显示锁 + 信息提示 + 隐藏保存按钮', () => {
    render(
      <ParameterSetEditDialog
        open
        mode="edit"
        initialValue={{
          ...baseValue,
          is_template: true,
        }}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByTestId('param-set-template-lock')).toBeInTheDocument();
    expect(screen.getByText(/查看模板参数集/)).toBeInTheDocument();
    expect(screen.getByText(/模板参数集只读/)).toBeInTheDocument();
    expect(screen.getByLabelText('名称')).toBeDisabled();
    expect(screen.queryByRole('button', { name: /^保存$/ })).not.toBeInTheDocument();
  });

  test('mode=duplicate → 标题 "复制模板并修改"，提交后 is_template 强制 false', () => {
    const onSubmit = vi.fn();
    render(
      <ParameterSetEditDialog
        open
        mode="duplicate"
        initialValue={{
          ...baseValue,
          is_template: true,
          name: '严格(复制)',
        }}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={onSubmit}
      />,
    );
    expect(screen.getByText(/复制模板并修改/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /复制并保存/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /复制并保存/ }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      is_template: false,
      name: '严格(复制)',
    }));
  });

  test('名称为空 → 保存禁用', () => {
    render(
      <ParameterSetEditDialog
        open
        mode="create"
        initialValue={{
          ...baseValue,
          name: '',
        }}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /^保存$/ })).toBeDisabled();
  });

  test('SOC 规则字段缺失 → 保存禁用', () => {
    render(
      <ParameterSetEditDialog
        open
        mode="create"
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
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /^保存$/ })).toBeDisabled();
  });

  test('完整 SOC 规则 → 保存可用', () => {
    render(
      <ParameterSetEditDialog
        open
        mode="create"
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
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /^保存$/ })).toBeEnabled();
  });

  test('cancel → onCancel', () => {
    const onCancel = vi.fn();
    render(
      <ParameterSetEditDialog
        open
        mode="create"
        initialValue={baseValue}
        availableConnectorIds={['elastic']}
        onCancel={onCancel}
        onSubmit={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /取消/ }));
    expect(onCancel).toHaveBeenCalled();
  });

  test('default_targets / tags 渲染为 chip', () => {
    render(
      <ParameterSetEditDialog
        open
        mode="edit"
        initialValue={{
          ...baseValue,
          default_targets: [
            {
              kind: 'asset',
              id: 'a1',
              display_name: 'web-01',
            },
          ],
          tags: [{
            id: 't1',
            name: 'pci',
          }],
        }}
        availableConnectorIds={['elastic']}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByTestId('param-set-target-0')).toBeInTheDocument();
    expect(screen.getByTestId('param-set-tag-0')).toBeInTheDocument();
    expect(screen.getByText(/web-01/)).toBeInTheDocument();
    expect(screen.getByText('pci')).toBeInTheDocument();
  });
});
