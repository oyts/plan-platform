import { test, expect, type Page } from '@playwright/test';
async function fixture(page: Page) {
  // Synthetic layout/API fixtures, never production members or secrets.
  const user = { id: 'ai-admin-fixture', email: 'ai-admin@example.test', displayName: '额度测试管理员', role: 'ADMIN', emailVerified: true };
  let policy = { dailyLimit: 0, version: 0 };
  let personal: number | null = null, paused = false, version = 0;
  const audits: object[] = [];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname, method = route.request().method();
    if (!path.startsWith('/api/')) return route.continue();
    if (path === '/api/auth/config') return route.fulfill({ json: { apiVersion: 1, serverVersion: '0.1.9', registrationEnabled: false, invitationEnabled: true, planRestDaysEnabled: true, todoEnabled: true } });
    if (path === '/api/auth/refresh') return route.fulfill({ json: { accessToken: 'ai-layout-fixture', expiresInSeconds: 3600, user } });
    if (path === '/api/admin/invitations') return route.fulfill({ json: [] });
    if (path === '/api/admin/ai/settings') {
      if (method === 'PUT') { const request = route.request().postDataJSON(); if (request.version !== policy.version) return route.fulfill({ status: 409, json: { message: '默认额度已被修改，请刷新后重试。' } }); policy = { dailyLimit: request.dailyLimit, version: policy.version + 1 }; }
      return route.fulfill({ json: policy });
    }
    if (path === '/api/admin/ai/users') {
      const dailyLimit = personal ?? policy.dailyLimit;
      const items = url.searchParams.get('query') === 'missing' ? [] : [{ id: 'member-fixture', displayName: '额度测试成员', email: 'very-long-ai-member-layout-address@example.test', role: 'USER', quota: { date: '2026-10-15', resetsAt: '2026-10-15T16:00:00Z', dailyLimit, overrideLimit: personal, paused, used: 1, pending: 1, remaining: paused ? 0 : Math.max(0, dailyLimit - 2), version } }];
      return route.fulfill({ json: { items, total: items.length, page: 0, size: 10 } });
    }
    if (path === '/api/admin/ai/users/member-fixture') {
      const request = route.request().postDataJSON();
      if (request.version !== version) return route.fulfill({ status: 409, json: { message: '成员额度已被修改，请刷新后重试。' } });
      audits.unshift({ actor: user.displayName, target: 'very-long-ai-member-layout-address@example.test', changeType: 'USER', beforeLimit: personal, afterLimit: request.dailyLimit, beforePaused: paused, afterPaused: request.paused, createdAt: '2026-10-15T10:00:00Z' });
      personal = request.dailyLimit; paused = request.paused; version++; return route.fulfill({ json: {} });
    }
    if (path === '/api/admin/ai/audit') return route.fulfill({ json: { items: audits, page: 0, size: 5, total: audits.length } });
    return route.fulfill({ json: [] });
  });
  await page.goto('/members');
  await expect(page.getByRole('heading', { name: 'AI 对话额度', exact: true })).toBeVisible();
  await expect(page.getByLabel('默认每日对话次数')).toHaveValue('0');
}
test('quota settings, personal inheritance, pause and mobile layout', async ({ page, isMobile }, info) => {
  await fixture(page);
  const panel = page.locator('.ai-quota');
  await expect(panel).toContainText('不会产生模型调用费用');
  await page.getByLabel('默认每日对话次数').fill('5'); await page.getByRole('button', { name: '保存默认额度' }).click();
  await expect(panel.getByRole('status')).toContainText('当天用量未重置');
  const list = panel.locator(isMobile ? '.quota-cards' : '.quota-table');
  await expect(list).toContainText('使用默认额度');
  if (isMobile) {
    for (const width of [320, 390, 430, 844]) {
      await page.setViewportSize({ width, height: width === 844 ? 390 : 844 });
      await expect(panel.locator('.quota-cards')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
      await page.evaluate(() => window.scrollTo(0, 0)); await page.screenshot({ path: info.outputPath(`ai-quota-${width}.png`), fullPage: width !== 844 });
    }
    await page.setViewportSize({ width: 390, height: 844 });
  } else { await page.evaluate(() => window.scrollTo(0, 0)); await page.screenshot({ path: info.outputPath('ai-quota-desktop.png'), fullPage: true }); }
  await list.getByRole('button', { name: '修改 额度测试成员 的额度' }).click();
  const dialog = page.getByRole('dialog', { name: '修改成员额度' });
  await dialog.getByLabel('使用默认额度', { exact: true }).uncheck();
  await dialog.getByLabel('个人每日对话次数').fill('3');
  await dialog.getByLabel('暂停该成员 AI 新请求').check();
  await page.screenshot({ path: info.outputPath('ai-quota-editor.png') });
  await dialog.getByRole('button', { name: '保存成员额度' }).click();
  await expect(dialog).not.toBeVisible(); await expect(list).toContainText('3 次/天'); await expect(list).toContainText('AI 已暂停');
  await expect(panel.locator('.quota-audit')).toContainText('暂停 AI');
  await list.getByRole('button', { name: '修改 额度测试成员 的额度' }).click();
  await dialog.getByLabel('使用默认额度', { exact: true }).check(); await dialog.getByLabel('暂停该成员 AI 新请求').uncheck();
  await dialog.getByRole('button', { name: '保存成员额度' }).click();
  await expect(list).toContainText('使用默认额度'); await expect(list).not.toContainText('AI 已暂停');
  await page.getByLabel('搜索成员').fill('missing'); await panel.getByRole('button', { name: '搜索', exact: true }).click(); await expect(panel).toContainText('没有找到成员');
});
test('quota conflicts stay explicit and never silently overwrite another administrator', async ({ page }) => {
  await fixture(page);
  await page.route('**/api/admin/ai/settings', async route => route.request().method() === 'PUT' ? route.fulfill({ status: 409, json: { message: '默认额度已被修改，请刷新后重试。' } }) : route.fulfill({ json: { dailyLimit: 8, version: 2 } }));
  await page.getByLabel('默认每日对话次数').fill('4'); await page.getByRole('button', { name: '保存默认额度' }).click();
  await expect(page.locator('.ai-quota').getByRole('alert')).toContainText('已被修改');
  await page.getByRole('button', { name: '刷新额度' }).click(); await expect(page.getByLabel('默认每日对话次数')).toHaveValue('8');
});
