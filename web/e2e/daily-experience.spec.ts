import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const API='http://127.0.0.1:43131/api', password='DailyExperience123!';
function localDay(){return new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());}
async function account(request:APIRequestContext){const email=`daily-${randomUUID()}@example.test`;const r=await request.post(`${API}/auth/register`,{data:{email,password,displayName:'日常体验验收'}});expect(r.status()).toBe(201);return{email,token:(await r.json()).accessToken as string};}
async function login(page:Page,email:string){await page.goto('/login');await page.getByLabel('邮箱',{exact:true}).fill(email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page.locator('.site-header')).toBeVisible();}
async function createTodo(request:APIRequestContext,token:string,title:string,date:string){const r=await request.post(`${API}/todos`,{headers:{Authorization:`Bearer ${token}`},data:{title,dueDate:date}});expect(r.status()).toBe(201);return r.json();}
test('overview filters modes and date buckets and opens the correct dated business page',async({page,request},info)=>{
  const user=await account(request), headers={Authorization:`Bearer ${user.token}`};
  await createTodo(request,user.token,'今天寄出资料','2026-10-18');await createTodo(request,user.token,'补交昨天资料','2026-10-17');
  const plan=await request.post(`${API}/plans`,{headers,data:{name:'阅读周期',startDate:'2026-10-01',endDate:'2026-11-30',restDays:[7]}});expect(plan.status()).toBe(201);const p=await plan.json();
  await request.post(`${API}/plans/${p.id}/tasks`,{headers,data:{date:'2026-10-20',title:'预习新章节',plannedMinutes:20,category:'STUDY',acceptance:[],required:true,sortOrder:0}});
  await request.post(`${API}/mainlines`,{headers,data:{name:'长期吉他路线',description:'无截止日期',color:'#002FA7',executionMode:'FLEXIBLE'}});
  await login(page,user.email);await page.goto('/overview');await page.getByLabel('查看日期',{exact:true}).fill('2026-10-18');
  await expect(page.getByRole('heading',{name:'今天寄出资料',exact:true})).toBeVisible();
  await page.getByRole('button',{name:/已逾期/}).click();await expect(page.getByRole('heading',{name:'补交昨天资料',exact:true})).toBeVisible();
  await page.getByRole('link',{name:'查看并处理',exact:true}).click();await expect(page).toHaveURL(/\/todos\?date=2026-10-17/);await expect(page.getByLabel('安排日期',{exact:true})).toHaveValue('2026-10-17');
  await page.goto('/overview');await page.getByLabel('查看日期',{exact:true}).fill('2026-10-18');await page.getByRole('button',{name:/即将到期/}).click();await page.getByRole('combobox',{name:'模式',exact:true}).selectOption('PLAN');
  await expect(page.getByRole('heading',{name:'预习新章节',exact:true})).toBeVisible();await page.getByRole('link',{name:'查看并处理',exact:true}).click();await expect(page).toHaveURL(/\/schedule\?plan=.*date=2026-10-20/);await expect(page.getByRole('heading',{name:/预习新章节/}).last()).toBeVisible();
  await page.goto('/overview');await expect(page.getByRole('heading',{name:'长期吉他路线',exact:true})).toBeVisible();await expect(page.getByText('没有截止日期，不标记逾期。',{exact:false})).toBeVisible();
  await page.screenshot({path:info.outputPath('daily-overview.png'),fullPage:true});
});
test('reminders require opting in and dismissal persists without changing server settings',async({page,request},info)=>{
  const user=await account(request);await createTodo(request,user.token,'提醒测试事项',localDay());await login(page,user.email);await page.goto('/overview');
  const enabled=page.getByLabel('启用站内提醒',{exact:true});await expect(enabled).not.toBeChecked();await expect(page.getByLabel('站内事项提醒')).toHaveCount(0);
  await enabled.check();await page.getByRole('button',{name:'保存提醒设置',exact:true}).click();await expect(page.getByText('站内提醒设置已保存，其他设备联网读取后生效。')).toBeVisible();
  await expect(page.getByLabel('站内事项提醒')).toContainText('1 项事项值得留意');await page.getByRole('button',{name:'今天不再提示',exact:true}).click();await page.reload();await expect(enabled).toBeChecked();await expect(page.getByLabel('站内事项提醒')).toHaveCount(0);
  await enabled.uncheck();await page.getByRole('button',{name:'保存提醒设置',exact:true}).click();await expect(page.getByText('站内提醒设置已保存，其他设备联网读取后生效。')).toBeVisible();
  const settings=await request.get(`${API}/daily/reminders`,{headers:{Authorization:`Bearer ${user.token}`}});expect((await settings.json()).enabled).toBe(false);
  await page.setViewportSize({width:320,height:740});await page.locator('#reminder-settings').scrollIntoViewIfNeeded();await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('reminder-settings-320.png'),fullPage:true});
});
test('weekly notes survive reopen, prevent lost edits, and keep input on a real version conflict',async({page,request},info)=>{
  const user=await account(request);await login(page,user.email);await page.goto('/weekly-review');await page.getByLabel('选择一周中的任意日期').fill('2026-10-18');await page.getByRole('button',{name:'查看这一周',exact:true}).click();
  await expect(page.getByRole('heading',{name:'2026-10-12 至 2026-10-18'})).toBeVisible();await page.getByLabel('本周收获',{exact:true}).fill('完成了阅读和吉他第一步');await page.getByLabel('下周重点',{exact:true}).fill('继续练习和弦');
  await page.getByRole('button',{name:'保存本周复盘',exact:true}).click();await expect(page.getByText('本周复盘已保存，其他设备联网后可读取。')).toBeVisible();await page.reload();await expect(page.getByLabel('下周重点',{exact:true})).toHaveValue('继续练习和弦');
  await page.getByLabel('下周重点',{exact:true}).fill('本机保留的草稿');page.once('dialog',dialog=>dialog.dismiss());await page.getByRole('link',{name:'今日与提醒',exact:true}).last().click();await expect(page).toHaveURL(/\/weekly-review$/);
  const headers={Authorization:`Bearer ${user.token}`}, current=await (await request.get(`${API}/weekly/reviews/2026-10-12`,{headers})).json();const changed=await request.put(`${API}/weekly/reviews/2026-10-12`,{headers,data:{wins:current.wins,blockers:'',nextFocus:'另一端保存的重点',version:current.version}});expect(changed.status()).toBe(200);
  await page.getByRole('button',{name:'保存本周复盘',exact:true}).click();await expect(page.getByRole('alert')).toContainText('其他设备');await expect(page.getByLabel('下周重点',{exact:true})).toHaveValue('本机保留的草稿');
  page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'读取云端版本',exact:true}).click();await expect(page.getByLabel('下周重点',{exact:true})).toHaveValue('另一端保存的重点');
  await page.setViewportSize({width:320,height:740});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('weekly-review-320.png'),fullPage:true});
  await page.getByRole('region',{name:'本周概况'}).scrollIntoViewIfNeeded();await page.screenshot({path:info.outputPath('weekly-review-320-viewport.png')});
});
test('new pages can read previously loaded snapshots offline without enqueueing writes',async({page,request})=>{
  const user=await account(request);await createTodo(request,user.token,'缓存中的事项',localDay());await login(page,user.email);await page.goto('/weekly-review');await expect(page.getByLabel('本周收获',{exact:true})).toBeVisible();await page.goto('/overview');await expect(page.getByRole('heading',{name:'缓存中的事项',exact:true})).toBeVisible();
  await page.route('**/api/**',route=>route.abort('failed'));await page.reload();await expect(page.getByRole('heading',{name:'缓存中的事项',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'保存提醒设置',exact:true})).toBeDisabled();
  await page.goto('/weekly-review');await expect(page.getByLabel('本周收获',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'保存本周复盘',exact:true})).toBeDisabled();
});
