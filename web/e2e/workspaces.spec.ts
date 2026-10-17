import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const API = 'http://127.0.0.1:43131/api'; const password = 'WorkspaceAcceptance123!';
type Account = { email: string; token: string };
async function account(request: APIRequestContext): Promise<Account> {
  const email = `workspace-${randomUUID()}@example.test`;
  const response = await request.post(`${API}/auth/register`, {data:{email,password,displayName:'模式验收账号'}}); expect(response.status()).toBe(201);
  return {email,token:(await response.json()).accessToken};
}
async function call(request:APIRequestContext, user:Account, method:string, path:string, data?:unknown, status=200) {
  const response = await request.fetch(`${API}${path}`,{method,data,headers:{Authorization:`Bearer ${user.token}`}}); expect(response.status()).toBe(status);
  return status === 204 ? null : response.json();
}
async function login(page:Page,user:Account) {
  await page.goto('/login'); await page.getByLabel('邮箱',{exact:true}).fill(user.email); await page.getByLabel('密码',{exact:true}).fill(password);
  await page.getByRole('button',{name:'登录',exact:true}).click(); await expect(page.locator('.site-header .brand')).toBeVisible();
}
async function logout(page:Page) {
  const more = page.getByRole('button',{name:'更多',exact:true}); if(await more.isVisible()) await more.click();
  await page.getByRole('button',{name:'模式验收账号 · 退出',exact:true}).click(); await expect(page.getByRole('heading',{name:'登录计划平台'})).toBeVisible();
}
async function seed(request:APIRequestContext,user:Account) {
  const today = (await call(request,user,'GET','/dashboard/today')).date;
  const plan = await call(request,user,'POST','/plans',{name:'周期计划验收',description:'合成验收数据',startDate:today,endDate:today,restDays:[7],overallTagName:'总计',overallTagColor:'#002FA7'},201);
  await call(request,user,'POST',`/plans/${plan.id}/tasks`,{title:'只属于周期计划的任务',date:today,description:'合成验收数据',category:'CUSTOM',plannedMinutes:30,acceptance:['验收'],required:true,sortOrder:0},201);
  const line = await call(request,user,'POST','/mainlines',{name:'吉他目标路线验收',description:'合成路线，不按日历要求完成',color:'#002FA7'},201);
  const first = await call(request,user,'POST',`/mainlines/${line.id}/items`,{title:'认识吉他',description:'合成步骤',acceptance:['验收'],plannedMinutes:20,sortOrder:0},201);
  await call(request,user,'POST',`/mainlines/${line.id}/items`,{title:'和弦练习',description:'合成步骤',acceptance:['验收'],plannedMinutes:20,sortOrder:1},201);
  return {plan,line,first};
}
test('workspaces replace navigation, preserve each position and keep plan totals separate',async({page,request,isMobile}, info)=>{
  const user=await account(request); const {plan,line,first}=await seed(request,user); await login(page,user);
  await expect(page.getByLabel('工作模式')).toHaveValue('plan'); await page.goto(`/plans/${plan.id}`);
  const before = await call(request,user,'GET','/dashboard/today');
  await page.getByLabel('工作模式').selectOption('route'); await expect(page.getByRole('heading',{name:'继续路线',exact:true})).toBeVisible();
  // The heading renders before the API/cache commit; finish the online precondition before cutting the network.
  await expect(page.getByRole('heading',{name:'吉他目标路线验收',exact:true})).toBeVisible();
  const nav = page.getByRole('navigation',{name:isMobile?'手机主导航':'主导航',exact:true});
  for(const label of ['继续','路线','进展']) await expect(nav.getByRole('link',{name:label,exact:true})).toBeVisible();
  await expect(nav.getByRole('link',{name:'日程',exact:true})).toHaveCount(0); await expect(page.getByRole('heading',{name:'只属于周期计划的任务',exact:true})).toHaveCount(0);
  await page.screenshot({path:info.outputPath('route-home.png'),fullPage:true});
  await page.getByRole('link',{name:'继续这条路线',exact:true}).click(); await expect(page).toHaveURL(new RegExp(`mainlines\\?line=${line.id}`));
  await expect(page.getByRole('heading',{name:'认识吉他',exact:true})).toBeVisible(); await expect(page.locator('input[type=date]')).toHaveCount(0);
  await page.getByLabel('工作模式').selectOption('plan'); await expect(page).toHaveURL(new RegExp(`/plans/${plan.id}$`));
  await expect(page.getByRole('heading',{name:'只属于周期计划的任务',exact:true})).toBeVisible();
  await page.getByLabel('工作模式').selectOption('route'); await expect(page).toHaveURL(new RegExp(`mainlines\\?line=${line.id}`));
  await call(request,user,'POST',`/mainlines/items/${first.id}/complete`,{note:'合成验收',version:first.version});
  await nav.getByRole('link',{name:'进展',exact:true}).click(); await expect(page.getByRole('heading',{name:'路线进展',exact:true})).toBeVisible();
  await expect(page.locator('.metric').filter({hasText:'完成步骤'})).toContainText('1 / 2');
  const after=await call(request,user,'GET','/dashboard/today'); expect(after.completed).toBe(before.completed); expect(after.total).toBe(before.total); expect(after.plannedMinutes).toBe(before.plannedMinutes);
  await page.reload(); await expect(page.getByLabel('工作模式')).toHaveValue('route');
  await page.getByLabel('工作模式').selectOption('plan'); await expect(page).toHaveURL(new RegExp(`/plans/${plan.id}$`));
});
test('mode preferences are per account and cached routes reopen offline',async({page,request})=>{
  const owner=await account(request); const friend=await account(request); await seed(request,owner); await login(page,owner);
  await page.getByLabel('工作模式').selectOption('route'); await expect(page.getByRole('heading',{name:'继续路线',exact:true})).toBeVisible();
  await expect(page.getByRole('heading',{name:'吉他目标路线验收',exact:true})).toBeVisible();
  // Keep shell delivery online; native reopen is tested separately with a real TCP outage.
  await page.route('**/api/**', route => route.abort('failed')); await page.reload(); await expect(page.getByLabel('工作模式')).toHaveValue('route');
  await expect(page.getByRole('heading',{name:'吉他目标路线验收',exact:true})).toBeVisible(); await expect(page.getByLabel('连接状态')).toContainText('离线工作区');
  await page.unroute('**/api/**'); await page.reload(); await expect(page.getByLabel('工作模式')).toHaveValue('route'); await logout(page); await login(page,friend); await expect(page.getByLabel('工作模式')).toHaveValue('plan');
  await page.getByLabel('工作模式').selectOption('route'); await expect(page.getByText('还没有目标路线。可以先创建一条，再逐步添加学习或项目步骤。',{exact:true})).toBeVisible(); await expect(page.getByRole('heading',{name:'吉他目标路线验收',exact:true})).toHaveCount(0);
  await logout(page); await login(page,owner); await expect(page.getByLabel('工作模式')).toHaveValue('route'); await expect(page.getByRole('heading',{name:'继续路线',exact:true})).toBeVisible();
});
test('each recovery page filters its own mode and keeps the old URLs usable',async({page,request})=>{
  const user=await account(request); const {plan,line}=await seed(request,user);
  await call(request,user,'DELETE',`/plans/${plan.id}`,undefined,204); await call(request,user,'DELETE',`/mainlines/${line.id}`,undefined,204);
  await login(page,user); await page.goto('/records'); await expect(page.locator('.trash-row').filter({hasText:'周期计划验收'})).toBeVisible(); await expect(page.locator('.trash-row').filter({hasText:'吉他目标路线验收'})).toHaveCount(0);
  await page.goto('/route-records'); await expect(page.getByLabel('工作模式')).toHaveValue('route'); await expect(page.locator('.trash-row').filter({hasText:'吉他目标路线验收'})).toBeVisible(); await expect(page.locator('.trash-row').filter({hasText:'周期计划验收'})).toHaveCount(0);
  await expect(page.getByRole('heading',{name:'计时流水',exact:true})).toHaveCount(0);
  await page.locator('.trash-row').filter({hasText:'吉他目标路线验收'}).getByRole('button',{name:'恢复',exact:true}).click();
  await expect(page.locator('.trash-row').filter({hasText:'吉他目标路线验收'})).toHaveCount(0);
  await page.goto('/mainlines'); await expect(page.getByRole('heading',{name:'目标路线',exact:true})).toBeVisible(); await expect(page.getByRole('heading',{name:'吉他目标路线验收',exact:true})).toBeVisible();
});
