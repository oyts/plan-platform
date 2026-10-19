import { test, expect, type Page } from '@playwright/test';
import type { AiDraft, AiMode, AiSession } from '../src/aiPlanning';
const target = '11111111-1111-4111-8111-111111111111';
const user = { id: 'adjustment-ui-user', email: 'adjustment-ui@example.test', displayName: '调整界面合成验收', role: 'USER', emailVerified: true };
function original(mode: AiMode): AiDraft {
  return { schemaVersion:1,mode,revision:0,title:mode === 'TODO' ? '调整今日待办' : '原规划（界面测试）',startDate:mode === 'PLAN'?'2026-10-01':null,endDate:mode === 'PLAN'?'2026-10-31':null,restDays:mode === 'PLAN'?[7]:[],stages:mode === 'ROUTE'?[{localId:'stage_1',title:'原阶段'}]:[],items:[1,2].map(i=>({localId:`existing_${i}`,title:i===1?'已完成事项（测试）':'未开始事项（测试）',note:'合成内容，非用户数据',dueDate:mode === 'ROUTE'?null:'2026-10-16',plannedMinutes:mode === 'TODO'?0:30,stageId:mode === 'ROUTE'?'stage_1':null,prerequisites:mode === 'ROUTE'&&i===2?['existing_1']:[]})) };
}
async function fixture(page: Page, mode: AiMode, conflict = false) {
  let session: AiSession | null = null;
  const counts = { adjustments:0, saves:0, applies:0, jobs:0 };
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname, method = route.request().method();
    const body = method === 'GET' || method === 'DELETE' ? {} : route.request().postDataJSON();
    if(path === '/api/auth/config')return route.fulfill({json:{apiVersion:1,serverVersion:'0.1.10',registrationEnabled:false,invitationEnabled:true,todoEnabled:true,planRestDaysEnabled:true}});
    if(path === '/api/auth/refresh')return route.fulfill({json:{accessToken:'adjust-ui-fixture-only',user,expiresInSeconds:3600}});
    if(path === '/api/ai/capabilities')return route.fulfill({json:{available:true,adjustmentEnabled:true,maxInputChars:8000}});
    if(path === '/api/ai/quota')return route.fulfill({json:{dailyLimit:5,remaining:5,used:0,pending:0,paused:false}});
    if(path === '/api/ai/adjustments'){
      expect(body).toEqual({mode,targetId:target,consent:true});counts.adjustments++;
      const base=original(mode);session={id:'adjust-ui-session',mode,recommendedMode:mode,version:0,draft:structuredClone(base),applied:null,expiresAt:'2026-11-15T00:00:00Z',turns:[],adjustment:{original:base,protectedItems:['existing_1'],path:mode === 'PLAN'?`/plans/${target}`:mode === 'ROUTE'?`/mainlines?line=${target}`:'/todos'}};
      return route.fulfill({json:session});
    }
    if(path === '/api/ai/sessions')return route.fulfill({json:session?[{...session,adjustment:true,createdAt:'2026-10-16T00:00:00Z'}]:[]});
    if(path === '/api/ai/sessions/adjust-ui-session')return route.fulfill({json:session});
    if(path.endsWith('/draft') && session){counts.saves++;session.draft=JSON.parse(body.draftJson);session.version++;return route.fulfill({json:session});}
    if(path.endsWith('/jobs')){counts.jobs++;return route.fulfill({status:503,json:{message:'合成上游不可用，不实际调用'}});}
    if(path.endsWith('/apply') && session){
      counts.applies++;
      if(conflict)return route.fulfill({status:409,json:{message:'原对象已被其他设备修改；当前草稿仍保留。'}});
      session.applied={mode,path:session.adjustment!.path,count:1};session.version++;return route.fulfill({json:session.applied});
    }
    return route.fulfill({json:[]});
  });
  return counts;
}
for (const mode of ['PLAN','ROUTE','TODO'] as const) test(`consented ${mode} adjustment preserves history, previews differences and confirms once`, async ({page,isMobile},info) => {
  const counts=await fixture(page,mode);await page.goto(`/ai?mode=${mode}&target=${target}`);
  const load=page.getByRole('button',{name:'载入所选内容（不调用 AI）',exact:true});
  await expect(load).toBeDisabled();expect(counts.adjustments).toBe(0);
  await page.getByRole('checkbox',{name:/我同意将所选对象/}).check();await load.click();
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();
  await expect(page.getByRole('region',{name:'调整前后对照'})).toContainText('目前没有改动');
  await expect(page.getByLabel('任务标题',{exact:true}).nth(0)).toBeDisabled();
  await expect(page.getByRole('button',{name:'确认应用修改',exact:true})).toBeDisabled();
  await page.getByLabel('任务标题',{exact:true}).nth(1).fill('未开始事项－节奏调整');
  await expect(page.getByRole('region',{name:'调整前后对照'})).toContainText('原内容');
  await expect(page.getByRole('region',{name:'调整前后对照'})).toContainText('未开始事项－节奏调整');
  await expect(page.getByRole('button',{name:'确认应用修改',exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'保存草稿修改'}).click();
  await page.reload();if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();await expect(page.getByLabel('任务标题',{exact:true}).nth(1)).toHaveValue('未开始事项－节奏调整');
  await page.getByText('给外部 AI 的格式说明',{exact:true}).click();await expect(page.getByLabel('外部 AI 格式说明',{exact:true})).toHaveValue(/existing_1/);await page.getByText('给外部 AI 的格式说明',{exact:true}).click();
  await page.screenshot({path:info.outputPath(`ai-adjust-${mode.toLowerCase()}.png`),fullPage:true});
  await page.getByRole('checkbox',{name:/我已逐项核对前后差异/}).check();await page.getByRole('button',{name:'确认应用修改',exact:true}).click();
  await expect(page.locator('.ai-applied')).toContainText('已应用 1 处修改');expect(counts.applies).toBe(1);expect(counts.adjustments).toBe(1);expect(counts.jobs).toBe(0);
  await page.reload();if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();await expect(page.locator('.ai-applied')).toContainText('执行历史保留');expect(counts.applies).toBe(1);
});
test('409 retains the proposed changes and never claims they were applied', async ({page,isMobile},info) => {
  const counts=await fixture(page,'PLAN',true);await page.goto(`/ai?mode=PLAN&target=${target}`);
  await page.getByRole('checkbox',{name:/我同意将所选对象/}).check();await page.getByRole('button',{name:'载入所选内容（不调用 AI）'}).click();
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();
  await page.getByLabel('任务标题',{exact:true}).nth(1).fill('必须保留的建议');await page.getByRole('button',{name:'保存草稿修改'}).click();
  await page.getByRole('checkbox',{name:/我已逐项核对前后差异/}).check();await page.getByRole('button',{name:'确认应用修改',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('其他设备');await expect(page.getByLabel('任务标题',{exact:true}).nth(1)).toHaveValue('必须保留的建议');await expect(page.locator('.ai-applied')).toHaveCount(0);
  expect(counts.applies).toBe(1);
  if(isMobile)for(const width of [320,390,430,844]){await page.setViewportSize({width,height:width===844?390:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);}
  await page.screenshot({path:info.outputPath('ai-adjust-conflict-retained.png'),fullPage:true});
});
test('pending local operations block snapshot capture without clearing the queue', async ({page}) => {
  const counts=await fixture(page,'PLAN');await page.goto(`/ai?mode=PLAN&target=${target}`);await expect(page.getByRole('heading',{name:'AI 规划助手'})).toBeVisible();
  await page.evaluate(async owner => {
    const db=await new Promise<IDBDatabase>((resolve,reject)=>{const r=indexedDB.open('plan-platform-offline',3);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
    await new Promise<void>((resolve,reject)=>{const tx=db.transaction('operations','readwrite');tx.objectStore('operations').put({id:'adjust-queued-fixture',ownerId:owner,method:'PUT',url:'/tasks/synthetic',status:'PENDING',data:{version:0},createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),attempts:0});tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);});db.close();window.dispatchEvent(new CustomEvent('plan-platform:queue-change'));
  },user.id);
  await page.getByRole('checkbox',{name:/我同意将所选对象/}).check();await expect(page.getByRole('button',{name:'载入所选内容（不调用 AI）'})).toBeDisabled();await expect(page.getByRole('status').filter({hasText:'本机仍有'})).toContainText('1 项');expect(counts.adjustments).toBe(0);
});
test('pacing examples only fill the message and protect an unsent request',async({page},info)=>{
  const counts=await fixture(page,'PLAN');await page.goto(`/ai?mode=PLAN&target=${target}`);
  await page.getByRole('checkbox',{name:/我同意将所选对象/}).check();await page.getByRole('button',{name:'载入所选内容（不调用 AI）'}).click();
  const guide=page.getByRole('region',{name:'调整节奏指南'});
  await expect(guide).toContainText('其中 1 项有执行历史');
  await guide.getByRole('button',{name:'周期延长一周'}).click();
  await expect(page.getByLabel('你的目标或修改要求')).toHaveValue(/2026-11-07/);
  await expect(page.getByLabel('你的目标或修改要求')).toHaveValue(/保持原休息日/);
  expect(counts.jobs).toBe(0);expect(counts.applies).toBe(0);
  page.once('dialog',d=>d.dismiss());await guide.getByRole('button',{name:'每项缩短到15分钟'}).click();
  await expect(page.getByLabel('你的目标或修改要求')).toHaveValue(/2026-11-07/);
  page.once('dialog',d=>d.accept());await guide.getByRole('button',{name:'每项缩短到15分钟'}).click();
  await expect(page.getByLabel('你的目标或修改要求')).toHaveValue(/15分钟/);
  await expect(page.getByLabel('任务标题',{exact:true}).first()).toBeDisabled();
  await guide.scrollIntoViewIfNeeded();expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({path:info.outputPath('ai-pacing-guide.png'),fullPage:true});
  expect(counts.jobs).toBe(0);expect(counts.applies).toBe(0);
});
