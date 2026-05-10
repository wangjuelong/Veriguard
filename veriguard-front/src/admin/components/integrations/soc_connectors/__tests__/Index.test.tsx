import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import type { SocConnectorOutputDto } from '../../../../../actions/soc_connectors/soc-connector-actions';

const sampleConnectors: SocConnectorOutputDto[] = [
  {
    connector_id: 'elastic',
    display_name: 'Elastic SIEM',
    status: 'HEALTHY',
    message: null,
    available_rule_count: 12,
    last_checked_at: '2026-05-10T00:00:00Z',
  },
  {
    connector_id: 'splunk',
    display_name: 'Splunk',
    status: 'DISABLED',
    message: 'connector disabled',
    available_rule_count: null,
    last_checked_at: '2026-05-10T00:00:00Z',
  },
];

const fetchMock = vi.fn(async () => ({ data: sampleConnectors }));
const refreshMock = vi.fn(async (_id: string) => ({
  data: {
    connector_id: 'elastic',
    display_name: 'Elastic SIEM',
    status: 'DEGRADED' as const,
    message: 'rate limited',
    available_rule_count: 12,
    last_checked_at: '2026-05-10T01:00:00Z',
  },
}));

vi.mock('../../../../../actions/soc_connectors/soc-connector-actions', () => ({
  fetchSocConnectors: () => fetchMock(),
  refreshSocConnector: (id: string) => refreshMock(id),
  fetchSocConnectorRules: vi.fn(),
}));

// eslint-disable-next-line import/first -- mock must be defined before importing SUT
import SocConnectorsIndex from '../Index';

describe('SocConnectorsIndex', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(cleanup);

  test('renders fetched connector rows mapped from snake_case wire format', async () => {
    render(<SocConnectorsIndex />);
    expect(await screen.findByText('Elastic SIEM')).toBeInTheDocument();
    expect(screen.getByText('Splunk')).toBeInTheDocument();
    expect(screen.getByText('健康')).toBeInTheDocument();
    expect(screen.getByText('已禁用')).toBeInTheDocument();
  });

  test('clicking refresh-one calls refreshSocConnector and updates that row', async () => {
    render(<SocConnectorsIndex />);
    await screen.findByText('Elastic SIEM');

    const refreshButton = screen.getByLabelText('刷新 elastic 健康状态');
    fireEvent.click(refreshButton);

    await waitFor(() => {
      expect(refreshMock).toHaveBeenCalledWith('elastic');
    });
    expect(await screen.findByText('降级')).toBeInTheDocument();
    expect(await screen.findByText(/rate limited/)).toBeInTheDocument();
  });

  test('clicking refresh-all re-fetches the full list', async () => {
    render(<SocConnectorsIndex />);
    await screen.findByText('Elastic SIEM');

    fireEvent.click(screen.getByLabelText('刷新所有 SOC connector 健康状态'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });
  });
});
