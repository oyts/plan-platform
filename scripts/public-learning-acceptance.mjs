// Real HTTPS teaching acceptance. Only the existing invited synthetic USER accounts.
import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { chromium, expect } from '../web/node_modules/@playwright/test/index.mjs';
import { exerciseLearning } from './learning-acceptance.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const origin = 'https://plan.zijun701.cn';
const version = JSON.parse(await readFile(join(root, 'web/package.json'), 'utf8')).version;
const accounts = JSON.parse(await readFile(join(root, '.desktop-tools/public-web/private/accounts.json'), 'utf8'));
const emails = ['web-acceptance-a-20261012@example.test', 'web-acceptance-b-20261012@example.test'];
assert.deepEqual(Object.keys(accounts).sort(), [...emails].sort());
const secrets = emails.map(email => accounts[email].password);
const report = { version, status: 'RUNNING', scope: 'Real HTTPS teaching with synthetic USER accounts; no business writes and no APK device claims', cases: [] };
const out = join(root, 'acceptance-results/public-learning', new Date().toISOString().replace(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8));
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ executablePath: join(root, '.desktop-tools/playwright/chromium-1243/chrome-win64/chrome.exe'), headless: true });
async function check(name, action) { await action(); report.cases.push({ name, status: 'PASS' }); console.log('PASS ' + name); }
try {
  for (const [index, profile] of ['desktop', 'mobile-web'].entries()) {
    const context = await browser.newContext({ viewport: profile === 'desktop' ? { width: 1280, height: 900 } : { width: 390, height: 844 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai', serviceWorkers: 'block' });
    try {
      const page = await context.newPage(); const email = emails[index]; const account = accounts[email];
      assert.equal(account.registered, true);
      const config = await (await context.request.get(origin + '/api/auth/config')).json(); assert.equal(config.serverVersion, version); assert.equal(config.registrationEnabled, false);
      await page.goto(origin + '/login'); await page.getByLabel('邮箱', { exact: true }).fill(email); await page.getByLabel('密码', { exact: true }).fill(account.password);
      const pending = page.waitForResponse(response => new URL(response.url()).pathname === '/api/auth/login' && response.request().method() === 'POST');
      await page.getByRole('button', { name: '登录', exact: true }).click();
      const response = await pending; assert.equal(response.status(), 200); const session = await response.json(); secrets.push(session.accessToken); assert.equal(session.user.role, 'USER');
      await expect(page.locator('.site-header')).toBeVisible();
      const headers = { Authorization: 'Bearer ' + session.accessToken };
      const todayResponse = await context.request.get(origin + '/api/dashboard/today', { headers }); assert.equal(todayResponse.status(), 200); const today = (await todayResponse.json()).date;
      const paths = ['/plans', '/mainlines', `/todos?from=${today}&to=${today}`];
      async function snapshots() { const values = []; for (const path of paths) { const result = await context.request.get(origin + '/api' + path, { headers }); assert.equal(result.status(), 200); values.push(await result.json()); } return values; }
      const before = await snapshots();
      const client = { kind: 'web', page };
      const navigate = async (_client, path) => { await page.goto(origin + path); await expect(page.locator('.site-header')).toBeVisible(); };
      await check(profile + ': four practical lessons and persisted progress', async () => {
        await exerciseLearning(client, expect, navigate, navigate);
        await page.screenshot({ path: join(out, profile + '-learning.png'), fullPage: true });
      });
      await check(profile + ': teaching leaves business data unchanged', async () => { assert.deepEqual(await snapshots(), before); });
      await check(profile + ': replay is available from the actual navigation', async () => {
        if (profile === 'mobile-web') { await page.getByRole('button', { name: '更多', exact: true }).click(); await page.getByRole('dialog').getByRole('link', { name: '使用教学', exact: true }).click(); }
        else await page.getByRole('navigation', { name: '主导航', exact: true }).getByRole('link', { name: '使用教学', exact: true }).click();
        await expect(page.getByRole('heading', { name: '使用教学', exact: true })).toBeVisible();
      });
    } finally { await context.request.post(origin + '/api/auth/logout').catch(() => undefined); await context.close(); }
  }
  report.status = 'PASS';
} catch (error) {
  report.status = 'FAIL'; report.failure = secrets.reduce((value, secret) => typeof secret === 'string' && secret ? value.replaceAll(secret, '[redacted]') : value, String(error.message));
  console.error('Public learning acceptance failed: ' + report.failure); process.exitCode = 1;
} finally { await browser.close(); await writeFile(join(out, 'report.json'), JSON.stringify(report, null, 2)); console.log('Report: ' + join(out, 'report.json')); }
