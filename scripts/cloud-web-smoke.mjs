// Read-only checks of the independent cloud frontend via an SSH loopback forward.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { chromium, expect } from '../web/node_modules/@playwright/test/index.mjs';
const origin = process.argv[2];
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(origin || '')) throw new Error('An SSH-forwarded loopback frontend is required.');
const output = new URL('../acceptance-results/cloud/', import.meta.url);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  for (const [name, viewport] of [['desktop', {width:1280,height:900}], ['mobile', {width:390,height:844}]]) {
    const context = await browser.newContext({viewport, locale:'zh-CN', timezoneId:'Asia/Shanghai'});
    const page = await context.newPage();
    const failures = [];
    page.on('pageerror', error => failures.push(error.message));
    await page.goto(origin, {waitUntil:'networkidle'});
    await expect(page.getByRole('heading', {level:1})).toContainText('把今天安排好');
    for (const label of ['周期计划','目标路线','今日待办']) {
      await page.getByRole('tab', {name:label,exact:true}).click();
      await expect(page.getByRole('tab', {name:label,exact:true})).toHaveAttribute('aria-selected','true');
    }
    await expect(page.getByRole('button', {name:'正式安装包尚未发布',exact:true})).toBeDisabled();
    await expect(page.getByRole('link', {name:'鄂ICP备2026050813号-1'})).toHaveAttribute('href','https://beian.miit.gov.cn/');
    const manifest = await (await context.request.get(`${origin}/downloads/manifest.json`)).json();
    assert.equal(manifest.windows,null); assert.equal(manifest.android,null);
    await page.screenshot({path:fileURLToPath(new URL(`${name}-home.png`,output)),fullPage:true});
    await page.getByRole('button', {name:'开始使用网页版',exact:true}).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('button',{name:'登录',exact:true})).toBeEnabled();
    await expect(page.getByText('当前未开放公开注册。')).toBeVisible();
    assert.equal(failures.length,0,failures.join('\n'));
    await context.close();
    console.log(`PASS ${name}: cloud homepage, three mode previews, filing link, gated downloads, login and closed registration.`);
  }
} finally { await browser.close(); }
