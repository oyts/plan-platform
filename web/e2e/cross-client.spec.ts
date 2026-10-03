import { test, expect, type APIRequestContext, type Browser, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

const API = 'http://127.0.0.1:43131/api';
const SITE = 'http://127.0.0.1:43130';
const PASSWORD = 'Acceptance123456!';
type Account = { email: string; token: string };

async function account(request: APIRequestContext): Promise<Account> {
  const email = `acceptance-${randomUUID()}@example.test`;
  const response = await request.post(`${API}/auth/register`, { data: { email, password: PASSWORD, displayName: '验收测试账号' } });
  expect(response.status()).toBe(201);
  return { email, token: (await response.json()).accessToken };
}
async function call(request: APIRequestContext, user: Account, method: string, path: string, data?: unknown, status = 200) {
  const response = await request.fetch(`${API}${path}`, { method, data, headers: { Authorization: `Bearer ${user.token}` } });
  expect(response.status(), `${method} ${path}`).toBe(status);
  return response.status() === 204 ? null : response.json();
}
async function login(page: Page, user: Account) {
  await page.goto(`${SITE}/login`);
  await page.getByLabel('邮箱', { exact: true }).fill(user.email);
  await page.getByLabel('密码', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.locator('.site-header .brand')).toBeVisible();
  await page.goto(`${SITE}/`);
  await expect(page.getByRole('heading', { name: '今日控制台' })).toBeVisible();
}
async function secondPage(browser: Browser, run: (page: Page) => Promise<void>) {
  const context = await browser.newContext({ baseURL: SITE, viewport: { width: 390, height: 844 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai', serviceWorkers: 'block' });
  try { await run(await context.newPage()); } finally { await context.close(); }
}
async function plan(request: APIRequestContext, user: Account, name = '跨端验收计划') {
  const today = (await call(request, user, 'GET', '/dashboard/today')).date;
  return call(request, user, 'POST', '/plans', { name, description: '独立内存测试库', startDate: today, endDate: today, overallTagName: '总计', overallTagColor: '#002FA7' }, 201);
}
async function task(request: APIRequestContext, user: Account, currentPlan: any, title = '跨端任务') {
  return call(request, user, 'POST', `/plans/${currentPlan.id}/tasks`, {
    date: currentPlan.startDate, title, description: '验证保存和同步', category: 'JAVA', plannedMinutes: 15,
    startTime: '09:00', endTime: '09:15', acceptance: ['记录验收'], resourceUrl: '', required: true, sortOrder: 0,
  }, 201);
}
async function queueRows(page: Page): Promise<any[]> {
  return page.evaluate(() => new Promise((resolve, reject) => {
    const request = indexedDB.open('plan-platform-offline');
    request.onsuccess = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains('operations')) { db.close(); resolve([]); return; }
      const query = db.transaction('operations').objectStore('operations').getAll();
      query.onsuccess = () => { db.close(); resolve(query.result); }; query.onerror = () => { db.close(); reject(query.error); };
    };
    request.onerror = () => reject(request.error);
  }));
}
async function enterNewPlan(page: Page, name: string) {
  await page.getByLabel('计划名称', { exact: true }).fill(name);
  await page.getByRole('button', { name: '保存计划', exact: true }).click();
}
async function navigate(page: Page, name: string) {
  if (name === '主线') { await page.getByLabel('工作模式').selectOption('route'); name = '路线'; }
  const link = page.getByRole('link', { name, exact: true });
  if (!(await link.count()) && await page.getByRole('button', { name: '更多', exact: true }).isVisible()) await page.getByRole('button', { name: '更多', exact: true }).click();
  await link.click();
}
async function logout(page: Page) {
  if (await page.getByRole('button', { name: '更多', exact: true }).isVisible()) await page.getByRole('button', { name: '更多', exact: true }).click();
  await page.getByRole('button', { name: '验收测试账号 · 退出' }).click();
}

test('two independent sessions see shared data but another account cannot read or modify it', async ({ page, browser, request }) => {
  const owner = await account(request); const friend = await account(request);
  await login(page, owner); await navigate(page, '计划');
  await enterNewPlan(page, '设备间共享计划');
  await expect(page.getByRole('heading', { name: '设备间共享计划', exact: true })).toBeVisible();
  const planId = page.url().split('/').at(-1)!;
  await page.getByPlaceholder('新标签名称').fill('跨端标签');
  await page.getByRole('button', { name: '添加标签' }).click();
  await expect(page.getByText('跨端标签', { exact: true }).first()).toBeVisible();
  await secondPage(browser, async mobile => {
    await login(mobile, owner); await mobile.goto(`${SITE}/plans/${planId}`);
    await expect(mobile.getByRole('heading', { name: '设备间共享计划', exact: true })).toBeVisible();
    await expect(mobile.getByText('跨端标签', { exact: true }).first()).toBeVisible();
    await mobile.getByRole('button', { name: '添加任务', exact: true }).click();
    await mobile.getByLabel('任务标题', { exact: true }).fill('手机新增的任务');
    await mobile.getByRole('button', { name: '保存任务', exact: true }).click();
    await expect(mobile.getByRole('heading', { name: '手机新增的任务', exact: true })).toBeVisible();
  });
  await page.reload(); await expect(page.getByRole('heading', { name: '手机新增的任务', exact: true })).toBeVisible();
  await call(request, friend, 'GET', `/plans/${planId}/tasks`, undefined, 404);
  await call(request, friend, 'POST', `/plans/${planId}/tags`, { name: '越权标签', color: '#002FA7' }, 404);
  expect(await call(request, friend, 'GET', '/plans')).toEqual([]);
});

test('offline creation survives reload and does not leak queue data to a different account', async ({ page, request }) => {
  const owner = await account(request); const friend = await account(request);
  await login(page, owner); await navigate(page, '计划');
  await page.context().setOffline(true);
  await enterNewPlan(page, '离线计划');
  await expect(page.getByRole('status')).toContainText('已加入离线队列');
  const rows = await queueRows(page); expect(rows).toHaveLength(1);
  expect(JSON.stringify(rows)).not.toContain('Bearer'); expect(JSON.stringify(rows)).not.toContain(PASSWORD);
  await page.context().setOffline(false);
  await logout(page);
  await expect(page.getByRole('heading', { name: '登录计划平台' })).toBeVisible();
  await login(page, friend); await page.goto(`${SITE}/sync`);
  await expect(page.getByText('队列为空', { exact: true })).toBeVisible();
  await logout(page);
  await expect(page.getByRole('heading', { name: '登录计划平台' })).toBeVisible();
  await login(page, owner); await page.goto(`${SITE}/sync`);
  await expect(page.locator('.sync-row')).toHaveCount(1);
  await page.reload();
  await expect(page.locator('.sync-row')).toHaveCount(1);
  await page.getByRole('button', { name: '同步等待项', exact: true }).click();
  await expect(page.getByText('队列为空', { exact: true })).toBeVisible();
  expect((await call(request, owner, 'GET', '/plans')).filter((row: any) => row.name === '离线计划')).toHaveLength(1);
});

test('a committed write with a lost response replays the same key and creates only one plan', async ({ page, request }) => {
  const owner = await account(request); await login(page, owner);
  await navigate(page, '计划');
  await page.route('**/api/plans', async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return; }
    const response = await route.fetch(); expect(response.status()).toBe(201);
    await route.abort('failed');
  });
  await enterNewPlan(page, '响应丢失验收');
  await expect(page.getByRole('status')).toContainText('已加入离线队列');
  expect(await page.evaluate(() => navigator.onLine)).toBe(true);
  await page.unroute('**/api/plans');
  await navigate(page, '同步');
  await page.getByRole('button', { name: '同步等待项', exact: true }).click();
  await expect(page.getByText('队列为空', { exact: true })).toBeVisible();
  expect((await call(request, owner, 'GET', '/plans')).filter((row: any) => row.name === '响应丢失验收')).toHaveLength(1);
});

