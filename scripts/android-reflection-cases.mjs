// Only the independent synthetic account from the Android runner is accepted.
import {expect} from '../web/node_modules/@playwright/test/index.mjs';
export async function acceptReflection({page,api,scenario,guard,proxy,user,followup=false}){
 const date=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());
 const note={wins:'安卓合成云端收获',blockers:'',nextFocus:'小步前进',energy:'STEADY',burden:'MANAGEABLE',reviewed:false,version:null};
 await api('/todos',{title:'安卓合成复盘事项',dueDate:date},user.accessToken,'POST',201);
 await api(`/reflection/days/${date}`,note,user.accessToken,'PUT');
 let jobs=0;page.on('request',r=>{if(r.method()==='POST'&&/\/ai\/sessions\/[^/]+\/jobs/.test(r.url()))jobs++;});
 if(!followup){await scenario('三模式事实与真实Java读取复盘',async()=>{
  await guard();await page.evaluate(date=>location.hash='/reflection?date='+date,date);
  await expect(page.getByLabel('收获',{exact:true})).toHaveValue(note.wins);
  await expect(page.getByText('不设每日配额，不计作未完成或逾期')).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 });
 await scenario('安卓本机草稿离线重开，不重放AI或云端保存',async()=>{
  await guard();await page.getByLabel('收获',{exact:true}).fill('安卓原生离线草稿');await page.getByRole('button',{name:'保存本机草稿',exact:true}).click();await expect(page.getByRole('status')).toContainText('已存本机');
  proxy.setOnline(false);await page.reload();await expect(page.locator('.site-header')).toBeVisible();await guard();await page.evaluate(date=>location.hash='/reflection?date='+date,date);
  await expect(page.getByLabel('收获',{exact:true})).toHaveValue('安卓原生离线草稿');await expect(page.getByRole('button',{name:'保存云端复盘',exact:true})).toBeDisabled();
  proxy.setOnline(true);expect((await api(`/reflection/days/${date}`,null,user.accessToken)).wins).toBe(note.wins);expect(jobs).toBe(0);
 });
 await scenario('安卓复盘版本变化保留输入，人工比较后第二次保存',async()=>{
  const cloud=await api(`/reflection/days/${date}`,null,user.accessToken);await api(`/reflection/days/${date}`,{...cloud,wins:'合成另一端的新版本'},user.accessToken,'PUT');
  // Reconnect by the app's normal action, preserving the local draft.
  await guard();const reconnect=page.getByRole('button',{name:'恢复连接',exact:true});if(await reconnect.count())await reconnect.click();else await page.reload();
  await expect(page.locator('.site-header')).toBeVisible();await page.evaluate(date=>location.hash='/reflection?date='+date,date);
  await expect(page.getByLabel('收获',{exact:true})).toHaveValue('安卓原生离线草稿');
  await expect(page.getByText('云端收获：合成另一端的新版本')).toBeVisible();
  await expect(page.getByRole('button',{name:'保存云端复盘',exact:true})).toBeDisabled();
  page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'已比较，采用最新版本号',exact:true}).click();
  expect((await api(`/reflection/days/${date}`,null,user.accessToken)).wins).toBe('合成另一端的新版本');
  await page.getByRole('button',{name:'保存云端复盘',exact:true}).click();await expect(page.getByRole('status')).toContainText('复盘已保存');
  expect((await api(`/reflection/days/${date}`,null,user.accessToken)).wins).toBe('安卓原生离线草稿');
 });}
 await scenario('安卓AI预览同意门槛与周趋势默认不加量',async()=>{
  await guard();if(followup){await page.evaluate(date=>location.hash='/reflection?date='+date,date);await expect(page.getByLabel('收获',{exact:true})).toHaveValue(note.wins);}
  await page.getByRole('button',{name:'预览将发送的内容',exact:true}).click();await expect(page.locator('.analysis-preview pre')).toContainText(followup?note.wins:'安卓原生离线草稿');
  await expect(page.getByRole('button',{name:'建立分析会话，尚不调用AI',exact:true})).toBeDisabled();await expect(page.getByRole('heading',{name:'从建议到调整',exact:true})).toHaveCount(0);expect(jobs).toBe(0);
  await guard();await page.getByRole('combobox',{name:/^复盘范围/}).selectOption('WEEK');await page.getByRole('button',{name:'查看所选范围',exact:true}).click();
  await expect(page.getByLabel('我愿意讨论少量增加投入（不代表同意修改计划）')).not.toBeChecked();await expect(page.getByRole('button',{name:'我想讨论节奏',exact:true})).toHaveCount(0);
  await page.getByText('复盘引导与时间设置',{exact:true}).click();await expect(page.getByLabel('日复盘定时提醒',{exact:true})).not.toBeChecked();await expect(page.getByLabel('周复盘定时提醒',{exact:true})).not.toBeChecked();
  expect(jobs).toBe(0);await page.evaluate(()=>location.hash='/reminders');await expect(page.getByRole('heading',{name:'系统通知与事项提醒',exact:true})).toBeVisible();
 });
}
