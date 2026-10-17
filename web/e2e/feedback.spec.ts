import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const API='http://127.0.0.1:43131/api', password='FeedbackTest123!';
async function account(request:APIRequestContext){const email=`feedback-${randomUUID()}@example.test`;const result=await request.post(`${API}/auth/register`,{data:{email,password,displayName:'反馈验收'}});expect(result.status()).toBe(201);return{email,token:(await result.json()).accessToken as string};}
async function login(page:Page,email:string){await page.goto('/login');await page.getByLabel('邮箱',{exact:true}).fill(email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page.locator('.site-header')).toBeVisible();await page.goto('/feedback');await expect(page.getByRole('heading',{name:'反馈与诊断',exact:true})).toBeVisible();}
async function fill(page:Page,title='合成教学反馈'){await page.getByLabel('问题摘要',{exact:true}).fill(title);await page.getByLabel('操作步骤',{exact:true}).fill('打开教学，选择合成日期范围。');await page.getByLabel('期望的结果',{exact:true}).fill('能看到天数说明。');await page.getByLabel('实际的结果',{exact:true}).fill('说明不清楚。');}
async function preview(page:Page){await page.getByRole('button',{name:'预览待发送内容',exact:true}).click();await page.getByLabel('我已核对内容不含敏感信息，同意将以上内容发送给管理员。',{exact:true}).check();}
test('feedback consent, private submission, status, reply and safe diagnostic preview',async({page,request},info)=>{
  const user=await account(request);await login(page,user.email);await fill(page);
  await expect(page.getByRole('button',{name:'管理员处理',exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'生成并查看诊断摘要',exact:true}).click();
  const diagnostic=JSON.parse(await page.getByTestId('diagnostic-preview').innerText());
  expect(diagnostic).toMatchObject({schemaVersion:1,platform:'WEB',queueCount:0,conflictCount:0});
  for(const forbidden of ['email','accessToken','serverUrl','title','deviceId','logs'])expect(diagnostic).not.toHaveProperty(forbidden);
  await page.getByRole('button',{name:'预览待发送内容',exact:true}).click();await expect(page.getByRole('button',{name:'确认发送反馈',exact:true})).toBeDisabled();
  await page.getByLabel('我已核对内容不含敏感信息，同意将以上内容发送给管理员。',{exact:true}).check();
  await page.getByRole('button',{name:'确认发送反馈',exact:true}).click();await expect(page.getByText('反馈已发送。可在下方查看处理状态与管理员回复。',{exact:true})).toBeVisible();
  await page.getByLabel('补充信息',{exact:true}).fill('已用合成案例复现。');await page.getByRole('button',{name:'发送补充',exact:true}).click();await expect(page.getByText('已用合成案例复现。',{exact:true})).toBeVisible();
  expect((await request.get(`${API}/feedback`,{headers:{Authorization:`Bearer ${user.token}`}})).status()).toBe(200);
  await page.screenshot({path:info.outputPath('feedback-submitted.png'),fullPage:true});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('offline draft persists on reopen, stays private and never auto submits',async({page,request},info)=>{
  const user=await account(request);await login(page,user.email);await expect(page.getByText('这个列表还没有反馈。',{exact:true})).toBeVisible();
  await page.route('**/api/**',route=>route.abort('failed'));await fill(page,'离线合成草稿');await page.getByRole('button',{name:'保存本机草稿',exact:true}).click();
  await expect(page.getByText(/反馈草稿已保存在当前设备/)).toBeVisible();await page.reload();
  await expect(page.getByLabel('问题摘要',{exact:true})).toHaveValue('离线合成草稿');await expect(page.getByText(/当前离线：可编辑并手动保存/)).toBeVisible();
  await preview(page);await expect(page.getByRole('button',{name:'确认发送反馈',exact:true})).toBeDisabled();
  const list=await request.get(`${API}/feedback`,{headers:{Authorization:`Bearer ${user.token}`}});expect((await list.json()).totalElements).toBe(0);
  await page.screenshot({path:info.outputPath('feedback-offline.png'),fullPage:true});
});
test('lost submission response reuses durable key, locks body and does not duplicate tickets',async({page,request})=>{
  const user=await account(request);await login(page,user.email);await fill(page,'丢失响应测试');await preview(page);
  let lose=true;
  await page.route('**/api/feedback',async route=>{if(route.request().method()==='POST'&&lose){lose=false;const response=await route.fetch();expect(response.status()).toBe(201);await route.abort('failed');}else await route.continue();});
  await page.getByRole('button',{name:'确认发送反馈',exact:true}).click();await expect(page.getByText(/上次发送结果尚未确认，内容已锁定/)).toBeVisible();
  await expect(page.getByLabel('问题摘要',{exact:true})).toBeDisabled();await page.reload();
  await expect(page.getByLabel('问题摘要',{exact:true})).toHaveValue('丢失响应测试');await preview(page);await page.getByRole('button',{name:'重试同一份反馈',exact:true}).click();
  await expect(page.getByText('反馈已发送。可在下方查看处理状态与管理员回复。',{exact:true})).toBeVisible();
  const list=await request.get(`${API}/feedback`,{headers:{Authorization:`Bearer ${user.token}`}});expect((await list.json()).totalElements).toBe(1);
});
test('admin mobile handling preserves text after stale version and renders plain text safely',async({page,request},info)=>{
  const user=await account(request);let version=0,status='OPEN';const messages:unknown[]=[];
  const detail=()=>({ticket:{id:randomId,title:'合成管理员反馈',category:'BUG',status,version,createdAt:'2026-10-18T00:00:00Z',updatedAt:'2026-10-18T00:00:00Z'},steps:'<script>never execute</script>',expectedResult:'合成期望',actualResult:'合成结果',diagnostic:null,messages});
  const randomId=randomUUID();
  // The list is a UI fixture; server-side ADMIN authorization is exercised by FeedbackIntegrationTest.
  await page.route(/\/api\/auth\/(login|refresh)$/,async route=>{const response=await route.fetch();const body=await response.json();if(body.user)body.user.role='ADMIN';await route.fulfill({response,json:body});});
  await page.route('**/api/admin/feedback**',async route=>{
    if(route.request().method()==='PUT'){
      const body=route.request().postDataJSON();if(body.version!==version){await route.fulfill({status:409,json:{message:'反馈已被更新，请读取最新记录。'}});return;}
      messages.push({id:randomUUID(),authorKind:'ADMIN',body:body.body,createdAt:'2026-10-18T01:00:00Z'});version++;status=body.status;await route.fulfill({json:detail()});
    }else await route.fulfill({json:route.request().url().includes(randomId)?detail():{content:[detail().ticket],page:0,size:10,totalElements:1,totalPages:1}});
  });
  await login(page,user.email);await page.getByRole('button',{name:'管理员处理',exact:true}).click();await page.getByRole('button',{name:/待处理 · 功能异常 合成管理员反馈/}).click();
  await expect(page.getByText('<script>never execute</script>',{exact:true})).toBeVisible();
  await page.getByLabel('处理说明',{exact:true}).fill('合成处理说明');version=1;await page.getByRole('button',{name:'保存处理记录',exact:true}).click();await expect(page.getByRole('alert')).toContainText('当前文字仍保留');await expect(page.getByLabel('处理说明',{exact:true})).toHaveValue('合成处理说明');
  await page.getByRole('button',{name:'读取最新记录（保留输入）',exact:true}).click();await expect(page.getByLabel('处理说明',{exact:true})).toHaveValue('合成处理说明');
  await page.getByLabel('更新状态',{exact:true}).selectOption('RESOLVED');await page.getByRole('button',{name:'保存处理记录',exact:true}).click();await expect(page.getByText('合成处理说明',{exact:true})).toBeVisible();
  if(info.project.name==='mobile-web')await page.setViewportSize({width:320,height:760});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('feedback-admin.png'),fullPage:true});
});