test('stale edits stop the queue and do not overwrite another device or submit later writes', async ({ page, request }) => {
  const owner = await account(request); const currentPlan = await plan(request, owner); const currentTask = await task(request, owner, currentPlan);
  await login(page, owner); await page.goto(`${SITE}/plans/${currentPlan.id}`);
  await page.locator('.task-row').getByRole('button', { name: '编辑', exact: true }).click();
  await page.getByLabel('任务标题', { exact: true }).fill('离线旧版本标题');
  await page.context().setOffline(true); await page.getByRole('button', { name: '保存修改', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('已加入离线队列');
  await call(request, owner, 'PUT', `/tasks/${currentTask.id}`, { ...currentTask, title: '另一端保留标题', version: currentTask.version });
  await page.context().setOffline(false); await navigate(page, '计划');
  await page.context().setOffline(true); await enterNewPlan(page, '冲突后的等待计划');
  await expect.poll(async () => (await queueRows(page)).length).toBe(2);
  await page.context().setOffline(false); await navigate(page, '同步');
  await page.getByRole('button', { name: '同步等待项', exact: true }).click();
  await expect(page.getByText('同步已暂停：请先处理队首冲突，后续操作仍保留在本机。')).toBeVisible();
  await expect(page.locator('.sync-row--conflict')).toHaveCount(1);
  expect((await queueRows(page)).find(row => row.data.name === '冲突后的等待计划').attempts).toBe(0);
  const savedTasks = await call(request, owner, 'GET', `/plans/${currentPlan.id}/tasks`);
  expect(savedTasks[0].title).toBe('另一端保留标题');
  expect((await call(request, owner, 'GET', '/plans')).some((row: any) => row.name === '冲突后的等待计划')).toBe(false);
  page.once('dialog', dialog => dialog.accept());
  await page.locator('.sync-row--conflict').getByRole('button', { name: '放弃本地操作' }).click();
  await page.getByRole('button', { name: '同步等待项', exact: true }).click();
  await expect(page.getByText('队列为空', { exact: true })).toBeVisible();
  expect((await call(request, owner, 'GET', '/plans')).some((row: any) => row.name === '冲突后的等待计划')).toBe(true);
});

test('refresh restores the browser session and logging out one device leaves the other signed in', async ({ page, browser, request }) => {
  const owner = await account(request); await login(page, owner); await page.reload();
  await expect(page.getByRole('heading', { name: '今日控制台' })).toBeVisible();
  const local = await page.evaluate(() => Object.keys(localStorage).filter(key => /token/i.test(key)));
  expect(local).toEqual([]);
  await secondPage(browser, async mobile => {
    await login(mobile, owner);
    await logout(page);
    await expect(page.getByRole('heading', { name: '登录计划平台' })).toBeVisible(); await page.reload();
    await expect(page.getByRole('heading', { name: '登录计划平台' })).toBeVisible();
    await mobile.reload(); await expect(mobile.getByRole('heading', { name: '今日控制台' })).toBeVisible();
  });
});

test('server-backed timer, completion, pagination and Chinese exports remain consistent', async ({ page, browser, request }) => {
  const owner = await account(request); const currentPlan = await plan(request, owner); const currentTask = await task(request, owner, currentPlan, '今日计时任务');
  await task(request, owner, currentPlan, '分页任务二'); await task(request, owner, currentPlan, '分页任务三');
  await login(page, owner);
  const row = page.locator('.task-row').filter({ has: page.getByRole('heading', { name: '今日计时任务', exact: true }) });
  await row.getByRole('button', { name: '开始', exact: true }).click(); await expect(row).toContainText('RUNNING');
  await secondPage(browser, async mobile => {
    await login(mobile, owner);
    const mobileRow = mobile.locator('.task-row').filter({ has: mobile.getByRole('heading', { name: '今日计时任务', exact: true }) });
    await expect(mobileRow).toContainText('RUNNING');
    await mobileRow.getByRole('button', { name: '暂停', exact: true }).click(); await expect(mobileRow).toContainText('PAUSED');
  });
  const latest = (await call(request, owner, 'GET', `/plans/${currentPlan.id}/tasks`)).find((item: any) => item.id === currentTask.id);
  await call(request, owner, 'POST', `/tasks/${currentTask.id}/manual-time`, { seconds: 600, version: latest.progress.version });
  await page.reload(); await row.getByRole('button', { name: '完成', exact: true }).click();
  await page.getByRole('dialog').getByLabel('记录验收', { exact: true }).check();
  await page.getByRole('dialog').getByLabel('结果备注', { exact: true }).fill('中文验收通过');
  await page.getByRole('button', { name: '确认完成', exact: true }).click(); await expect(row).toContainText('COMPLETED');
  const range = `from=${currentPlan.startDate}&to=${currentPlan.endDate}`;
  const paged = await call(request, owner, 'GET', `/plans/${currentPlan.id}/tasks/page?${range}&size=2&page=1`);
  expect(paged.totalElements).toBe(3); expect(paged.content).toHaveLength(1);
  const stats = await call(request, owner, 'GET', `/plans/${currentPlan.id}/stats?${range}`);
  expect(stats.completedCount).toBe(1);
  const csv = await request.get(`${API}/plans/${currentPlan.id}/tasks/export?${range}`, { headers: { Authorization: `Bearer ${owner.token}` } });
  expect(csv.status()).toBe(200); expect(await csv.text()).toContain('今日计时任务');
});

test('mainline step two remains locked until another session completes step one', async ({ page, browser, request }) => {
  const owner = await account(request);
  const line = await call(request, owner, 'POST', '/mainlines', { name: '顺序主线验收', description: '', color: '#002FA7' }, 201);
  const first = await call(request, owner, 'POST', `/mainlines/${line.id}/items`, { title: '第一步', description: '', acceptance: [], plannedMinutes: 10, sortOrder: 0 }, 201);
  const next = await call(request, owner, 'POST', `/mainlines/${line.id}/items`, { title: '第二步', description: '', acceptance: [], plannedMinutes: 10, sortOrder: 1 }, 201);
  await login(page, owner); await navigate(page, '主线');
  const nextRow = page.locator('.mainline-step').filter({ has: page.getByRole('heading', { name: '第二步', exact: true }) });
  await expect(nextRow.getByRole('button', { name: '开始', exact: true })).toBeDisabled();
  await call(request, owner, 'POST', `/mainlines/items/${next.id}/start?version=${next.version}`, undefined, 409);
  await secondPage(browser, async mobile => {
    await login(mobile, owner); await navigate(mobile, '主线');
    await mobile.locator('.mainline-step').filter({ has: mobile.getByRole('heading', { name: '第一步', exact: true }) }).getByRole('button', { name: '完成', exact: true }).click();
    await expect(mobile.locator('.mainline-step').first()).toContainText('COMPLETED');
  });
  await page.reload(); await expect(nextRow.getByRole('button', { name: '开始', exact: true })).toBeEnabled();
  await nextRow.getByRole('button', { name: '开始', exact: true }).click(); await expect(nextRow).toContainText('RUNNING');
  expect(first.id).not.toBe(next.id);
});

test('visited PWA shell can reopen offline without caching private API responses', async ({ browser, request }) => {
  const owner = await account(request);
  const context = await browser.newContext({ baseURL: SITE, serviceWorkers: 'allow' });
  try {
    const page = await context.newPage(); await login(page, owner);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload(); await expect(page.getByRole('heading', { name: '今日控制台' })).toBeVisible();
    await expect.poll(async () => page.evaluate(() => Boolean(navigator.serviceWorker.controller))).toBe(true);
    const cachedUrls = await page.evaluate(async () => {
      const urls: string[] = []; for (const name of await caches.keys()) for (const entry of await (await caches.open(name)).keys()) urls.push(entry.url);
      return urls;
    });
    expect(cachedUrls.some(url => new URL(url).pathname.startsWith('/api/'))).toBe(false);
    expect(cachedUrls.some(url => /\/assets\/LoginView-.*\.js$/.test(new URL(url).pathname))).toBe(true);
    const loginAsset = cachedUrls.find(url => /\/assets\/LoginView-.*\.js$/.test(new URL(url).pathname))!;
    expect(await page.evaluate(async url => (await caches.match(url))?.headers.get('content-type'), loginAsset)).toContain('javascript');
    await context.setOffline(true);
    expect(await page.evaluate(async url => (await fetch(url)).status, loginAsset)).toBe(200);
    await page.reload();
    await expect(page.getByRole('heading', { name: '今日控制台' })).toBeVisible();
    await expect(page.getByLabel('连接状态')).toContainText('离线工作区');
  } finally { await context.close(); }
});

test('offline workspace reopens with cached tasks, queues visible edits and clears pages on logout', async ({ browser, request }, testInfo) => {
  const owner = await account(request); const friend = await account(request);
  const currentPlan = await plan(request, owner); const currentTask = await task(request, owner, currentPlan, '离线缓存任务');
  const context = await browser.newContext({ baseURL: SITE, serviceWorkers: 'allow', viewport: testInfo.project.use.viewport, timezoneId: 'Asia/Shanghai', locale: 'zh-CN' });
  try {
    const page = await context.newPage(); await login(page, owner);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.goto(`${SITE}/plans/${currentPlan.id}`);
    await expect(page.getByRole('heading', { name: '离线缓存任务', exact: true })).toBeVisible();
    await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller))).toBe(true);
    await context.setOffline(true); await page.reload();
    await expect(page.getByRole('heading', { name: '离线缓存任务', exact: true })).toBeVisible();
    await expect(page.getByLabel('连接状态')).toContainText('离线工作区');
    await page.locator('.task-row').getByRole('button', { name: '编辑', exact: true }).click();
    await page.getByLabel('任务标题', { exact: true }).fill('离线可见的新标题');
    await page.getByRole('button', { name: '保存修改', exact: true }).click();
    await expect(page.getByRole('heading', { name: '离线可见的新标题', exact: true })).toBeVisible();
    await expect(page.locator('.task-row')).toContainText('本地修改 · 待同步');
    await page.screenshot({ path: testInfo.outputPath('offline-workspace.png'), fullPage: true });
    await page.reload(); await expect(page.getByRole('heading', { name: '离线可见的新标题', exact: true })).toBeVisible();
    expect((await call(request, owner, 'GET', `/plans/${currentPlan.id}/tasks`))[0].title).toBe(currentTask.title);
    // Logout while disconnected: no automatic resurrection of the local profile.
    page.once('dialog', dialog => dialog.accept());
    await logout(page);
    await expect(page.getByRole('heading', { name: '登录计划平台' })).toBeVisible(); await page.reload();
    await expect(page.getByRole('heading', { name: '登录计划平台' })).toBeVisible();
    const count = await page.evaluate(() => new Promise<number>(resolve => {
      const req = indexedDB.open('plan-platform-cache'); req.onsuccess = () => { const db = req.result; const query = db.transaction('responses').objectStore('responses').count(); query.onsuccess = () => { db.close(); resolve(query.result); }; };
    }));
    expect(count).toBe(0);
    await context.setOffline(false); await login(page, friend); await navigate(page, '计划');
    await expect(page.getByRole('heading', { name: '离线可见的新标题', exact: true })).toHaveCount(0);
    await page.goto(`${SITE}/sync`); await expect(page.getByText('队列为空', { exact: true })).toBeVisible();
    await logout(page);
    await expect(page.getByRole('heading', { name: '登录计划平台' })).toBeVisible(); await login(page, owner);
    await page.goto(`${SITE}/sync`); await expect(page.locator('.sync-row')).toHaveCount(1);
    await page.getByRole('button', { name: '同步等待项', exact: true }).click();
    await expect(page.getByText('队列为空', { exact: true })).toBeVisible();
    expect((await call(request, owner, 'GET', `/plans/${currentPlan.id}/tasks`))[0].title).toBe('离线可见的新标题');
  } finally { await context.close(); }
});

