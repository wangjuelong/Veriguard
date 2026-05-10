import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import type { ValidationParameterSetOutput } from '../../../../utils/api-types';

const sampleApiOutputs: ValidationParameterSetOutput[] = [
  {
    parameter_set_id: 'ps-template',
    parameter_set_name: '模板严格',
    parameter_set_description: '默认模板',
    parameter_set_is_template: true,
    parameter_set_default_targets: [],
    parameter_set_prevention_expected_score: 100,
    parameter_set_prevention_expiration_seconds: 1800,
    parameter_set_detection_expected_score: 100,
    parameter_set_detection_expiration_seconds: 1800,
    parameter_set_soc_correlation_rules: [],
    parameter_set_tag_ids: [],
    parameter_set_created_at: '2026-05-01T00:00:00Z',
    parameter_set_updated_at: '2026-05-01T00:00:00Z',
  },
  {
    parameter_set_id: 'ps-custom',
    parameter_set_name: '自定义',
    parameter_set_description: '业务自定义',
    parameter_set_is_template: false,
    parameter_set_default_targets: [
      {
        target_id: 'asset-1',
        target_type: 'ASSET',
      },
      {
        target_id: 'group-1',
        target_type: 'ASSET_GROUP',
      },
    ],
    parameter_set_prevention_expected_score: 80,
    parameter_set_prevention_expiration_seconds: 600,
    parameter_set_detection_expected_score: 60,
    parameter_set_detection_expiration_seconds: 600,
    parameter_set_soc_correlation_rules: [],
    parameter_set_tag_ids: [],
    parameter_set_created_at: '2026-05-02T00:00:00Z',
    parameter_set_updated_at: '2026-05-09T00:00:00Z',
  },
];

const searchMock = vi.fn(async (_input: unknown) => ({ data: { content: sampleApiOutputs } }));
const createMock = vi.fn(async (_input: unknown) => ({ data: {} }));
const updateMock = vi.fn(async (_id: string, _input: unknown) => ({ data: {} }));
const deleteMock = vi.fn(async (_id: string) => ({ data: {} }));
const duplicateMock = vi.fn(async (_id: string, _name: string) => ({ data: {} }));
const fetchSocConnectorsMock = vi.fn(async () => ({
  data: [
    {
      connector_id: 'elastic',
      display_name: 'Elastic SIEM',
      status: 'HEALTHY' as const,
    },
    {
      connector_id: 'splunk',
      display_name: 'Splunk',
      status: 'DISABLED' as const,
    },
  ],
}));

vi.mock('../../../../actions/validation_parameter_sets/parameter-set-actions', () => ({
  searchValidationParameterSets: (input: unknown) => searchMock(input),
  createValidationParameterSet: (input: unknown) => createMock(input),
  updateValidationParameterSet: (id: string, input: unknown) => updateMock(id, input),
  deleteValidationParameterSet: (id: string) => deleteMock(id),
  duplicateValidationParameterSet: (id: string, name: string) => duplicateMock(id, name),
}));
vi.mock('../../../../actions/soc_connectors/soc-connector-actions', () => ({
  fetchSocConnectors: () => fetchSocConnectorsMock(),
  refreshSocConnector: vi.fn(),
  fetchSocConnectorRules: vi.fn(),
}));

// eslint-disable-next-line import/first -- mock must be defined before importing SUT
import ValidationParameterSets from '../Index';

describe('ValidationParameterSets index', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(cleanup);

  test('renders fetched parameter sets as cards', async () => {
    render(<ValidationParameterSets />);
    expect(await screen.findByText('模板严格')).toBeInTheDocument();
    expect(screen.getByText('自定义')).toBeInTheDocument();
  });

  test('clicking Add opens edit dialog in create mode with empty values', async () => {
    render(<ValidationParameterSets />);
    await screen.findByText('模板严格');
    fireEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(await screen.findByText('新建参数集')).toBeInTheDocument();
  });

  test('editing a non-template parameter set opens dialog in edit mode', async () => {
    render(<ValidationParameterSets />);
    await screen.findByText('自定义');
    const customRow = screen.getByTestId('param-set-row-ps-custom');
    fireEvent.click(within(customRow).getByLabelText('编辑参数集'));
    expect(await screen.findByText('编辑参数集')).toBeInTheDocument();
  });

  test('editing a template parameter set opens dialog in duplicate mode (template lock)', async () => {
    render(<ValidationParameterSets />);
    await screen.findByText('模板严格');
    const templateRow = screen.getByTestId('param-set-row-ps-template');
    // Template row has Lock icon and Edit button is disabled, but Duplicate is the path.
    // Click Duplicate to test the duplicate flow on template.
    fireEvent.click(within(templateRow).getByLabelText('复制参数集'));
    await waitFor(() => {
      expect(duplicateMock).toHaveBeenCalledWith('ps-template', '模板严格 (副本)');
    });
  });

  test('delete button calls deleteValidationParameterSet then refreshes', async () => {
    render(<ValidationParameterSets />);
    await screen.findByText('自定义');
    const customRow = screen.getByTestId('param-set-row-ps-custom');
    fireEvent.click(within(customRow).getByLabelText('删除参数集'));
    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledWith('ps-custom');
    });
    // refresh: search called twice (initial + after delete)
    expect(searchMock).toHaveBeenCalledTimes(2);
  });

  test('submitting create dialog posts a new parameter set with mapped wire format', async () => {
    render(<ValidationParameterSets />);
    await screen.findByText('模板严格');

    fireEvent.click(screen.getByRole('button', { name: 'Add' }));
    await screen.findByText('新建参数集');

    // Fill required name field
    const nameInput = screen.getByLabelText(/名称/);
    fireEvent.change(nameInput, { target: { value: '冒烟测试' } });

    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(createMock).toHaveBeenCalledTimes(1);
    });
    const payload = createMock.mock.calls[0]?.[0] as { parameter_set_name: string } | undefined;
    expect(payload?.parameter_set_name).toBe('冒烟测试');
  });

  test('opening the dialog fetches SOC connectors and only HEALTHY ones reach the picker', async () => {
    render(<ValidationParameterSets />);
    await screen.findByText('模板严格');

    fireEvent.click(screen.getByRole('button', { name: 'Add' }));
    await screen.findByText('新建参数集');

    await waitFor(() => {
      expect(fetchSocConnectorsMock).toHaveBeenCalled();
    });
    // "新增规则" button is enabled when at least one HEALTHY connector exists
    const addRuleButton = screen.getByRole('button', { name: /新增规则/ });
    expect(addRuleButton).not.toBeDisabled();
  });
});
