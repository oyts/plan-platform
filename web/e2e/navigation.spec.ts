import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const API = 'http://127.0.0.1:43131/api';
const password = 'NavigationAcceptance123!';

async function setup(page: Page, request: APIRequestContext) {
  const email = `navigation-${randomUUID()}@example.test`;
  const response = await request.post(`${API}/auth/register`, { data: { email, password, displayName: '导航验收' } });
  expect(response.status()).toBe(201);
  const token = (await response.json()).accessToken;
  const headers = { Authorization: `Bearer ${token}` };
  const today = (await (await request.get(`${API}/dashboard/today`, { headers })).json()).date;
  for (let i = 0; i < 9; i++) {
    const created = await request.post(`${API}/plans`, { headers, data: { name: `导航测试计划 ${i + 1}`, description: '独立内存数据库中的合成数据', startDate: today, endDate: today, restDays: [7], overallTagName: '总计', overallTagColor: '#002FA7' } });
    expect(created.status()).toBe(201);
  }
  await page.goto('/login');
  await page.getByLabel('邮箱', { exact: true }).fill(email);
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.site-header')).toBeVisible();
  await page.goto('/plans');
  await expect(page.getByRole('heading', { name: '我的计划', exact: true })).toBeVisible();
  await expect(page.getByText('导航测试计划 9', { exact: true })).toBeVisible();
}

async function enterLearning(page: Page, isMobile: boolean) {
  if (isMobile) {
    await page.getByRole('button', { name: '更多', exact: true }).click();
    await page.getByRole('dialog').getByRole('link', { name: '使用教学', exact: true }).click();
    await expect(page.getByRole('dialog')).not.toBeVisible();
  } else {
    await page.getByRole('navigation', { name: '主导航', exact: true }).getByRole('button', { name: '更多', exact: true }).click();
    await page.getByRole('dialog').getByRole('link', { name: '使用教学', exact: true }).click();
  }
  await expect(page).toHaveURL(/\/learn$/);
  await expect(page.locator('.lesson-card')).toHaveCount(5);
}

test('new page opens at the top with content focus from a scrolled workspace', async ({ page, request, isMobile }, info) => {
  await setup(page, request);
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(200);
  await page.screenshot({ path: info.outputPath('before-navigation.png') });
  await enterLearning(page, isMobile);
  await page.screenshot({ path: info.outputPath('after-navigation.png') });
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
  await expect(page.locator('main')).toBeFocused();
  await expect(page.getByRole('heading', { name: '使用教学', exact: true })).toBeInViewport();
  await page.getByRole('navigation', { name: '教学课程', exact: true }).getByRole('link', { name: '周期计划', exact: true }).click();
  await expect(page.getByLabel('练习计划名称')).toBeVisible();
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
  await expect(page.locator('main')).toBeFocused();
});

test('history back restores the prior long list position after data loads', async ({ page, request, isMobile }) => {
  await setup(page, request);
  await page.evaluate(() => window.scrollTo(0, 700));
  const before = await page.evaluate(() => scrollY);
  expect(before).toBeGreaterThan(200);
  await enterLearning(page, isMobile);
  // Force a real empty-list render before its API response on history back.
  await page.route('**/api/plans', async route => { await new Promise(resolve => setTimeout(resolve, 450)); await route.continue(); });
  await page.goBack();
  await expect(page).toHaveURL(/\/plans$/);
  await expect(page.getByText('导航测试计划 9', { exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => scrollY)).toBeCloseTo(before, 0);
});

test('a newer navigation cancels pending history restoration', async ({ page, request, isMobile }) => {
  await setup(page, request);
  await page.evaluate(() => window.scrollTo(0, 700));
  await enterLearning(page, isMobile);
  await page.route('**/api/plans', async route => { await new Promise(resolve => setTimeout(resolve, 900)); await route.continue(); });
  await page.goBack();
  await expect(page).toHaveURL(/\/plans$/);
  await enterLearning(page, isMobile);
  await page.waitForTimeout(2200); // Past the bounded restoration window.
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
  await expect(page.locator('main')).toBeFocused();
});

test('public product anchor links still reach the download section', async ({ page }) => {
  await page.goto('/welcome');
  await page.getByRole('navigation', { name: '官网导航', exact: true }).getByRole('link', { name: '客户端下载', exact: true }).click();
  await expect(page).toHaveURL(/#clients$/);
  await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(100);
  await expect(page.locator('#clients')).toBeInViewport();
});
