import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import DeleteConfirmDialog from '../DeleteConfirmDialog';

afterEach(cleanup);

describe('DeleteConfirmDialog', () => {
  test('renders title and message when open', () => {
    render(
      <DeleteConfirmDialog
        open
        title="删除沙箱预设"
        message="确定删除沙箱预设「勒索沙箱」？此操作不可恢复。"
        onCancel={() => {}}
        onConfirm={() => {}}
      />,
    );
    expect(screen.getByText('删除沙箱预设')).toBeInTheDocument();
    expect(screen.getByText(/确定删除沙箱预设「勒索沙箱」/)).toBeInTheDocument();
  });

  test('calls onCancel when 取消 clicked', () => {
    const onCancel = vi.fn();
    render(
      <DeleteConfirmDialog
        open
        title="删除沙箱预设"
        message="确定删除？"
        onCancel={onCancel}
        onConfirm={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  test('calls onConfirm when 确认删除 clicked', () => {
    const onConfirm = vi.fn();
    render(
      <DeleteConfirmDialog
        open
        title="删除沙箱预设"
        message="确定删除？"
        onCancel={() => {}}
        onConfirm={onConfirm}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});