test('plan rest days are chosen at creation, editable and visible in the cached schedule', async ({ page, request }, testInfo) => {
  const owner = await account(request); await login(page, owner);
  const today = (await call(request, owner, 'GET', '/dashboard/today')).date;
  const end = new Date(`${today}T00:00:00`); end.setDate(end.getDate() + 6);
  await navigate(page, '计划');
  await expect(page.getByRole('checkbox', { name: '星期六', exact: true })).not.toBeChecked();
  await expect(page.getByRole('checkbox', { name: '星期日', exact: true })).not.toBeChecked();
  await page.getByLabel('开始日期', { exact: true }).fill(today);
  await page.getByLabel('结束日期', { exact: true }).fill(end.toLocaleDateString('sv-SE'));
  await page.getByRole('checkbox', { name: '星期一', exact: true }).check();
  await enterNewPlan(page, '自定义休息日计划');
  await expect(page.getByRole('heading', { name: '自定义休息日计划', exact: true })).toBeVisible();
  const planId = page.url().split('/').at(-1)!;
  const currentPlan = (await call(request, owner, 'GET', '/plans')).find((row: any) => row.id === planId);
  expect(currentPlan.restDays).toEqual([1]);
  await task(request, owner, currentPlan, '不因休息日删除的任务');
  await page.reload(); await expect(page.getByRole('heading', { name: '不因休息日删除的任务' })).toBeVisible();
  await page.goto(`${SITE}/schedule?plan=${planId}`);
  await expect(page.locator('.rail-task')).toContainText('不因休息日删除的任务');
  await expect(page.locator('.rail-day--rest')).toHaveCount(1);
  await expect(page.locator('.rail-day--rest header')).toContainText('周一');
  await expect(page.locator('.rail-day').filter({ hasText: '周六' })).not.toHaveClass(/rail-day--rest/);
  await page.getByRole('link', { name: '编辑计划设置', exact: true }).click();
  await page.getByRole('button', { name: '编辑计划设置', exact: true }).click();
  const editor = page.getByRole('form', { name: '编辑计划设置', exact: true });
  await editor.getByRole('checkbox', { name: '星期一', exact: true }).uncheck();
  await editor.getByRole('checkbox', { name: '星期六', exact: true }).check();
  await editor.getByRole('button', { name: '保存计划设置', exact: true }).click();
  await expect(page.locator('.page-head')).toContainText('每周 星期六 休息');
  await page.getByRole('button', { name: '编辑计划设置', exact: true }).click();
  await editor.getByRole('checkbox', { name: '星期六', exact: true }).uncheck();
  await editor.getByRole('checkbox', { name: '星期日', exact: true }).check();
  await editor.locator('.rest-days').screenshot({ path: testInfo.outputPath('rest-days-picker.png') });
  await page.context().setOffline(true);
  await editor.getByRole('button', { name: '保存计划设置', exact: true }).click();
  await expect(page.locator('.page-head')).toContainText('每周 星期日 休息');
  await expect(page.locator('.page-head')).toContainText('本地修改 · 待同步');
  await navigate(page, '日程');
  await expect(page.locator('.rail-task')).toContainText('不因休息日删除的任务');
  await expect(page.locator('.rail-day--rest header')).toContainText('周日');
  expect((await call(request, owner, 'GET', '/plans')).find((row: any) => row.id === planId).restDays).toEqual([6]);
  await page.context().setOffline(false);
  await navigate(page, '同步');
  await page.getByRole('button', { name: '同步等待项', exact: true }).click();
  await expect(page.getByText('队列为空', { exact: true })).toBeVisible();
  expect((await call(request, owner, 'GET', '/plans')).find((row: any) => row.id === planId).restDays).toEqual([7]);
});
