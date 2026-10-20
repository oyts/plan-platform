import {test,expect,type Page,type APIRequestContext} from '@playwright/test';
import {randomUUID} from 'node:crypto';
const API='http://127.0.0.1:43131/api',password='PersonalDataTest123!';
async function account(request:APIRequestContext){const email=`data-${randomUUID()}@example.test`;const response=await request.post(`${API}/auth/register`,{data:{email,password,displayName:'数据中心验收'}});expect(response.status()).toBe(201);return{email,token:(await response.json()).accessToken as string};}
async function login(page:Page,email:string){await page.goto('/login');await page.getByLabel('邮箱',{exact:true}).fill(email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page.locator('.site-header')).toBeVisible();await page.goto('/exports');await expect(page.getByRole('heading',{name:'个人数据中心',exact:true})).toBeVisible();}
async function fixture(request:APIRequestContext){const a=await account(request),headers={Authorization:`Bearer ${a.token}`};await request.post(`${API}/todos`,{headers,data:{title:'合成备份待办',note:'中文,带引号"的备注',dueDate:'2026-10-18'}});await request.post(`${API}/mainlines`,{headers,data:{name:'合成长期路线',description:'无期限',color:'#002FA7',executionMode:'FLEXIBLE'}});await request.put(`${API}/weekly/reviews/2026-10-12`,{headers,data:{wins:'源账号周收获',blockers:'无',nextFocus:'下周小步推进'}});const response=await request.get(`${API}/data/backup`,{headers});expect(response.status()).toBe(200);return{file:await response.json(),account:a};}
async function select(page:Page,file:unknown){await page.getByLabel('选择私有 JSON 备份',{exact:true}).setInputFiles({name:'synthetic-private.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(file))});await expect(page.getByText('synthetic-private.json',{exact:true})).toBeVisible();}
const consent='我确认这是获准使用的私有备份，同意将文件发送到当前登录服务器进行只读预检。';
const confirmation='我已核对以上数量和限制，确认恢复为新副本。日周复盘保存在下方只读副本中，不覆盖当前记录。';
test('private backup preview, restore copies, duplicate protection and JSON download',async({page,request},info)=>{
  const source=await fixture(request),target=await account(request),headers={Authorization:`Bearer ${target.token}`};
  await request.post(`${API}/todos`,{headers,data:{title:'原来的待办',dueDate:'2026-10-18'}});await request.put(`${API}/weekly/reviews/2026-10-12`,{headers,data:{wins:'不可覆盖的原复盘',blockers:'',nextFocus:''}});
  let writes=0;page.on('request',r=>{if(r.method()==='POST'&&r.url().includes('/api/data/'))writes++;});
  await login(page,target.email);await select(page,source.file);expect(writes).toBe(0);await expect(page.getByRole('button',{name:'只读预检，不恢复数据',exact:true})).toBeDisabled();
  await page.getByLabel(consent,{exact:true}).check();await page.getByRole('button',{name:'只读预检，不恢复数据',exact:true}).click();await expect(page.getByRole('heading',{name:'预检通过 · 3 条记录',exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await expect(page.getByRole('button',{name:'确认恢复为新副本',exact:true})).toBeDisabled();await page.getByLabel(confirmation,{exact:true}).check();await page.getByRole('button',{name:'确认恢复为新副本',exact:true}).click();await expect(page.getByRole('status').filter({hasText:/恢复批次 .* 已完成/})).toBeVisible();
  await page.getByLabel(confirmation,{exact:true}).check();await page.getByRole('button',{name:'确认恢复为新副本',exact:true}).click();await expect(page.getByText('共 1 批',{exact:true})).toBeVisible();
  await page.getByText('2026-10-12 这一周 · 只读副本',{exact:true}).click();await expect(page.getByText('源账号周收获',{exact:true})).toBeVisible();
  const old=await request.get(`${API}/weekly/reviews/2026-10-12`,{headers});expect((await old.json()).wins).toBe('不可覆盖的原复盘');
  const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'下载私有 JSON 备份',exact:true}).click();const download=await downloadPromise;expect(download.suggestedFilename()).toMatch(/^plan-cabin-private-.*\.json$/);
  const chunks:Buffer[]=[];for await(const chunk of (await download.createReadStream())!)chunks.push(Buffer.from(chunk));const downloaded=JSON.parse(Buffer.concat(chunks).toString());expect(downloaded.records.todos.length).toBe(2);expect(downloaded.records.weekly_reviews.length).toBe(2);expect(JSON.stringify(downloaded)).not.toContain(target.email);
  await page.screenshot({path:info.outputPath('personal-data-restored.png'),fullPage:true});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('bad format stays local and server whitelist rejects an owner field without restoring',async({page,request})=>{
  const source=await fixture(request),target=await account(request);await login(page,target.email);
  await page.getByLabel('选择私有 JSON 备份',{exact:true}).setInputFiles({name:'legacy.json',mimeType:'application/json',buffer:Buffer.from('{"schemaVersion":6}')});await expect(page.getByRole('alert')).toContainText('旧版迁移文件');
  source.file.records.todos[0].owner_id=randomUUID();await select(page,source.file);await page.getByLabel(consent,{exact:true}).check();await page.getByRole('button',{name:'只读预检，不恢复数据',exact:true}).click();await expect(page.getByRole('alert')).toContainText('未知字段');await expect(page.getByRole('button',{name:'确认恢复为新副本',exact:true})).toHaveCount(0);
  const history=await request.get(`${API}/data/restores`,{headers:{Authorization:`Bearer ${target.token}`}});expect((await history.json()).totalElements).toBe(0);
});
test('offline file selection cannot preview or restore and does not expose the queue',async({page,request},info)=>{
  const source=await fixture(request),target=await account(request);await login(page,target.email);
  await page.route('**/api/**',r=>r.abort('failed'));await page.reload();await expect(page.getByText(/当前离线：仍可选择本机文件/)).toBeVisible();await select(page,source.file);await page.getByLabel(consent,{exact:true}).check();await expect(page.getByRole('button',{name:'只读预检，不恢复数据',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'下载私有 JSON 备份',exact:true})).toBeDisabled();
  if(info.project.name==='mobile-web')await page.setViewportSize({width:320,height:760});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('personal-data-offline.png'),fullPage:true});
});
test('data center is shared without switching the selected workspace',async({page,request},info)=>{
  const user=await account(request);await login(page,user.email);await page.getByLabel('工作模式',{exact:true}).selectOption('route');await expect(page.getByLabel('工作模式',{exact:true})).toHaveValue('route');
  await page.getByRole('button',{name:'更多',exact:true}).click();
  await page.getByRole('link',{name:'数据中心',exact:true}).click();await expect(page.getByRole('heading',{name:'个人数据中心',exact:true})).toBeVisible();await expect(page.getByLabel('工作模式',{exact:true})).toHaveValue('route');
});
