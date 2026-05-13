// Boundary-coverage PR C3 截图脚本 —— IPv6 安全验证系统 §3.1 + §4.1.
//
// 用于招标响应截图佐证：在运行中的 dev 栈上执行可产出 5 张高保真界面截图：
//   1. 基线列表（CoverageBaselinesList）
//   2. 基线创建 Dialog（CoverageBaselineEditor）
//   3. 覆盖矩阵（CoverageMatrixView）
//   4. 单元格详情 Drawer（CellDetailDrawer）
//   5. 对比视图（CoverageDiffView）
//
// 与 combination/screenshots.spec.ts 同模式：依赖运行中的后端 + Postgres + 至少 1 个
// completed run（含 coverage_results 数据）。截图保存到 tests_e2e/artifacts/screenshots/coverage/
// 供文档拼接。CI 上跑 `yarn test:e2e` 不会执行 —— `test.describe.skip` 默认跳过，
// 操作员手动改 `.skip` 为空字符串后再运行。
//
// 启用流程：
//   1. cd veriguard-dev && docker compose up -d
//   2. 启动 veriguard-api（mvn spring-boot:run）
//   3. 启动 veriguard-front（yarn start）
//   4. 通过 POST /api/coverage/baselines 创建一个基线（assets ≥ 5、policies ≥ 5）
//   5. POST /api/coverage/baselines/{id}/run 触发评估并等待 completed
//   6. 把基线 id / run id 写到 COVERAGE_BASELINE_ID / COVERAGE_RUN_ID 环境变量
//   7. 注释掉下面 `test.describe.skip` 的 `.skip`
//   8. yarn playwright test tests_e2e/tests/admin/coverage/screenshots.spec.ts

import { expect, test } from '@playwright/test';

const BASELINE_ID = process.env.COVERAGE_BASELINE_ID ?? '';
const COMPARE_RUN_ID = process.env.COVERAGE_COMPARE_RUN_ID ?? '';

test.describe.skip('coverage screenshots (PR C3)', () => {
  test.beforeEach(async ({ page }) => {
    if (!BASELINE_ID) {
      throw new Error('COVERAGE_BASELINE_ID env var required');
    }
    await page.goto('/admin/login');
    await page.fill('input[name=login]', process.env.COVERAGE_USER ?? 'admin@veriguard.local');
    await page.fill('input[name=password]', process.env.COVERAGE_PASS ?? 'admin');
    await page.click('button[type=submit]');
    await page.waitForURL('**/admin/**');
  });

  test('1. baselines list', async ({ page }) => {
    await page.goto('/admin/coverage');
    await expect(page.getByText('边界覆盖度基线')).toBeVisible();
    await page.waitForTimeout(500);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/coverage/01_baselines_list.png',
      fullPage: true,
    });
  });

  test('2. baseline editor dialog', async ({ page }) => {
    await page.goto('/admin/coverage');
    await page.getByRole('button', { name: '新建基线' }).click();
    await expect(page.getByText('新建覆盖度基线')).toBeVisible();
    await page.waitForTimeout(500);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/coverage/02_baseline_editor.png',
      fullPage: true,
    });
  });

  test('3. matrix view', async ({ page }) => {
    await page.goto(`/admin/coverage/baselines/${BASELINE_ID}`);
    await expect(page.getByText('覆盖矩阵')).toBeVisible();
    await page.getByRole('tab', { name: '覆盖矩阵' }).click();
    await page.waitForTimeout(1000);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/coverage/03_matrix_view.png',
      fullPage: true,
    });
  });

  test('4. cell detail drawer', async ({ page }) => {
    await page.goto(`/admin/coverage/baselines/${BASELINE_ID}`);
    await page.getByRole('tab', { name: '覆盖矩阵' }).click();
    await page.waitForTimeout(1000);
    // click first hit/miss cell
    const firstCell = page.locator('table tbody tr td').nth(1);
    await firstCell.click();
    await page.waitForTimeout(500);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/coverage/04_cell_detail.png',
      fullPage: true,
    });
  });

  test('5. diff view', async ({ page }) => {
    if (!COMPARE_RUN_ID) {
      test.skip(true, 'COVERAGE_COMPARE_RUN_ID env var required for diff view');
      return;
    }
    await page.goto(`/admin/coverage/baselines/${BASELINE_ID}`);
    await page.getByRole('tab', { name: '对比' }).click();
    await page.fill('input[label="对比 run_id"], input', COMPARE_RUN_ID);
    await page.getByRole('button', { name: '对比' }).click();
    await page.waitForTimeout(1000);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/coverage/05_diff_view.png',
      fullPage: true,
    });
  });
});
