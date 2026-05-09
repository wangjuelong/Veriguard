import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import { type EdgeConditionTree } from '../attackChainEditorTypes';
import ConditionEdgePopover from '../ConditionEdgePopover';

afterEach(cleanup);

const initialLeaf: EdgeConditionTree = {
  kind: 'leaf',
  dimension: 'PREVENTION',
  status: 'ANY_FAILED',
};

describe('ConditionEdgePopover', () => {
  test('初次渲染 → 显示叶子（维度 / 状态 select + 加条件按钮）', () => {
    const anchor = document.createElement('div');
    document.body.appendChild(anchor);
    render(
      <ConditionEdgePopover
        open
        anchorEl={anchor}
        initialValue={initialLeaf}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    expect(screen.getByLabelText('维度')).toBeInTheDocument();
    expect(screen.getByLabelText('状态')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /加条件/ })).toBeInTheDocument();
  });

  test('应用 → onSubmit 拿到当前 tree', () => {
    const onSubmit = vi.fn();
    const anchor = document.createElement('div');
    render(
      <ConditionEdgePopover
        open
        anchorEl={anchor}
        initialValue={initialLeaf}
        onCancel={() => {}}
        onSubmit={onSubmit}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /应用/ }));
    expect(onSubmit).toHaveBeenCalledWith(initialLeaf);
  });

  test('点 "加条件" → 叶子提升为带 2 个孩子的 AND 组', () => {
    const onSubmit = vi.fn();
    const anchor = document.createElement('div');
    render(
      <ConditionEdgePopover
        open
        anchorEl={anchor}
        initialValue={initialLeaf}
        onCancel={() => {}}
        onSubmit={onSubmit}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /加条件/ }));
    fireEvent.click(screen.getByRole('button', { name: /应用/ }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'group',
        op: 'AND',
        children: expect.arrayContaining([
          expect.objectContaining({ kind: 'leaf' }),
          expect.objectContaining({ kind: 'leaf' }),
        ]),
      }),
    );
  });

  test('cancel 调用 onCancel', () => {
    const onCancel = vi.fn();
    const anchor = document.createElement('div');
    render(
      <ConditionEdgePopover
        open
        anchorEl={anchor}
        initialValue={initialLeaf}
        onCancel={onCancel}
        onSubmit={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /取消/ }));
    expect(onCancel).toHaveBeenCalled();
  });

  test('group 渲染 → 显示 AND/OR toggle + 子项', () => {
    const group: EdgeConditionTree = {
      kind: 'group',
      op: 'OR',
      children: [
        {
          kind: 'leaf',
          dimension: 'PREVENTION',
          status: 'ANY_FAILED',
        },
        {
          kind: 'leaf',
          dimension: 'DETECTION',
          status: 'ANY_SUCCESS',
        },
      ],
    };
    const anchor = document.createElement('div');
    render(
      <ConditionEdgePopover
        open
        anchorEl={anchor}
        initialValue={group}
        onCancel={() => {}}
        onSubmit={() => {}}
      />,
    );
    const orToggle = screen.getByRole('button', { name: 'OR' });
    expect(orToggle).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getAllByLabelText('维度')).toHaveLength(2);
  });
});
