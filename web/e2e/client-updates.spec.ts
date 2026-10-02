import { expect, test } from '@playwright/test';

// UI/bridge contract tests, not a substitute for native signature or installation tests.
async function nativeFixture(page: import('@playwright/test').Page, mandatory = false) {
  await page.addInitScript(({ required }) => {
    const runtime = window as unknown as { isTauri: boolean; __TAURI_INTERNALS__: unknown; testUpdateCalls: string[] };
    runtime.isTauri = true; runtime.testUpdateCalls = [];
    runtime.__TAURI_INTERNALS__ = { invoke: async (command: string, args: { request?: { path?: string } }) => {
      runtime.testUpdateCalls.push(command);
      if (command === 'desktop_info') return { version: '0.1.1', serverUrl: 'http://127.0.0.1:43131', updatesEnabled: true, updateChannel: 'https://plan.example.cn/client-updates/windows/latest.json' };
      if (command === 'desktop_check_update') return { version: '0.1.2', notes: '测试发布：验证更新提示和确认操作。', mandatory: required };
      if (command === 'desktop_request') return args.request?.path === '/api/auth/config'
        ? { status: 200, headers: {}, body: JSON.stringify({ registrationEnabled: true, planRestDaysEnabled: true }) }
        : { status: 401, headers: {}, body: '{}' };
      return true;
    } };
  }, { required: mandatory });
  await page.goto('/#/desktop');
  await expect(page.getByRole('heading', { name: '客户端更新', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: /可更新至|必须更新至/ })).toBeVisible();
}
test('optional update is not preselected and may be postponed', async ({ page }) => {
  await nativeFixture(page);
  await expect(page.getByRole('checkbox', { name: '自动下载更新（安装前仍需确认）' })).not.toBeChecked();
  const calls = await page.evaluate(() => (window as unknown as { testUpdateCalls: string[] }).testUpdateCalls);
  expect(calls).not.toContain('desktop_download_update'); expect(calls).not.toContain('desktop_install_update');
  await page.getByRole('button', { name: '稍后处理' }).click();
  await expect(page.getByRole('status', { name: '客户端版本提示' })).toHaveCount(0);
  await page.screenshot({ path: test.info().outputPath('client-update-panel.png'), fullPage: true });
});
test('automatic download persists explicit consent without automatic installation', async ({ page }) => {
  await nativeFixture(page);
  await page.getByRole('checkbox', { name: '自动下载更新（安装前仍需确认）' }).check();
  await expect(page.getByRole('button', { name: '安装并重新打开' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('checkbox', { name: '自动下载更新（安装前仍需确认）' })).toBeChecked();
  await expect(page.getByRole('button', { name: '安装并重新打开' })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { testUpdateCalls: string[] }).testUpdateCalls)).not.toContain('desktop_install_update');
});
test('download then cancellation does not invoke installation', async ({ page }) => {
  await nativeFixture(page);
  await page.getByRole('button', { name: '下载更新', exact: true }).click();
  page.once('dialog', dialog => dialog.dismiss());
  await page.getByRole('button', { name: '安装并重新打开' }).click();
  expect(await page.evaluate(() => (window as unknown as { testUpdateCalls: string[] }).testUpdateCalls)).not.toContain('desktop_install_update');
});
test('required update cannot be dismissed or navigate to business editing', async ({ page }) => {
  await nativeFixture(page, true);
  await expect(page.getByRole('button', { name: '稍后处理' })).toHaveCount(0);
  await page.evaluate(() => { window.location.hash = '/plans'; });
  await expect(page).toHaveURL(/#\/desktop$/);
  await expect(page.getByRole('heading', { name: '必须更新至 0.1.2' })).toBeVisible();
});
