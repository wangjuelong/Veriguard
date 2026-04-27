// Sandbox M1 E2E — covers sandbox preset CRUD + script export from the
// /admin/veriguard sandbox tab. Requires a running dev stack (backend on
// dev port, frontend served by Vite, Postgres up with V4_73 migration
// applied) and an authenticated admin user via the project's existing
// auth.setup.ts. Until that's wired up, this spec is left in the suite
// for the operator to enable when the stack is available; it is not
// covered by `yarn test:e2e` runs in environments without the stack.

import { expect, test } from '@playwright/test';

test.describe('sandbox m1', () => {
  test('create / edit / export / delete preset', async ({ page }) => {
    await page.goto('/admin/veriguard');
    await page.getByRole('tab', { name: /沙箱管理/ }).click();

    // 新建预设（不填规则）
    await page.getByRole('button', { name: /新建预设/ }).click();
    await page.getByLabel('名称').fill('e2e-勒索沙箱');
    await page.getByRole('checkbox', { name: '勒索病毒样本执行' }).check();
    await page.getByRole('button', { name: '保存' }).click();
    await expect(page.getByText('e2e-勒索沙箱')).toBeVisible();

    // 行操作 → 编辑加规则
    await page.getByRole('button', { name: /沙箱「e2e-勒索沙箱」操作/ }).click();
    await page.getByRole('menuitem', { name: '编辑' }).click();
    await page.getByRole('button', { name: /添加规则/ }).click();
    await page.getByRole('button', { name: '保存' }).click();

    // 导出 iptables（拦截 download 事件）
    await page.getByRole('button', { name: /沙箱「e2e-勒索沙箱」操作/ }).click();
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('menuitem', { name: '导出 iptables 脚本' }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/iptables\.sh$/);

    // 删除（二次确认）
    await page.getByRole('button', { name: /沙箱「e2e-勒索沙箱」操作/ }).click();
    await page.getByRole('menuitem', { name: '删除' }).click();
    await expect(page.getByText('确定删除沙箱预设「e2e-勒索沙箱」')).toBeVisible();
    await page.getByRole('button', { name: '确认删除' }).click();
    await expect(page.getByText('e2e-勒索沙箱')).toHaveCount(0);
  });
});
