import {test,expect,type Page} from '@playwright/test';
import {randomUUID} from 'node:crypto';
async function setup(page:Page){
  page.on('response',response=>{if(response.url().includes('/api/auth/'))console.log('Synthetic auth response',new URL(response.url()).pathname,response.status());});
  const auth=await page.request.post('/api/auth/register',{data:{email:`reliability-${randomUUID()}@example.test`,password:`Synthetic-${randomUUID()}!`,displayName:'AI可靠性合成验收'}});expect(auth.status()).toBe(201);
  const token=(await auth.json()).accessToken;
  expect((await page.context().cookies()).some(cookie=>cookie.name==='plan_refresh')).toBeTruthy();
  const headers={Authorization:`Bearer ${token}`};
  const response=await page.request.post('/api/ai/sessions',{headers,data:{mode:'PLAN',consent:true}});expect(response.ok()).toBeTruthy();
  const session=await response.json();await page.goto('/ai?session='+session.id);await expect(page.getByRole('button',{name:'发送并讨论',exact:true})).toBeVisible();
  return {headers,id:session.id};
}
async function generate(page:Page,isMobile:boolean){
  await page.getByLabel('你的目标或修改要求').fill('请根据已确认要求生成规划');await page.getByRole('button',{name:'生成规划草稿',exact:true}).click();
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();
  await expect(page.getByLabel('规划名称')).toHaveValue('合成Java规划',{timeout:20000});
}
test('real local API: dialogue, retained requirements, one repair and explicit apply gate',async({page,isMobile},info)=>{
  const s=await setup(page);await page.getByLabel('你的目标或修改要求').fill('零基础学Java，两个月，每天两小时，星期日休息');await page.getByRole('button',{name:'发送并讨论',exact:true}).click();
  await expect(page.locator('.ai-assistant-message')).toContainText('已记住你的目标',{timeout:15000});
  await expect(page.locator('.ai-preview .ai-empty')).toHaveCount(1);
  await page.getByText('需求笔记（发送过的要求持续保留）',{exact:false}).click();await expect(page.getByLabel('需求笔记',{exact:true})).toHaveValue(/星期日休息/);
  await page.reload();await page.getByText('需求笔记（发送过的要求持续保留）',{exact:false}).click();await expect(page.getByLabel('需求笔记',{exact:true})).toHaveValue(/星期日休息/);
  await generate(page,isMobile);await expect(page.getByRole('button',{name:'确认生成',exact:true})).toBeDisabled();
  const current=await (await page.request.get('/api/ai/sessions/'+s.id,{headers:s.headers})).json();expect(current.turns.at(-1).diagnostic.attempts).toBe(2);expect(current.requirementsText).toContain('星期日休息');
  expect(current.turns.at(-1).diagnostic.details).toHaveLength(2);expect(current.turns.at(-1).diagnostic.details[0].inputChars).toBeGreaterThan(0);expect(current.turns.at(-1).diagnostic.details[0].outputTokens).toBe(100);
  expect((await page.request.get('/api/admin/ai/usage',{headers:s.headers})).status()).toBe(403);
  const quota=await (await page.request.get('/api/ai/quota',{headers:s.headers})).json();expect(quota.used).toBe(2);expect(quota.pending).toBe(0);
  expect((await (await page.request.get('/api/plans',{headers:s.headers})).json()).length).toBe(0);
  await page.screenshot({path:info.outputPath('reliability-preview.png'),fullPage:true});
});
test('requirements unsaved edit blocks send and survives refresh; save preserves manual draft edits',async({page,isMobile})=>{
  await setup(page);await generate(page,isMobile);await page.getByLabel('任务标题',{exact:true}).fill('不能丢失的手改标题');
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^对话/}).click();
  await page.getByText('需求笔记（发送过的要求持续保留）',{exact:false}).click();await page.getByLabel('需求笔记',{exact:true}).fill('明确改为周六休息，每天一小时');
  if(isMobile){
    await expect(page.locator('.ai-message-form')).toHaveCSS('position','static');
    // A short viewport represents the available area while a phone keyboard is open.
    await page.setViewportSize({width:390,height:500});
    await page.getByRole('button',{name:'保存需求笔记',exact:true}).click({trial:true});
  }
  await page.getByRole('button',{name:'刷新对话',exact:true}).click();await expect(page.getByLabel('需求笔记',{exact:true})).toHaveValue('明确改为周六休息，每天一小时');
  await expect(page.getByRole('button',{name:'按要求修改草稿',exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'保存需求笔记',exact:true}).click();
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();
  await expect(page.getByLabel('任务标题',{exact:true})).toHaveValue('不能丢失的手改标题');
});
test('known rejected draft keeps readable prose, safe diagnostic and manual paths',async({page,isMobile},info)=>{
  await setup(page);await page.getByLabel('你的目标或修改要求').fill('[invalid-always] 合成无效输出');await page.getByRole('button',{name:'生成规划草稿',exact:true}).click();
  await expect(page.locator('.ai-assistant-message')).toContainText('本次额度已退回',{timeout:15000});
  await expect(page.locator('.ai-assistant-message')).toContainText('文字仅供参考');await expect(page.locator('.ai-assistant-message')).toContainText('已尝试 2 次');
  await expect(page.locator('.ai-assistant-message')).not.toContainText('可补充更明确的日期');
  await page.screenshot({path:info.outputPath('reliability-failure.png'),fullPage:true});
  if(isMobile)await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name:/^草稿/}).click();await expect(page.locator('.ai-preview .ai-empty')).toBeVisible();
});
