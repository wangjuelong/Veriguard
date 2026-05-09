import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import ParameterSetCard from '../ParameterSetCard';
import { type ValidationParameterSetSummary } from '../validationParameterSetTypes';

afterEach(cleanup);

const baseSummary: ValidationParameterSetSummary = {
  id: 'ps1',
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
  created_at: '2026-05-01T00:00:00Z',
  updated_at: '2026-05-09T00:00:00Z',
};

describe('ParameterSetCard', () => {
  test('普通参数集 → 渲染名称 + 副信息 + Edit/Duplicate/Delete 按钮可点', () => {
    const onEdit = vi.fn();
    const onDuplicate = vi.fn();
    const onDelete = vi.fn();
    render(
      <ParameterSetCard
        parameterSet={baseSummary}
        onEdit={onEdit}
        onDuplicate={onDuplicate}
        onDelete={onDelete}
      />,
    );
    expect(screen.getByText('严格')).toBeInTheDocument();
    expect(screen.getByText(/默认 100\/30min/)).toBeInTheDocument();
    expect(screen.getByText(/PREVENTION 100 \/ 30min/)).toBeInTheDocument();
    expect(screen.getByText(/DETECTION 100 \/ 30min/)).toBeInTheDocument();
    expect(screen.getByText(/SOC 0 条/)).toBeInTheDocument();
    expect(screen.queryByTestId('param-set-card-lock')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('param-set-card-edit'));
    expect(onEdit).toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('param-set-card-duplicate'));
    expect(onDuplicate).toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('param-set-card-delete'));
    expect(onDelete).toHaveBeenCalled();
  });

  test('模板参数集 → 显示锁 + 模板 chip + 隐藏 Edit/Delete + 启用 Duplicate', () => {
    const onEdit = vi.fn();
    const onDuplicate = vi.fn();
    const onDelete = vi.fn();
    render(
      <ParameterSetCard
        parameterSet={{
          ...baseSummary,
          is_template: true,
        }}
        onEdit={onEdit}
        onDuplicate={onDuplicate}
        onDelete={onDelete}
      />,
    );
    expect(screen.getByTestId('param-set-card-lock')).toBeInTheDocument();
    expect(screen.getByText('模板')).toBeInTheDocument();
    expect(screen.queryByTestId('param-set-card-edit')).not.toBeInTheDocument();
    expect(screen.queryByTestId('param-set-card-delete')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('param-set-card-duplicate'));
    expect(onDuplicate).toHaveBeenCalled();
    expect(onEdit).not.toHaveBeenCalled();
    expect(onDelete).not.toHaveBeenCalled();
  });

  test('SOC 规则 + tags 数量正确显示', () => {
    render(
      <ParameterSetCard
        parameterSet={{
          ...baseSummary,
          soc_correlation_rules: [
            {
              connector_id: 'elastic',
              rule_id: 'r1',
              display_name: 'A',
              match_window_seconds: 7200,
            },
            {
              connector_id: 'elastic',
              rule_id: 'r2',
              display_name: 'B',
              match_window_seconds: 7200,
            },
          ],
          tags: [
            {
              id: 't1',
              name: 'pci',
            },
            {
              id: 't2',
              name: 'soc2',
            },
          ],
          default_targets: [{
            kind: 'asset',
            id: 'a1',
          }],
        }}
      />,
    );
    expect(screen.getByText(/SOC 2 条/)).toBeInTheDocument();
    expect(screen.getByText(/目标 1/)).toBeInTheDocument();
    expect(screen.getByText('pci')).toBeInTheDocument();
    expect(screen.getByText('soc2')).toBeInTheDocument();
  });

  test('未传 callback → 不渲染对应按钮', () => {
    render(<ParameterSetCard parameterSet={baseSummary} />);
    expect(screen.queryByTestId('param-set-card-edit')).not.toBeInTheDocument();
    expect(screen.queryByTestId('param-set-card-duplicate')).not.toBeInTheDocument();
    expect(screen.queryByTestId('param-set-card-delete')).not.toBeInTheDocument();
  });
});
