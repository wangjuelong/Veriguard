import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import SocConnectorStatusList from '../SocConnectorStatusList';
import { type SocConnectorStatusItem } from '../socConnectorTypes';

afterEach(cleanup);

const baseItem: SocConnectorStatusItem = {
  connectorId: 'elastic',
  displayName: 'Elastic SIEM',
  status: 'HEALTHY',
  availableRuleCount: 12,
  lastCheckedAt: '2026-05-09T03:00:00Z',
};

describe('SocConnectorStatusList', () => {
  test('空列表 → 显示运维提示', () => {
    render(<SocConnectorStatusList items={[]} />);
    expect(screen.getByTestId('soc-status-list-empty')).toBeInTheDocument();
    expect(screen.getByText(/未注册任何 SOC connector/)).toBeInTheDocument();
  });

  test('HEALTHY 状态 → 渲染 connector 行 + 健康图标', () => {
    render(<SocConnectorStatusList items={[baseItem]} />);
    expect(screen.getByTestId('soc-status-list')).toBeInTheDocument();
    expect(screen.getByTestId('soc-status-row-elastic')).toBeInTheDocument();
    expect(screen.getByText('elastic')).toBeInTheDocument();
    expect(screen.getByText('Elastic SIEM')).toBeInTheDocument();
    expect(screen.getByText('健康')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
  });

  test('UNHEALTHY 状态 → 显示错误图标 + message 副文案', () => {
    render(
      <SocConnectorStatusList
        items={[{
          ...baseItem,
          status: 'UNHEALTHY',
          message: 'auth failed',
        }]}
      />,
    );
    expect(screen.getByText('不可用')).toBeInTheDocument();
    expect(screen.getByText(/auth failed/)).toBeInTheDocument();
  });

  test('DEGRADED 状态 → 显示降级 + 副文案', () => {
    render(
      <SocConnectorStatusList
        items={[{
          ...baseItem,
          status: 'DEGRADED',
          message: 'cluster yellow',
        }]}
      />,
    );
    expect(screen.getByText('降级')).toBeInTheDocument();
    expect(screen.getByText(/cluster yellow/)).toBeInTheDocument();
  });

  test('DISABLED 状态 → 显示 "已禁用" 且 availableRuleCount 显示 "—"', () => {
    render(
      <SocConnectorStatusList
        items={[{
          ...baseItem,
          status: 'DISABLED',
          availableRuleCount: undefined,
          lastCheckedAt: undefined,
        }]}
      />,
    );
    expect(screen.getByText('已禁用')).toBeInTheDocument();
    // 可用规则 + 上次检查列各一个 "—"
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2);
  });

  test('onRefreshAll → 顶部刷新按钮可点', () => {
    const onRefreshAll = vi.fn();
    render(<SocConnectorStatusList items={[baseItem]} onRefreshAll={onRefreshAll} />);
    fireEvent.click(screen.getByTestId('soc-status-refresh-all'));
    expect(onRefreshAll).toHaveBeenCalled();
  });

  test('onRefreshOne → 行级刷新按钮带 connectorId', () => {
    const onRefreshOne = vi.fn();
    render(<SocConnectorStatusList items={[baseItem]} onRefreshOne={onRefreshOne} />);
    fireEvent.click(screen.getByTestId('soc-status-refresh-elastic'));
    expect(onRefreshOne).toHaveBeenCalledWith('elastic');
  });

  test('未传 callback → 不渲染刷新按钮', () => {
    render(<SocConnectorStatusList items={[baseItem]} />);
    expect(screen.queryByTestId('soc-status-refresh-all')).not.toBeInTheDocument();
    expect(screen.queryByTestId('soc-status-refresh-elastic')).not.toBeInTheDocument();
  });
});
