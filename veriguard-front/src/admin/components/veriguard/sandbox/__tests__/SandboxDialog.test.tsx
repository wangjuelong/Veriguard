import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import SandboxDialog from '../SandboxDialog';

afterEach(cleanup);

const baseValue = {
  sandbox_name: '勒索沙箱',
  sandbox_description: '',
  sandbox_network_policy: 'DENY_ALL' as const,
  sandbox_network_rules: [],
  sandbox_auto_restore_enabled: true,
  sandbox_supported_sample_types: ['RANSOMWARE' as const],
  sandbox_status: 'ACTIVE' as const,
};

describe('SandboxDialog', () => {
  test('save button enabled when network rules empty (修订点)', () => {
    render(<SandboxDialog open mode="create" initialValue={baseValue}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeEnabled();
  });

  test('save button disabled when name blank', () => {
    render(<SandboxDialog open mode="create"
      initialValue={{ ...baseValue, sandbox_name: '' }}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('save button disabled when no sample types', () => {
    render(<SandboxDialog open mode="create"
      initialValue={{ ...baseValue, sandbox_supported_sample_types: [] }}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('save button disabled when auto restore off', () => {
    render(<SandboxDialog open mode="create"
      initialValue={{ ...baseValue, sandbox_auto_restore_enabled: false }}
      onCancel={() => {}} onSubmit={() => {}} />);
    expect(screen.getByRole('button', { name: /保存/ })).toBeDisabled();
  });

  test('submit returns the current form value', () => {
    const onSubmit = vi.fn();
    render(<SandboxDialog open mode="create" initialValue={baseValue}
      onCancel={() => {}} onSubmit={onSubmit} />);
    fireEvent.click(screen.getByRole('button', { name: /保存/ }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ sandbox_name: '勒索沙箱' }));
  });
});
