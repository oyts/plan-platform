import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const API='http://127.0.0.1:43131/api'; const password='RouteStagesAcceptance123!';
type User={email:string;token:string};
async function account(request:APIRequestContext):Promise<User> {
  const email=`route-${randomUUID()}@example.test`;
  const response=await request.post(`${API}/auth/register`,{data:{email,password,displayName:'阶段验收'}}); expect(response.status()).toBe(201);
  return {email,token:(await response.json()).accessToken};
}
async function call(request:APIRequestContext,user:User,method:string,path:string,data?:unknown,status=200) {
  const response=await request.fetch(`${API}${path}`,{method,data,headers:{Authorization:`Bearer ${user.token}`}}); expect(response.status()).toBe(status);
  return status===204?null:response.json();
}
async function login(page:Page,user:User) {
  await page.goto('/login'); await page.getByLabel('邮箱',{exact:true}).fill(user.email); await page.getByLabel('密码',{exact:true}).fill(password);
  await page.getByRole('button',{name:'登录',exact:true}).click(); await expect(page.locator('.site-header .brand')).toBeVisible();
}
test('route UI creates stages and opens the focused step editor with optional prerequisites',async({page,request,isMobile},info)=>{
  const user=await account(request); await login(page,user); await page.goto('/mainlines');
  await page.getByLabel('路线名称',{exact:true}).fill('吉他学习路线'); await page.getByRole('button',{name:'新建路线',exact:true}).click();
  await expect(page.getByRole('heading',{name:'吉他学习路线',exact:true})).toBeVisible();
  expect((await call(request,user,'GET','/mainlines'))[0].executionMode).toBe('FLEXIBLE');
  await page.getByRole('button',{name:'管理阶段',exact:true}).click();
  let panel=page.getByRole('dialog'); await panel.getByLabel('阶段名称',{exact:true}).fill('基础练习');
  await panel.getByRole('button',{name:'添加阶段',exact:true}).click(); await expect(panel.locator('.route-stage-row')).toContainText('基础练习');
  await panel.getByRole('button',{name:'关闭面板',exact:true}).click();
  const stage=(await call(request,user,'GET','/mainlines'))[0].stages[0];
  for(const title of ['认识吉他','和弦练习']) {
    await page.getByRole('button',{name:'添加步骤',exact:true}).click(); panel=page.getByRole('dialog');
    await panel.getByLabel('步骤标题',{exact:true}).fill(title); await panel.getByLabel('所属阶段',{exact:true}).selectOption(stage.id);
    if(title==='和弦练习') { await panel.getByLabel('前置条件',{exact:true}).selectOption('EXPLICIT'); await panel.getByLabel('认识吉他',{exact:true}).check(); }
    await panel.getByRole('button',{name:'保存步骤',exact:true}).click(); await expect(panel).not.toBeVisible();
  }
  const row=page.locator('.mainline-step').filter({has:page.getByRole('heading',{name:'和弦练习',exact:true})});
  await expect(row.getByRole('button',{name:'开始',exact:true})).toBeDisabled(); await expect(row).toContainText('等待：认识吉他');
  await page.screenshot({path:info.outputPath('stage-route.png')});
  await row.getByRole('button',{name:'编辑步骤',exact:true}).click(); panel=page.getByRole('dialog');
  await expect(panel.getByLabel('步骤标题',{exact:true})).toHaveValue('和弦练习'); await expect(panel.getByLabel('认识吉他',{exact:true})).toBeChecked();
  await panel.getByLabel('步骤标题',{exact:true}).fill('和弦练习修订');
  await page.screenshot({path:info.outputPath('step-editor.png')});
  const box=await panel.boundingBox(); expect(box).not.toBeNull(); if(!isMobile) expect(box!.x).toBeGreaterThan(500);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth)).toBeLessThanOrEqual(1);
  await panel.getByRole('button',{name:'保存步骤',exact:true}).click();
  await page.locator('.mainline-step').filter({has:page.getByRole('heading',{name:'认识吉他',exact:true})}).getByRole('button',{name:'完成',exact:true}).click();
  await expect(page.locator('.mainline-step').filter({hasText:'和弦练习修订'}).getByRole('button',{name:'开始',exact:true})).toBeEnabled();
  await page.getByRole('button',{name:'管理阶段',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:'编辑阶段',exact:true}).click();
  await page.getByRole('dialog').getByLabel('阶段名称',{exact:true}).fill('基础知识');
  await page.getByRole('dialog').getByRole('button',{name:'保存阶段',exact:true}).click();
  page.once('dialog',dialog=>dialog.accept()); await page.getByRole('dialog').getByRole('button',{name:'删除阶段',exact:true}).click();
  await expect(page.getByRole('dialog').locator('.route-stage-row')).toHaveCount(0);
  await page.getByRole('dialog').getByRole('button',{name:'关闭面板',exact:true}).click();
  await expect(page.locator('.mainline-step')).toHaveCount(2); const updated=(await call(request,user,'GET','/mainlines'))[0]; expect(updated.items.every((item:any)=>item.stageId===null)).toBe(true);
});
test('long routes fold, search and paginate without losing progress totals',async({page,request},info)=>{
  const user=await account(request); const line=await call(request,user,'POST','/mainlines',{name:'长路线',color:'#002FA7',executionMode:'FLEXIBLE'},201);
  const stage=await call(request,user,'POST',`/mainlines/${line.id}/stages`,{name:'曲目练习',sortOrder:0},201);
  for(let i=0;i<12;i++) await call(request,user,'POST',`/mainlines/${line.id}/items`,{title:`曲目 ${i+1}`,description:'分段练习',acceptance:[],plannedMinutes:15,sortOrder:i,routing:{stageId:stage.id,dependencyMode:'INHERIT',prerequisiteIds:[]}},201);
  await login(page,user); await page.goto(`/mainlines?line=${line.id}`);
  await expect(page.locator('.mainline-step')).toHaveCount(10); await page.getByRole('button',{name:'下一页',exact:true}).click();
  await expect(page.locator('.mainline-step')).toHaveCount(2); await expect(page.getByRole('heading',{name:'曲目 12',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'折叠全部',exact:true}).click(); await expect(page.locator('.mainline-step')).toHaveCount(0);
  await page.getByRole('button',{name:'展开全部',exact:true}).click(); await page.getByLabel('搜索步骤',{exact:true}).fill('曲目 12');
  await expect(page.locator('.mainline-step')).toHaveCount(1); await expect(page.locator('.route-stage-toggle').first()).toContainText('0 / 12');
  await page.screenshot({path:info.outputPath('stage-search.png'),fullPage:true});
});
test('cached route step text edits remain visible after offline reopen and replay once',async({page,request})=>{
  const user=await account(request); const line=await call(request,user,'POST','/mainlines',{name:'离线路线',color:'#002FA7',executionMode:'FLEXIBLE'},201);
  const step=await call(request,user,'POST',`/mainlines/${line.id}/items`,{title:'离线练习',description:'原说明',acceptance:['练习'],plannedMinutes:20,sortOrder:0},201);
  await login(page,user); await page.goto(`/mainlines?line=${line.id}`); await expect(page.getByRole('heading',{name:'离线练习',exact:true})).toBeVisible();
  await page.route('**/api/**',route=>route.abort('failed')); await page.reload(); await expect(page.getByLabel('连接状态')).toContainText('离线工作区');
  await page.getByRole('button',{name:'编辑步骤',exact:true}).click(); const panel=page.getByRole('dialog');
  await expect(panel.getByLabel('前置条件',{exact:true})).toBeDisabled(); await panel.getByLabel('步骤标题',{exact:true}).fill('离线练习－修订');
  await panel.getByRole('button',{name:'保存步骤',exact:true}).click(); await expect(panel).not.toBeVisible();
  await expect(page.locator('.mainline-step')).toContainText('本地修改 · 待同步'); await page.reload();
  await expect(page.getByRole('heading',{name:'离线练习－修订',exact:true})).toBeVisible();
  await page.unroute('**/api/**'); await page.goto('/sync'); await page.getByRole('button',{name:'同步等待项',exact:true}).click(); await expect(page.getByText('队列为空',{exact:true})).toBeVisible();
  const latest=(await call(request,user,'GET','/mainlines'))[0].items; expect(latest).toHaveLength(1); expect(latest[0]).toMatchObject({id:step.id,title:'离线练习－修订',dependencyMode:'INHERIT',version:1});
});
