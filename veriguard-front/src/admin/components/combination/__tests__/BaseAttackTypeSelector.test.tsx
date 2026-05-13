import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import type {
  BaseAttackTypeOutput,
  BaseAttackTypePageOutput,
} from '../../../../actions/combination/combination-actions';

const buildType = (
  overrides: Partial<BaseAttackTypeOutput> & Pick<BaseAttackTypeOutput, 'base_attack_type_name' | 'base_attack_type_category' | 'base_attack_type_target_layer'>,
): BaseAttackTypeOutput => ({
  base_attack_type_id: `id-${overrides.base_attack_type_name}`,
  base_attack_type_display_label: `Label ${overrides.base_attack_type_name}`,
  base_attack_type_description: 'desc',
  base_attack_type_severity_score: 50,
  base_attack_type_default_payload: 'payload',
  base_attack_type_attack_pattern_id: null,
  base_attack_type_created_at: '2026-05-01T00:00:00Z',
  base_attack_type_updated_at: '2026-05-01T00:00:00Z',
  ...overrides,
});

const fakeTypes: BaseAttackTypeOutput[] = [
  buildType({
    base_attack_type_name: 'sql_injection',
    base_attack_type_display_label: 'SQL 注入',
    base_attack_type_category: 'web_injection',
    base_attack_type_target_layer: 'application',
  }),
  buildType({
    base_attack_type_name: 'xss_reflected',
    base_attack_type_display_label: 'XSS 反射',
    base_attack_type_category: 'web_injection',
    base_attack_type_target_layer: 'application',
  }),
  buildType({
    base_attack_type_name: 'kerberoast',
    base_attack_type_display_label: 'Kerberoast 凭证爆破',
    base_attack_type_category: 'credential',
    base_attack_type_target_layer: 'host',
  }),
  buildType({
    base_attack_type_name: 'arp_spoof',
    base_attack_type_display_label: 'ARP 欺骗',
    base_attack_type_category: 'network_protocol',
    base_attack_type_target_layer: 'traffic',
  }),
];

const buildPage = (content: BaseAttackTypeOutput[]): BaseAttackTypePageOutput => ({
  content,
  page: 0,
  size: content.length,
  total_elements: content.length,
  total_pages: 1,
});

const listBaseAttackTypesMock = vi.fn();

vi.mock('../../../../actions/combination/combination-actions', () => (
  { listBaseAttackTypes: (...args: unknown[]) => listBaseAttackTypesMock(...args) }
));

// eslint-disable-next-line import/first -- mock must be defined before importing SUT
import BaseAttackTypeSelector from '../BaseAttackTypeSelector';

describe('BaseAttackTypeSelector', () => {
  beforeEach(() => {
    listBaseAttackTypesMock.mockReset();
  });
  afterEach(cleanup);

  test('shows loading state then renders 9 categories with counts after fetch', async () => {
    listBaseAttackTypesMock.mockResolvedValueOnce(buildPage(fakeTypes));
    const onChange = vi.fn();
    render(<BaseAttackTypeSelector selectedNames={[]} onSelectionChange={onChange} />);

    expect(screen.getByLabelText('loading-base-attack-types')).toBeInTheDocument();

    expect(await screen.findByText(/Web 注入/)).toBeInTheDocument();
    expect(screen.getByText(/凭证攻击/)).toBeInTheDocument();
    expect(screen.getByText(/网络协议/)).toBeInTheDocument();
    // Header chip "0 / 4" reflects total available types.
    expect(screen.getByText('0 / 4')).toBeInTheDocument();
  });

  test('renders error alert when fetch fails', async () => {
    listBaseAttackTypesMock.mockRejectedValueOnce(new Error('boom-network'));
    render(<BaseAttackTypeSelector selectedNames={[]} onSelectionChange={vi.fn()} />);
    expect(await screen.findByText(/boom-network/)).toBeInTheDocument();
  });

  test('search filter narrows visible categories by display_label', async () => {
    listBaseAttackTypesMock.mockResolvedValueOnce(buildPage(fakeTypes));
    render(<BaseAttackTypeSelector selectedNames={[]} onSelectionChange={vi.fn()} />);
    await screen.findByText(/Web 注入/);

    fireEvent.change(
      screen.getByLabelText('search-base-attack-types'),
      { target: { value: 'kerberoast' } },
    );

    await waitFor(() => {
      expect(screen.queryByText(/Web 注入/)).not.toBeInTheDocument();
    });
    expect(screen.getByText(/凭证攻击/)).toBeInTheDocument();
  });

  test('target_layer chip filter restricts to selected layer', async () => {
    listBaseAttackTypesMock.mockResolvedValueOnce(buildPage(fakeTypes));
    render(<BaseAttackTypeSelector selectedNames={[]} onSelectionChange={vi.fn()} />);
    await screen.findByText(/Web 注入/);

    // Click the host chip to filter by host target layer.
    fireEvent.click(screen.getByText('主机 (host)'));

    await waitFor(() => {
      expect(screen.queryByText(/Web 注入/)).not.toBeInTheDocument();
    });
    expect(screen.getByText(/凭证攻击/)).toBeInTheDocument();
    expect(screen.queryByText(/网络协议/)).not.toBeInTheDocument();
  });

  test('clicking category select-all toggles all names in that category', async () => {
    listBaseAttackTypesMock.mockResolvedValueOnce(buildPage(fakeTypes));
    const onChange = vi.fn();
    render(<BaseAttackTypeSelector selectedNames={[]} onSelectionChange={onChange} />);
    await screen.findByText(/Web 注入/);

    fireEvent.click(screen.getByLabelText('category-web_injection-select-all'));

    expect(onChange).toHaveBeenCalledTimes(1);
    const namesArg = onChange.mock.calls[0][0] as string[];
    expect(namesArg).toEqual(expect.arrayContaining(['sql_injection', 'xss_reflected']));
    expect(namesArg).toHaveLength(2);
  });

  test('selected counter chip updates with selectedNames prop', async () => {
    listBaseAttackTypesMock.mockResolvedValueOnce(buildPage(fakeTypes));
    render(
      <BaseAttackTypeSelector
        selectedNames={['sql_injection', 'kerberoast']}
        onSelectionChange={vi.fn()}
      />,
    );
    await screen.findByText(/Web 注入/);
    expect(screen.getByText('2 / 4')).toBeInTheDocument();
  });

  test('clear button resets selection via onSelectionChange([])', async () => {
    listBaseAttackTypesMock.mockResolvedValueOnce(buildPage(fakeTypes));
    const onChange = vi.fn();
    render(
      <BaseAttackTypeSelector
        selectedNames={['sql_injection']}
        onSelectionChange={onChange}
      />,
    );
    await screen.findByText(/Web 注入/);

    fireEvent.click(screen.getByRole('button', { name: '清空' }));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  test('disabled (enabled=false) skips fetch entirely', () => {
    render(
      <BaseAttackTypeSelector
        selectedNames={[]}
        onSelectionChange={vi.fn()}
        enabled={false}
      />,
    );
    expect(listBaseAttackTypesMock).not.toHaveBeenCalled();
  });
});
