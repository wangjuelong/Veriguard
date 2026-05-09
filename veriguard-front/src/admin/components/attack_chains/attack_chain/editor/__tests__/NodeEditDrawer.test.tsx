import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import { type AttackChainNodeEditValue } from '../attackChainEditorTypes';
import NodeEditDrawer from '../NodeEditDrawer';

afterEach(cleanup);

const baseValue: AttackChainNodeEditValue = {
  node_title: '横向移动',
  node_description: '',
  validation_parameter_set_id: null,
  repeat_count: 1,
  repeat_interval_seconds: 0,
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

describe('NodeEditDrawer', () => {
  test('显示初始值', () => {
    render(
      <NodeEditDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByDisplayValue('横向移动')).toBeInTheDocument();
  });

  test('节点名为空 → 保存禁用', () => {
    render(
      <NodeEditDrawer
        open
        initialValue={{
          ...baseValue,
          node_title: '',
        }}
        parameterSetOptions={paramSets}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('repeat_count 输入 → 受限 ≥ 1', () => {
    const onSubmit = vi.fn();
    render(
      <NodeEditDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        onCancel={() => {}}
        onSubmit={onSubmit}
      />,
    );
    const repeatInput = screen.getByLabelText(/重复次数/);
    fireEvent.change(repeatInput, { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /保存/ }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ repeat_count: 1 }));
  });

  test('保存提交 onSubmit 携带当前值', () => {
    const onSubmit = vi.fn();
    render(
      <NodeEditDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        onCancel={() => {}}
        onSubmit={onSubmit}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /保存/ }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ node_title: '横向移动' }));
  });

  test('cancel → onCancel', () => {
    const onCancel = vi.fn();
    render(
      <NodeEditDrawer
        open
        initialValue={baseValue}
        parameterSetOptions={paramSets}
        onCancel={onCancel}
        onSubmit={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /取消/ }));
    expect(onCancel).toHaveBeenCalled();
  });
});
