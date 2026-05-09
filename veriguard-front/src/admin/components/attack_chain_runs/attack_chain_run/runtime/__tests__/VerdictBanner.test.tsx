import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import { type VerdictBannerData } from '../attackChainRuntimeTypes';
import VerdictBanner from '../VerdictBanner';

afterEach(cleanup);

describe('VerdictBanner', () => {
  test('终态 FULL_BLOCKED + FULL_BLOCKED → 显示双行 verdict + stats', () => {
    const data: VerdictBannerData = {
      prevention: 'FULL_BLOCKED',
      detection: 'FULL_BLOCKED',
      preventionStats: {
        blocked: 5,
        total: 5,
      },
      detectionStats: {
        nodesDetected: 5,
        nodeTotal: 5,
        linksMatched: 2,
        linkTotal: 2,
      },
    };
    render(<VerdictBanner data={data} />);
    expect(screen.getByTestId('verdict-banner')).toBeInTheDocument();
    expect(screen.getByText(/PREVENTION:.*完全防守/)).toBeInTheDocument();
    expect(screen.getByText(/DETECTION:.*完全防守/)).toBeInTheDocument();
    expect(screen.getByText(/5 \/ 5 个节点被拦下/)).toBeInTheDocument();
    expect(screen.getByText(/2 \/ 2 链路 SOC 匹配/)).toBeInTheDocument();
  });

  test('PARTIAL + stop-on-block → 显示 "截停于 N3" 副文案', () => {
    const data: VerdictBannerData = {
      prevention: 'PARTIAL',
      detection: 'PARTIAL',
      preventionStats: {
        blocked: 3,
        total: 5,
      },
      stoppedAtNodeName: 'N3',
    };
    render(<VerdictBanner data={data} />);
    expect(screen.getByText(/3 \/ 5 个节点被拦下/)).toBeInTheDocument();
    expect(screen.getByText(/截停于 N3/)).toBeInTheDocument();
  });

  test('折叠按钮 → 隐藏 DETECTION 行', async () => {
    const data: VerdictBannerData = {
      prevention: 'FULL_BREACH',
      detection: 'FULL_BREACH',
      preventionStats: {
        blocked: 0,
        total: 5,
      },
    };
    render(<VerdictBanner data={data} />);
    expect(screen.getByTestId('verdict-banner-detection-row')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('折叠'));
    // Collapse 是异步动画 + unmountOnExit，等到 row 真的从 DOM 消失
    await waitFor(() =>
      expect(screen.queryByTestId('verdict-banner-detection-row')).not.toBeInTheDocument(),
    );
  });

  test('点击 DETECTION 行 → 触发 onClickDetection', () => {
    const onClickDetection = vi.fn();
    const data: VerdictBannerData = {
      prevention: 'FULL_BLOCKED',
      detection: 'FULL_BLOCKED',
    };
    render(<VerdictBanner data={data} onClickDetection={onClickDetection} />);
    fireEvent.click(screen.getByTestId('verdict-banner-detection-row'));
    expect(onClickDetection).toHaveBeenCalled();
  });

  test('RUNNING 模式 → 单行精简显示', () => {
    const data: VerdictBannerData = {
      prevention: 'PENDING',
      detection: 'PENDING',
      running: true,
      runningStats: {
        settled: 4,
        total: 8,
        blocked: 1,
        detected: 2,
      },
    };
    render(<VerdictBanner data={data} />);
    expect(screen.getByTestId('verdict-banner-running')).toBeInTheDocument();
    expect(screen.getByText(/4\/8 已结算/)).toBeInTheDocument();
    expect(screen.getByText(/1 被拦下/)).toBeInTheDocument();
    expect(screen.getByText(/2 被检测/)).toBeInTheDocument();
  });

  test('N_A verdict → 显示"不适用"文案', () => {
    const data: VerdictBannerData = {
      prevention: 'N_A',
      detection: 'N_A',
    };
    render(<VerdictBanner data={data} />);
    expect(screen.getAllByText(/不适用/).length).toBeGreaterThanOrEqual(2);
  });
});
