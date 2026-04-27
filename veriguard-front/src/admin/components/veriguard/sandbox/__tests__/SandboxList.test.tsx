import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import type { SandboxOutput } from '../../../../../actions/veriguard/veriguard-actions';

const fakeSandboxes: SandboxOutput[] = [
  {
    sandbox_id: 'sb-001',
    sandbox_name: '勒索沙箱',
    sandbox_description: '勒索软件分析',
    sandbox_network_policy: 'DENY_ALL',
    sandbox_network_rules: [],
    sandbox_auto_restore_enabled: true,
    sandbox_supported_sample_types: ['RANSOMWARE'],
    sandbox_status: 'ACTIVE',
    sandbox_created_at: '2025-01-01T00:00:00Z',
    sandbox_updated_at: '2025-01-01T00:00:00Z',
  },
  {
    sandbox_id: 'sb-002',
    sandbox_name: '挖矿沙箱',
    sandbox_description: '挖矿样本分析',
    sandbox_network_policy: 'DENY_ALL',
    sandbox_network_rules: [
      {
        rule_direction: 'EGRESS',
        rule_action: 'DENY',
        rule_protocol: 'TCP',
        rule_cidr: '0.0.0.0/0',
        rule_ports: 'all',
      },
    ],
    sandbox_auto_restore_enabled: false,
    sandbox_supported_sample_types: ['MINER'],
    sandbox_status: 'INACTIVE',
    sandbox_created_at: '2025-01-02T00:00:00Z',
    sandbox_updated_at: '2025-01-02T00:00:00Z',
  },
];

vi.mock('../../../../../actions/veriguard/veriguard-actions', () => ({
  fetchVeriguardSandboxes: vi.fn(async () => fakeSandboxes),
  createVeriguardSandbox: vi.fn(),
  updateVeriguardSandbox: vi.fn(),
  deleteVeriguardSandbox: vi.fn(),
  exportSandboxIptables: vi.fn(),
  exportSandboxRoutingConf: vi.fn(),
}));

// eslint-disable-next-line import/first -- mock must be defined before importing SUT
import SandboxList from '../SandboxList';

describe('SandboxList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(cleanup);

  test('renders sandbox names and chip status from fetched list', async () => {
    render(<SandboxList />);
    expect(await screen.findByText('勒索沙箱')).toBeInTheDocument();
    expect(screen.getByText('挖矿沙箱')).toBeInTheDocument();
    // 自动还原 chip — first row enabled, second row disabled
    expect(screen.getByText('已开启')).toBeInTheDocument();
    expect(screen.getByText('未开启')).toBeInTheDocument();
  });

  test('clicking 新建预设 opens SandboxDialog', async () => {
    render(<SandboxList />);
    await screen.findByText('勒索沙箱');
    fireEvent.click(screen.getByRole('button', { name: /新建预设/ }));
    expect(await screen.findByText('新建沙箱预设')).toBeInTheDocument();
  });

  test('filtering by status hides non-matching rows', async () => {
    render(<SandboxList />);
    await screen.findByText('勒索沙箱');

    // Open the 状态 select (first combobox in toolbar) and pick INACTIVE
    const [statusCombo] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(statusCombo);
    const listbox = await screen.findByRole('listbox');
    fireEvent.click(within(listbox).getByText('INACTIVE'));

    await waitFor(() => {
      expect(screen.queryByText('勒索沙箱')).not.toBeInTheDocument();
    });
    expect(screen.getByText('挖矿沙箱')).toBeInTheDocument();
  });

  test('clicking the row action menu shows 编辑 / 导出 / 删除 items', async () => {
    render(<SandboxList />);
    await screen.findByText('勒索沙箱');
    fireEvent.click(screen.getByRole('button', { name: '沙箱「勒索沙箱」操作' }));

    expect(await screen.findByRole('menuitem', { name: '编辑' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /导出 iptables/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: /导出 routing\.conf/ })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '删除' })).toBeInTheDocument();
  });
});
