import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';

// Local acceptance service only; never creates accounts on the production site.
const API = 'http://127.0.0.1:43131/api';
const password = 'LearningDatesAcceptance123!';
async function openLesson(page: Page, request: APIRequestContext) {
  const email = `learn-dates-${randomUUID()}@example.test`;
  const response = await request.post(`${API}/auth/register`, {data:{email,password,displayName:'日期教学验收'}});
  expect(response.status()).toBe(201);
  await page.goto('/login');
  await page.getByLabel('邮箱',{exact:true}).fill(email);
  await page.getByLabel('密码',{exact:true}).fill(password);
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(page.locator('.site-header')).toBeVisible();
  await page.goto('/learn/plan');
  await expect(page.getByLabel('练习计划名称',{exact:true})).toBeVisible();
}

test('32 days explains the block, save/reopen keeps input, and correction advances without saving', async ({page,request}, info) => {
  await openLesson(page,request);
  const writes: string[] = [];
  page.on('request', r => {
    if (/\/api\/(plans|tasks|mainlines|todos|sync)(\/|\?|$)/.test(r.url()) && !['GET','HEAD'].includes(r.method())) writes.push(r.url());
  });
  const next = page.getByRole('button',{name:'下一步',exact:true});
  await expect(next).toBeDisabled();
  await expect(next).toHaveAccessibleDescription(/请填写练习计划名称.*请选择练习开始日期.*请选择练习结束日期/);
  await page.getByLabel('练习计划名称',{exact:true}).fill('一个月学习论文');
  await page.getByLabel('练习开始日期',{exact:true}).fill('2026-10-18');
  await page.getByLabel('练习结束日期',{exact:true}).fill('2026-11-18');
  await expect(next).toBeDisabled();
  await expect(page.getByLabel('练习结束日期',{exact:true})).toHaveAttribute('aria-invalid','true');
  await expect(page.locator('#practice-end-error')).toContainText('当前选择 32 天，最多 31 天');
  await expect(next).toHaveAccessibleDescription(/2026-11-17/);
  await page.getByRole('button',{name:'保存练习',exact:true}).click();
  await expect(page.getByRole('status')).toHaveText('练习已保存在当前设备。');
  await expect(next).toBeDisabled();
  await page.reload();
  await expect(page.getByLabel('练习计划名称',{exact:true})).toHaveValue('一个月学习论文');
  await expect(page.getByLabel('练习结束日期',{exact:true})).toHaveValue('2026-11-18');
  await expect(next).toBeDisabled();
  await expect(page.locator('#practice-end-error')).toContainText('2026-11-17');
  await page.screenshot({path:info.outputPath('learning-dates-32-days.png'),fullPage:true});
  await page.getByLabel('练习结束日期',{exact:true}).fill('2026-11-17');
  await expect(page.locator('#practice-period-help')).toContainText('当前选择 31 天');
  await expect(page.getByLabel('练习结束日期',{exact:true})).toHaveAttribute('aria-invalid','false');
  await expect(page.locator('#practice-end-error')).toHaveCount(0);
  await expect(next).toBeEnabled();
  await expect(next).toHaveAccessibleDescription(/无需先保存练习/);
  await next.click();
  await expect(page.getByRole('heading',{name:'选择休息日',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'上一步',exact:true}).click();
  await expect(page.getByLabel('练习结束日期',{exact:true})).toHaveValue('2026-11-17');
  expect(writes).toEqual([]);
});

test('missing and reversed dates explain the actual reason and recover immediately', async ({page,request}) => {
  await openLesson(page,request);
  const next = page.getByRole('button',{name:'下一步',exact:true});
  await page.getByLabel('练习计划名称',{exact:true}).fill('日期练习');
  await page.getByLabel('练习开始日期',{exact:true}).fill('2026-10-18');
  await expect(next).toHaveAccessibleDescription(/请选择练习结束日期/);
  await page.getByLabel('练习结束日期',{exact:true}).fill('2026-10-17');
  await expect(next).toBeDisabled();
  await expect(page.locator('#practice-end-error')).toHaveText('结束日期不能早于开始日期。');
  await expect(page.locator('#practice-period-help')).not.toContainText('当前选择');
  await page.getByLabel('练习结束日期',{exact:true}).fill('2026-10-18');
  await expect(next).toBeEnabled();
  await expect(page.locator('#practice-period-help')).toContainText('当前选择 1 天');
  await page.getByLabel('练习开始日期',{exact:true}).fill('');
  await expect(next).toBeDisabled();
  await expect(next).toHaveAccessibleDescription(/请选择练习开始日期/);
  await page.getByLabel('练习开始日期',{exact:true}).fill('2026-10-18');
  await page.getByLabel('练习计划名称',{exact:true}).fill('  ');
  await expect(next).toHaveAccessibleDescription(/请填写练习计划名称/);
  await expect(next).toBeDisabled();
  await page.getByLabel('练习计划名称',{exact:true}).fill('恢复练习');
  await expect(next).toBeEnabled();
});

test('date explanation and all action buttons remain readable at 320px', async ({page,request}, info) => {
  await page.setViewportSize({width:320,height:740});
  await openLesson(page,request);
  await page.getByLabel('练习计划名称',{exact:true}).fill('一个月学习论文');
  await page.getByLabel('练习开始日期',{exact:true}).fill('2026-10-18');
  await page.getByLabel('练习结束日期',{exact:true}).fill('2026-11-18');
  const reason = page.locator('#practice-next-help');
  await reason.scrollIntoViewIfNeeded();
  await expect(reason).toBeVisible();
  await expect(reason).toContainText('2026-11-17');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button',{name:'下一步',exact:true}).scrollIntoViewIfNeeded();
  for (const name of ['上一步','保存练习','下一步']) {
    const button = page.getByRole('button',{name,exact:true});
    await expect(button).toBeInViewport();
    const bounds = await button.boundingBox();
    expect(bounds).not.toBeNull();
    expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(321);
    expect(await button.evaluate(el => {
      const rect = el.getBoundingClientRect();
      const target = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2);
      return !!target && (el === target || el.contains(target));
    })).toBe(true);
  }
  await page.screenshot({path:info.outputPath('learning-dates-320.png'),fullPage:true});
  await page.screenshot({path:info.outputPath('learning-dates-320-viewport.png')});
});
