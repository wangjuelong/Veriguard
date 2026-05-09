import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

import NodeBadge from '../NodeBadge';

afterEach(cleanup);

describe('NodeBadge', () => {
  test('OK → 渲染灰 ⚙', () => {
    render(<NodeBadge state={{ kind: 'OK' }} />);
    expect(screen.getByTestId('node-badge-ok')).toBeInTheDocument();
  });

  test('OVERRIDE → 渲染蓝 ⚙', () => {
    render(<NodeBadge state={{ kind: 'OVERRIDE' }} />);
    expect(screen.getByTestId('node-badge-override')).toBeInTheDocument();
  });

  test('REPEAT → 渲染带计数 badge', () => {
    render(
      <NodeBadge state={{
        kind: 'REPEAT',
        count: 3,
      }}
      />,
    );
    expect(screen.getByTestId('node-badge-repeat')).toBeInTheDocument();
  });

  test('INCOMPLETE → 渲染红 chip 含 reason', () => {
    render(
      <NodeBadge state={{
        kind: 'INCOMPLETE',
        reason: '缺少目标资产',
      }}
      />,
    );
    expect(screen.getByTestId('node-badge-incomplete')).toBeInTheDocument();
  });

  test('点击 → 调用 onClick', () => {
    const onClick = vi.fn();
    render(<NodeBadge state={{ kind: 'OK' }} onClick={onClick} />);
    fireEvent.click(screen.getByTestId('node-badge-ok'));
    expect(onClick).toHaveBeenCalled();
  });
});
