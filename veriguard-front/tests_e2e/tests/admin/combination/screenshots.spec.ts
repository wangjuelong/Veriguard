// Attack-combination ★2 截图脚本 —— IPv6 安全验证系统 §3.6.
//
// 用于招标响应截图佐证：在运行中的 dev 栈上执行可产出 5 张高保真界面截图：
//   1. 任务列表（CombinationsList）
//   2. 任务创建 Dialog（CombinationTaskEditor）
//   3. 运行画布（CombinationRunCanvas）
//   4. 聚类树 + 严重度徽标（ClusterTreeView）
//   5. Payload 下钻 Dialog（PayloadDrilldown）
//   6. 分级配置编辑器（SeverityConfigEditor）
//
// 与 sandbox/m1.spec.ts 同模式：依赖运行中的后端 + Postgres + 至少 1 个 completed
// run（含聚类与分级数据）。截图保存到 tests_e2e/artifacts/screenshots/combination/
// 供文档拼接。CI 上跑 `yarn test:e2e` 不会执行 —— `test.describe.skip` 默认跳过，
// 操作员手动改 `.skip` 为空字符串后再运行。
//
// 启用流程：
//   1. cd veriguard-dev && docker compose up -d
//   2. 启动 veriguard-api（mvn spring-boot:run）
//   3. 启动 veriguard-front（yarn start）
//   4. 通过 POST /api/attack_combination/runs 跑一个小任务（base × dim ≤ 20）
//      并等待 status=completed
//   5. 把任务 id 写到 COMBINATION_RUN_ID 环境变量
//   6. 注释掉下面 `test.describe.skip` 的 `.skip`
//   7. yarn playwright test tests_e2e/tests/admin/combination/screenshots.spec.ts

import { expect, test } from '@playwright/test';

const RUN_ID = process.env.COMBINATION_RUN_ID ?? '';

test.describe.skip('combination screenshots (★2)', () => {
  test('capture five screenshots', async ({ page }) => {
    expect(RUN_ID).not.toEqual('');

    // 1. 任务列表
    await page.goto('/admin/combinations');
    await expect(page.getByText('攻击组合任务')).toBeVisible();
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/combination/01-list.png',
      fullPage: true,
    });

    // 2. 任务创建 Dialog
    await page.getByRole('button', { name: '新建任务' }).click();
    await expect(page.getByText('新建攻击组合任务')).toBeVisible();
    // 等维度加载完
    await page.waitForTimeout(800);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/combination/02-task-editor.png',
      fullPage: true,
    });
    await page.getByRole('button', { name: '取消' }).click();

    // 3. 运行画布
    await page.goto(`/admin/combinations/${RUN_ID}`);
    await expect(page.getByText(/进度：/)).toBeVisible();
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/combination/03-run-canvas.png',
      fullPage: true,
    });

    // 4. 聚类树（资产视角）
    await page.getByRole('tab', { name: '聚类（资产视角）' }).click();
    await page.waitForTimeout(500);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/combination/04-cluster-tree.png',
      fullPage: true,
    });

    // 5. Payload 下钻
    const drillButton = page.getByRole('button', { name: '下钻' }).first();
    await drillButton.click();
    await expect(page.getByText(/Payload 下钻/)).toBeVisible();
    await page.waitForTimeout(500);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/combination/05-payload-drilldown.png',
      fullPage: true,
    });
    await page.getByRole('button', { name: '关闭' }).click();

    // 6. 分级配置
    await page.getByRole('tab', { name: '分级配置' }).click();
    await page.waitForTimeout(300);
    await page.screenshot({
      path: 'tests_e2e/artifacts/screenshots/combination/06-severity-config.png',
      fullPage: true,
    });
  });
});
