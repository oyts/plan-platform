import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const API='http://127.0.0.1:43131/api'; const password='TodoAcceptance123!';
async function account(request:APIRequestContext) {
  const email=`todo-${randomUUID()}@example.test`;
  const response=await request.post(`${API}/auth/register`,{data:{email,password,displayName:'待办验收账号'}});
  expect(response.status()).toBe(201); return {email,token:(await response.json()).accessToken as string};
}
async function login(page:Page,email:string) {
  await page.goto('/login'); await page.getByLabel('邮箱',{exact:true}).fill(email); await page.getByLabel('密码',{exact:true}).fill(password);
  await page.getByRole('button',{name:'登录',exact:true}).click(); await expect(page.locator('.site-header')).toBeVisible();
  await page.getByLabel('工作模式').selectOption('todo'); await expect(page.getByRole('heading',{name:'今日待办',exact:true})).toBeVisible();
}
test('public homepage presents modes and real release state without requiring an account',async({page},info)=>{
  await page.goto('/'); await expect(page.getByRole('heading',{level:1})).toContainText('把今天安排好');
  await expect(page.getByRole('link',{name:'鄂ICP备2026050813号-1'})).toHaveAttribute('href','https://beian.miit.gov.cn/');
  await page.getByRole('tab',{name:'目标路线',exact:true}).click(); await expect(page.getByRole('tabpanel')).toContainText('随时暂停');
  await page.getByRole('tab',{name:'今日待办',exact:true}).click(); await expect(page.getByRole('tabpanel')).toContainText('准备开始练吉他');
  await expect(page.getByRole('button',{name:'正式安装包尚未发布'})).toBeDisabled();
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({path:info.outputPath('product-home.png'),fullPage:true});
  await page.getByRole('button',{name:'查看 Android 发布状态'}).click(); await expect(page.getByRole('dialog')).toContainText('目前仅供受邀测试');
  await page.getByRole('button',{name:'关闭面板'}).click(); await page.getByRole('button',{name:'开始使用网页版'}).click(); await expect(page).toHaveURL(/\/login$/);
});
test('Android QR code and PC link use only the release manifest',async({page})=>{
  const release={version:'0.1.4',sha256:'a'.repeat(64),bytes:2048};
  await page.route('**/downloads/manifest.json',route=>route.fulfill({json:{schemaVersion:1,windows:{...release,url:'/downloads/plan.exe'},android:{...release,url:'/downloads/plan.apk'}}}));
  await page.goto('/welcome'); await expect(page.getByRole('link',{name:'下载 Windows 安装包'})).toHaveAttribute('href',/\/downloads\/plan\.exe$/);
  await page.getByRole('button',{name:'扫码下载 Android'}).click(); const dialog=page.getByRole('dialog');
  await expect(dialog.getByRole('img',{name:'Android 安装包下载二维码'})).toHaveAttribute('src',/^data:image\/png;base64,/);
  await expect(dialog.getByRole('link',{name:'直接下载 APK'})).toHaveAttribute('href',/\/downloads\/plan\.apk$/);
});
test('legacy server gets a version-specific error, not a certificate guess',async({page})=>{
  await page.route('**/api/auth/config',route=>route.fulfill({status:404,json:{error:'接口不存在。'}}));
  await page.goto('/login'); await expect(page.getByRole('alert')).toContainText('新版账号接口不存在');
  await expect(page.getByRole('button',{name:'重新检查连接'})).toBeVisible();
});
test('todo CRUD, pagination and workspace isolation work on desktop and phone',async({page,request},info)=>{
  const user=await account(request); const headers={Authorization:`Bearer ${user.token}`};
  const today=(await (await request.get(`${API}/dashboard/today`,{headers})).json()).date;
  for(let i=0;i<12;i++) expect((await request.post(`${API}/todos`,{headers,data:{title:`临时事项 ${String(i).padStart(2,'0')}`,dueDate:today}})).status()).toBe(201);
  await login(page,user.email); await expect(page.locator('.todo-row')).toHaveCount(10);
  await page.getByRole('button',{name:'下一页',exact:true}).click(); await expect(page.locator('.todo-row')).toHaveCount(2);
  await page.getByRole('button',{name:'上一页',exact:true}).click();
  let row=page.locator('.todo-row').filter({hasText:'临时事项 00'});
  await row.getByRole('button',{name:'编辑',exact:true}).click(); const dialog=page.getByRole('dialog');
  await dialog.getByLabel('待办标题').fill('修改后的临时事项'); await dialog.getByLabel('备注',{exact:true}).fill('独立待办备注');
  await dialog.getByRole('button',{name:'保存待办'}).click(); await expect(dialog).not.toBeVisible();
  row=page.locator('.todo-row').filter({hasText:'修改后的临时事项'}); await expect(row).toContainText('独立待办备注');
  await row.getByRole('checkbox').check(); await expect(row).toHaveCount(0); await page.getByLabel('状态',{exact:true}).selectOption('DONE');
  row=page.locator('.todo-row').filter({hasText:'修改后的临时事项'}); await expect(row).toBeVisible();
  page.on('dialog',dialog=>dialog.accept()); await row.getByRole('button',{name:'删除',exact:true}).click(); await expect(row).toHaveCount(0);
  await page.getByLabel('日期范围').selectOption('TRASH'); await expect(row).toBeVisible(); await row.getByRole('button',{name:'恢复待办'}).click(); await expect(row).toHaveCount(0);
  await page.getByLabel('日期范围').selectOption('DAY'); await expect(row).toBeVisible();
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({path:info.outputPath('todos.png'),fullPage:true});
  await page.getByLabel('工作模式').selectOption('plan'); await expect(page.locator('.todo-row')).toHaveCount(0);
  await page.getByLabel('工作模式').selectOption('route'); await expect(page.locator('.todo-row')).toHaveCount(0);
  await page.getByLabel('工作模式').selectOption('todo'); await page.getByLabel('状态',{exact:true}).selectOption('DONE'); await expect(row).toBeVisible();
  const other=await account(request); await expect(await (await request.get(`${API}/todos?from=${today}&to=${today}`,{headers:{Authorization:`Bearer ${other.token}`}})).json()).toEqual([]);
});
test('todo drafts survive offline reload and synchronize without duplication',async({page,request})=>{
  const user=await account(request); await login(page,user.email); await expect(page.locator('.todo-quick')).toBeVisible();
  await expect(page.getByText('没有匹配的待办。',{exact:false})).toBeVisible();
  await page.route('**/api/**',route=>route.abort('failed'));
  await page.getByLabel('快速记录').fill('离线临时事项'); await page.getByRole('button',{name:'记一件事'}).click();
  await expect(page.locator('.todo-row')).toContainText('离线临时事项'); await expect(page.locator('.todo-row')).toContainText('待同步');
  await page.reload(); await expect(page.getByLabel('工作模式')).toHaveValue('todo'); await expect(page.locator('.todo-row')).toContainText('离线临时事项');
  await page.unroute('**/api/**'); await page.goto('/sync'); await page.getByRole('button',{name:'同步等待项'}).click();
  await expect(page.getByText('队列为空',{exact:true})).toBeVisible(); await page.goto('/todos');
  await expect(page.locator('.todo-row')).toHaveCount(1); await expect(page.locator('.local-badge')).toHaveCount(0);
});
test('stale todo completion restores the checkbox and preserves the other device edit',async({page,request})=>{
  const user=await account(request); const headers={Authorization:`Bearer ${user.token}`};
  const today=(await (await request.get(`${API}/dashboard/today`,{headers})).json()).date;
  const item=await (await request.post(`${API}/todos`,{headers,data:{title:'冲突前待办',dueDate:today}})).json();
  await login(page,user.email); const row=page.locator('.todo-row').filter({hasText:'冲突前待办'});
  await expect(row).toBeVisible();
  expect((await request.put(`${API}/todos/${item.id}`,{headers,data:{title:'其他设备修改',note:'',dueDate:today,done:false,deleted:false,version:item.version}})).status()).toBe(200);
  await row.getByRole('checkbox').click(); await expect(page.getByRole('alert')).toContainText('其他设备修改');
  await expect(row.getByRole('checkbox')).not.toBeChecked();
  const saved=await (await request.get(`${API}/todos?from=${today}&to=${today}`,{headers})).json();
  expect(saved[0].title).toBe('其他设备修改'); expect(saved[0].done).toBe(false);
});
