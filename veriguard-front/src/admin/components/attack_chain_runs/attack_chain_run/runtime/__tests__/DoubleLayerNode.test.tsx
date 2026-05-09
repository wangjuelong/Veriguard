import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import { type DoubleLayerNodeData } from '../attackChainRuntimeTypes';
import DoubleLayerNode from '../DoubleLayerNode';

afterEach(cleanup);

const baseData: DoubleLayerNodeData = {
  title: 'PsExec 横向移动',
  subtitle: 'execution',
  preventionStatus: 'SUCCESS',
  detectionStatus: 'SUCCESS',
};

describe('DoubleLayerNode', () => {
  test('渲染节点名 + 副标题 + DETECTION 文案', () => {
    render(<DoubleLayerNode data={baseData} />);
    expect(screen.getByText('PsExec 横向移动')).toBeInTheDocument();
    expect(screen.getByText('execution')).toBeInTheDocument();
    expect(screen.getByText(/已检出/)).toBeInTheDocument();
  });

  test('repeat_count > 1 → 显示 ↻ N/M 角标', () => {
    render(
      <DoubleLayerNode
        data={{
          ...baseData,
          repeatCount: 3,
          currentIteration: 2,
        }}
      />,
    );
    expect(screen.getByTestId('repeat-badge')).toBeInTheDocument();
    expect(screen.getByTestId('repeat-badge').textContent).toContain('2');
    expect(screen.getByTestId('repeat-badge').textContent).toContain('3');
  });

  test('repeat_count = 1 → 不显示角标', () => {
    render(
      <DoubleLayerNode data={{
        ...baseData,
        repeatCount: 1,
      }}
      />,
    );
    expect(screen.queryByTestId('repeat-badge')).not.toBeInTheDocument();
  });

  test('SKIPPED 节点 → DETECTION 显示 "—"', () => {
    render(
      <DoubleLayerNode
        data={{
          ...baseData,
          preventionStatus: 'SKIPPED',
          detectionStatus: 'SKIPPED',
        }}
      />,
    );
    expect(screen.getByText(/DETECTION:/)).toBeInTheDocument();
    expect(screen.getByText(/—/)).toBeInTheDocument();
  });

  test('点击节点 → 触发 onClick', () => {
    const onClick = vi.fn();
    render(<DoubleLayerNode data={baseData} onClick={onClick} />);
    fireEvent.click(screen.getByTestId('double-layer-node'));
    expect(onClick).toHaveBeenCalled();
  });

  test('PARTIAL 状态 → 顶部 + 底部 都显示部分文案', () => {
    render(
      <DoubleLayerNode
        data={{
          ...baseData,
          preventionStatus: 'PARTIAL',
          detectionStatus: 'PARTIAL',
        }}
      />,
    );
    expect(screen.getByText(/部分检出/)).toBeInTheDocument();
  });
});
