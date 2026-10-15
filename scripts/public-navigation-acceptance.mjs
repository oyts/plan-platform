// Affected-area HTTPS acceptance only: route scrolling, teaching links and anchors.
import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { chromium, expect } from '../web/node_modules/@playwright/test/index.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const origin = 'https://plan.zijun701.cn';
const version = JSON.parse(await readFile(join(root, 'web/package.json'), 'utf8')).version;
const accounts = JSON.parse(await readFile(join(root, '.desktop-tools/public-web/private/accounts.json'), 'utf8'));
const emails = ['web-acceptance-a-20261012@example.test', 'web-acceptance-b-20261012@example.test'];
assert.deepEqual(Object.keys(accounts).sort(), [...emails].sort());
const secrets = emails.map(email => accounts[email].password);
const report = { version, status: 'RUNNING', scope: 'HTTPS affected navigation only; existing synthetic USER accounts; no business writes or native claims', cases: [] };
const out = join(root, 'acceptance-results/public-navigation', new Date().toISOString().replace(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8));
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ executablePath: join(root, '.desktop-tools/playwright/chromium-1243/chrome-win64/chrome.exe'), headless: true });
async function check(name, action) { await action(); report.cases.push({ name, status: 'PASS' }); console.log('PASS ' + name); }
try {
  for (const [index, profile] of ['desktop', 'mobile-web'].entries()) {
    const context = await browser.newContext({ viewport: profile === 'desktop' ? { width: 1280, height: 900 } : { width: 390, height: 844 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai', serviceWorkers: 'block' });
    try {
      const page = await context.newPage(); const email = emails[index]; const account = accounts[email];
      assert.equal(account.registered, true);
      const config = await (await context.request.get(origin + '/api/auth/config')).json();
      assert.equal(config.serverVersion, version); assert.equal(config.registrationEnabled, false);
      await page.goto(origin + '/login');
      await page.getByLabel('邮箱', { exact: true }).fill(email);
      await page.getByLabel('密码', { exact: true }).fill(account.password);
      const pending = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
      await page.getByRole('button', { name: '登录', exact: true }).click();
      const response = await pending; assert.equal(response.status(), 200);
      const session = await response.json(); secrets.push(session.accessToken); assert.equal(session.user.role, 'USER');
      await expect(page.locator('.site-header')).toBeVisible();
      await check(profile + ': scrolled settings to teaching starts at top', async () => {
        await page.goto(origin + '/settings');
        await expect(page.getByRole('heading', { name: '账号安全与数据迁移', exact: true })).toBeVisible();
        await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
        await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(100);
        if (profile === 'mobile-web') {
          await page.getByRole('button', { name: '更多', exact: true }).click();
          await page.getByRole('dialog').getByRole('link', { name: '使用教学', exact: true }).click();
        } else {
          await page.getByRole('navigation', { name: '主导航', exact: true }).getByRole('link', { name: '使用教学', exact: true }).click();
        }
        await expect(page.locator('.lesson-card')).toHaveCount(4);
        await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
        await expect(page.locator('main')).toBeFocused();
        await page.screenshot({ path: join(out, profile + '-teaching-top.png') });
      });
      await check(profile + ': course navigation starts at top', async () => {
        for (const title of ['周期计划', '目标路线', '今日待办', '离线与同步']) {
          await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
          await page.getByRole('navigation', { name: '教学课程', exact: true }).getByRole('link', { name: title, exact: true }).click();
          await expect(page.getByText('独立练习区', { exact: true })).toBeVisible();
          await expect.poll(() => page.evaluate(() => scrollY)).toBe(0);
          await expect(page.locator('main')).toBeFocused();
        }
      });
      await check(profile + ': product download anchor still works', async () => {
        await page.goto(origin + '/welcome');
        await page.getByRole('navigation', { name: '官网导航', exact: true }).getByRole('link', { name: '客户端下载', exact: true }).click();
        await expect(page.locator('#clients')).toBeInViewport();
        await expect.poll(() => page.evaluate(() => scrollY)).toBeGreaterThan(100);
      });
    } finally { await context.request.post(origin + '/api/auth/logout').catch(() => undefined); await context.close(); }
  }
  report.status = 'PASS';
} catch (error) {
  report.status = 'FAIL'; report.failure = secrets.reduce((value, secret) => typeof secret === 'string' && secret ? value.replaceAll(secret, '[redacted]') : value, String(error.message));
  console.error('Public navigation acceptance failed: ' + report.failure); process.exitCode = 1;
} finally { await browser.close(); await writeFile(join(out, 'report.json'), JSON.stringify(report, null, 2)); console.log('Report: ' + join(out, 'report.json')); }
