import { test, expect, type Page } from '@playwright/test';
import type { AiMode, AiDraft, AiSession } from '../src/aiPlanning';
function draft(mode: AiMode): AiDraft {
  return { schemaVersion: 1, mode, revision: 0, title: '吉他入门草稿（测试）', startDate: mode === 'PLAN' ? '2026-10-15' : null, endDate: mode === 'PLAN' ? '2026-10-31' : null, restDays: mode === 'PLAN' ? [7] : [], stages: mode === 'ROUTE' ? [{ localId: 'base', title: '基础知识' }] : [], items: [{ localId: 'one', title: '认识吉他', note: '练习十分钟，再回顾记录。', dueDate: mode === 'ROUTE' ? null : '2026-10-15', plannedMinutes: 30, stageId: mode === 'ROUTE' ? 'base' : null, prerequisites: [] }] };
}
async function fixture(page: Page, available = true) {
  // Synthetic data and provider replies only; this UI test never invokes a paid API.
  let session: AiSession | null = null, reads = 0, used = 0;
  const counts = { jobs: 0, applies: 0, creates: 0 };
  const user = { id: 'ai-ui-user', email: 'ai-ui@example.test', displayName: 'AI界面验收', role: 'USER', emailVerified: true };
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname, method = route.request().method(), body = method === 'GET' || method === 'DELETE' ? {} : route.request().postDataJSON();
    if (path === '/api/auth/config') return route.fulfill({ json: { apiVersion: 1, serverVersion: '0.1.9', registrationEnabled: false, invitationEnabled: true, planRestDaysEnabled: true, todoEnabled: true } });
    if (path === '/api/auth/refresh') return route.fulfill({ json: { accessToken: 'ai-ui-fixture-only', user, expiresInSeconds: 3600 } });
    if (path === '/api/ai/capabilities') return route.fulfill({ json: { available, message: 'AI 暂未开放，可继续手动创建或导入草稿。', maxInputChars: 8000 } });
    if (path === '/api/ai/quota') return route.fulfill({ json: { dailyLimit: 10, remaining: 10 - used, used, pending: 0, paused: false } });
    if (path === '/api/ai/sessions') {
      if (method === 'POST') { counts.creates++; session = { id: 'ai-session-fixture', mode: body.mode, recommendedMode: null, version: 0, draft: null, applied: null, expiresAt: '2026-11-14T00:00:00Z', turns: [] }; return route.fulfill({ json: session }); }
      return route.fulfill({ json: session ? [{ ...session, createdAt: '2026-10-15T12:00:00Z' }] : [] });
    }
    if (path === '/api/ai/sessions/ai-session-fixture') {
      if (method === 'DELETE') { session = null; return route.fulfill({ status: 204 }); }
      if (session?.turns.at(-1)?.state === 'QUEUED' && ++reads >= 3) { const turn = session.turns.at(-1)!; turn.state = 'SUCCEEDED'; turn.reply = session.mode ? '已准备草稿，请逐项核对后确认。' : '适合目标路线，不必每天固定推进。'; session.recommendedMode = session.mode ?? 'ROUTE'; session.draft = session.mode ? draft(session.mode) : null; session.version++; used++; }
      return route.fulfill({ json: session });
    }
    if (path.endsWith('/jobs') && session) { counts.jobs++; reads = 0; session.version++; session.turns.push({ id: `job-${counts.jobs}`, state: 'QUEUED', message: body.message, reply: null, error: null, createdAt: '2026-10-15T12:01:00Z' }); return route.fulfill({ status: 202, json: { id: `job-${counts.jobs}`, state: 'QUEUED' } }); }
    if (path.endsWith('/mode') && session) { session.mode = body.mode; session.version++; return route.fulfill({ json: session }); }
    if (path.endsWith('/draft') && session) { session.draft = JSON.parse(body.draftJson); session.version++; return route.fulfill({ json: session }); }
    if (path.endsWith('/apply') && session) { counts.applies++; session.applied = { mode: session.mode!, path: '/todos', count: session.draft!.items.length }; session.version++; return route.fulfill({ json: session.applied }); }
    return route.fulfill({ json: [] });
  });
  return counts;
}
test('conversation resumes after reload, asks for mode, supports editing and requires explicit apply', async ({ page, isMobile }, info) => {
  const counts = await fixture(page); await page.goto('/ai');
  await page.getByLabel('你的目标或修改要求').fill('我想学吉他，空闲才练习，没有固定结束日。');
  await expect(page.getByRole('button', { name: '发送给 AI', includeHidden:true })).toBeDisabled();
  await page.getByRole('checkbox', { name: /我同意将本次对话/ }).check(); await page.getByRole('button', { name: '发送给 AI' }).click();
  await expect(page).toHaveURL(/session=ai-session-fixture/); await page.reload();
  await expect(page.locator('.ai-mode-choice')).toContainText('AI 建议使用');
  await page.getByRole('button', { name: '选择目标路线', exact: true }).click();
  await page.getByLabel('你的目标或修改要求').fill('每次半小时，从基础开始，请分阶段。'); await page.getByRole('button', { name: '发送给 AI' }).click();
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();
  await expect(page.getByLabel('规划名称')).toHaveValue('吉他入门草稿（测试）'); expect(counts.creates).toBe(1); expect(counts.jobs).toBe(2); expect(counts.applies).toBe(0);
  await expect(page.getByRole('button', { name: '确认生成', exact: true })).toBeDisabled();
  await page.getByLabel('任务标题', { exact: true }).fill('认识吉他－手工调整');
  await expect(page.getByRole('button', { name: '发送给 AI', includeHidden:true })).toBeDisabled();
  await page.getByRole('button', { name: '保存草稿修改' }).click();
  await expect(page.getByLabel('任务标题', { exact: true })).toHaveValue('认识吉他－手工调整');
  await page.evaluate(() => window.scrollTo(0, 0)); await page.screenshot({ path: info.outputPath('ai-conversation-and-preview.png'), fullPage: true });
  await page.getByRole('checkbox', { name: /我已核对/ }).check(); await page.getByRole('button', { name: '确认生成', exact: true }).click();
  await expect(page.locator('.ai-applied')).toContainText('已生成 1 项内容'); expect(counts.applies).toBe(1);
  await page.reload();if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click(); await expect(page.locator('.ai-applied')).toContainText('已生成'); expect(counts.applies).toBe(1);
});
test('provider unavailable still permits validated import and keeps three entry modes', async ({ page, isMobile }, info) => {
  const counts = await fixture(page, false);
  for (const [path, name, mode] of [['/plans', '使用 AI 构建周期计划', 'PLAN'], ['/mainlines', '使用 AI 构建目标路线', 'ROUTE'], ['/todos', '使用 AI 构建今日待办', 'TODO']]) {
    await page.goto(path); await page.getByRole('link', { name }).click(); await expect(page.getByLabel('创建方式')).toHaveValue(mode!);
  }
  await expect(page.getByRole('button', { name: '发送给 AI' })).toBeDisabled();
  await page.getByText('给外部 AI 的格式说明', { exact: true }).click(); await expect(page.getByLabel('外部 AI 格式说明', { exact: true })).toHaveValue(/"mode": "TODO"/); await page.getByText('给外部 AI 的格式说明', { exact: true }).click();
  await page.getByRole('checkbox', { name: /我同意将本次对话/ }).check();
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();
  await page.getByText('导入外部 AI 草稿', { exact: true }).click(); await page.getByLabel('草稿 JSON', { exact: true }).fill(JSON.stringify(draft('TODO')));
  await page.getByRole('button', { name: '导入并校验草稿' }).click();
  await expect(page.getByLabel('规划名称')).toHaveValue('吉他入门草稿（测试）'); expect(counts.jobs).toBe(0); expect(counts.applies).toBe(0);
  if (isMobile) for (const width of [320, 390, 430, 844]) { await page.setViewportSize({ width, height: width === 844 ? 390 : 844 }); expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1); }
  await page.screenshot({ path: info.outputPath('ai-unavailable-manual-import.png'), fullPage: true });
});
test('stale save preserves local draft and never enables apply', async ({ page, isMobile }) => {
  await fixture(page, false); await page.goto('/ai?mode=TODO'); await page.getByRole('checkbox', { name: /我同意将本次对话/ }).check();
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();
  await page.getByText('导入外部 AI 草稿', { exact: true }).click(); await page.getByLabel('草稿 JSON', { exact: true }).fill(JSON.stringify(draft('TODO'))); await page.getByRole('button', { name: '导入并校验草稿' }).click();
  await page.getByLabel('任务标题', { exact: true }).fill('不能丢失的手工修改');
  await page.route('**/api/ai/sessions/*/draft', route => route.fulfill({ status: 409, json: { message: '草稿已变化，请刷新。' } }));
  await page.getByRole('button', { name: '保存草稿修改' }).click(); await expect(page.getByRole('alert')).toContainText('草稿已变化');
  await expect(page.getByLabel('任务标题', { exact: true })).toHaveValue('不能丢失的手工修改'); await expect(page.getByRole('button', { name: '确认生成', exact: true })).toBeDisabled();
});
