import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';

const API = 'http://127.0.0.1:43131/api';
test.beforeEach(({ isMobile }) => {
  test.skip(!isMobile, 'This suite checks touch/mobile layouts, including landscape.');
});
async function setup(page: Page, request: APIRequestContext, count = 1) {
  const email = `layout-${randomUUID()}@example.test`; const password = 'MobileLayout123!';
  const registered = await request.post(`${API}/auth/register`, { data: { email, password, displayName: '布局验收账号' } });
  expect(registered.status()).toBe(201);
  const { accessToken } = await registered.json(); const headers = { Authorization: `Bearer ${accessToken}` };
  const today = (await (await request.get(`${API}/dashboard/today`, { headers })).json()).date;
  const response = await request.post(`${API}/plans`, { headers, data: { name: '手机布局验收计划', description: '合成测试数据', startDate: today, endDate: today, restDays: [], overallTagName: '总计', overallTagColor: '#002FA7' } });
  expect(response.status()).toBe(201); const plan = await response.json();
  for (let i = 0; i < count; i++) {
    const row = await request.post(`${API}/plans/${plan.id}/tasks`, { headers, data: { date: today, title: `任务 ${i + 1}：学习并整理今天的执行记录`, description: '合成任务，用于检查窄屏换行和触控布局。', category: 'CUSTOM', plannedMinutes: 30, startTime: '09:00', endTime: '09:30', acceptance: ['整理记录'], required: true, sortOrder: i } });
    expect(row.status()).toBe(201);
  }
  await page.goto('/login'); await page.getByLabel('邮箱', { exact: true }).fill(email); await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('heading', { name: '今日控制台' })).toBeVisible();
  return plan;
}
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(1);
}
for (const width of [320, 390, 430, 844]) {
  test(`mobile hierarchy and touch navigation at ${width}px`, async ({ page, request }, testInfo) => {
    const height = width === 844 ? 390 : 844;
    await page.setViewportSize({ width, height }); const plan = await setup(page, request);
    const nav = page.getByRole('navigation', { name: '手机主导航' }); await expect(nav).toBeVisible();
    const bounds = await nav.boundingBox(); expect(bounds!.height).toBeGreaterThanOrEqual(60); expect(bounds!.y + bounds!.height).toBe(height);
    await noOverflow(page);
    // Full-page capture temporarily expands the viewport height in mobile Chromium;
    // use the visible viewport for the short landscape layout's height media query.
    await page.screenshot({ path: testInfo.outputPath('mobile-today.png'), fullPage: width !== 844 });
    await nav.getByRole('link', { name: '日程', exact: true }).click();
    await page.getByRole('button', { name: /任务 1：/ }).click();
    const dialog = page.getByRole('dialog'); await expect(dialog).toBeVisible(); await expect(dialog.getByLabel('执行状态')).toBeVisible();
    await dialog.getByRole('button', { name: '关闭面板' }).click(); await expect(dialog).not.toBeVisible(); await noOverflow(page);
    await nav.getByRole('button', { name: '更多', exact: true }).click(); await expect(page.getByRole('dialog', { name: '更多功能' })).toBeVisible();
    await page.getByRole('dialog').getByRole('link', { name: '同步', exact: false }).click();
    await expect(page.getByRole('heading', { name: '同步中心' })).toBeVisible(); await expect(page.getByRole('dialog')).not.toBeVisible(); await noOverflow(page);
    for (const path of ['/stats', '/settings', '/desktop', `/plans/${plan.id}`]) { await page.goto(path); await expect(page.locator('.site-header .brand')).toBeVisible(); await noOverflow(page); }
  });
}
test('task paging, search and focused editor preserve values on cancel', async ({ page, request }, testInfo) => {
  const plan = await setup(page, request, 13); await page.goto(`/plans/${plan.id}`);
  await expect(page.locator('.task-row')).toHaveCount(10); await page.getByRole('button', { name: '下一页', exact: true }).click(); await expect(page.locator('.task-row')).toHaveCount(3);
  await page.getByLabel('搜索任务').fill('任务 13：'); await expect(page.locator('.task-row')).toHaveCount(1);
  const row = page.locator('.task-row'); const title = await row.getByRole('heading').innerText();
  await row.getByRole('button', { name: '编辑', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '编辑任务', exact: true }); await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('任务标题', { exact: true })).toHaveValue(title);
  await dialog.getByLabel('任务标题', { exact: true }).fill('取消的修改');
  await page.screenshot({ path: testInfo.outputPath('mobile-task-editor.png') });
  await dialog.getByRole('button', { name: '取消编辑' }).click(); await expect(dialog).not.toBeVisible(); await expect(row.getByRole('heading')).toHaveText(title);
  await row.getByRole('button', { name: '编辑', exact: true }).click(); await page.keyboard.press('Escape'); await expect(dialog).not.toBeVisible();
  expect(await page.evaluate(() => document.body.style.overflow)).not.toBe('hidden'); await noOverflow(page);
});
test('more menu contains all secondary destinations and restores focus', async ({ page, request }) => {
  await setup(page, request); const more = page.getByRole('button', { name: '更多', exact: true }); await more.click();
  const dialog = page.getByRole('dialog', { name: '更多功能' });
  for (const label of ['统计', '复盘', '记录', '数据中心', '反馈与诊断', '同步', '设置']) await expect(dialog.getByRole('link', { name: new RegExp(`^${label}`) })).toBeVisible();
  await page.keyboard.press('Escape'); await expect(dialog).not.toBeVisible(); await expect(more).toBeFocused();
});
