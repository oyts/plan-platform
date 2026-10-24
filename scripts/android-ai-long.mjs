// Independent Android WebView only; real Java transport, synthetic local provider.
// No Windows CDP, formal-app input, system permission or password-manager automation.
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {assertPortsFree,selectDevice} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),pkg='cn.zijun701.plan.mobile.regression',formal='cn.zijun701.plan.app',port=43174,cdp=43176;
const apk=join(root,'acceptance-results/ai-long/android-build/PlanCabin-0.1.21-debug.apk'),expected='d799321b7d98230b2c79da5e3fb86ae0ec79308286df715bb71430378cf9ea6a';
const out=join(root,'acceptance-results/ai-long/android',new Date().toISOString().replace(/[:.]/g,'-')),run=promisify(execFile),delay=ms=>new Promise(r=>setTimeout(r,ms));
let serial,browser,page,token,id,formalBefore,reverse=false,forward=false;
const report={status:'FAIL',startedAt:new Date().toISOString(),cases:[],apkSha256:expected,paidAiCalls:0,realEmails:0,formalAppTouched:false,limits:'Independent debug package and fake upstream; not formal upgrade, signing or production evidence. OS file-picker ingress and rotation remain separate checks.'};
async function adb(...args){return(await run(join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe'),[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:20000,maxBuffer:3e6})).stdout.trim();}
async function safe(){const dump=await adb('shell','dumpsys','window');const focus=dump.split(/\r?\n/).filter(s=>s.includes('mCurrentFocus=')&&!s.includes('mCurrentFocus=null'));if(focus.length!==1||!focus[0].includes(pkg+'/'))throw Error('OWNER_RETURN_REQUIRED: unlock and return to independent app; system UI not automated');}
async function api(path,data,method=data?'POST':'GET'){const r=await fetch(`http://127.0.0.1:${port}/api${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data?JSON.stringify(data):undefined,signal:AbortSignal.timeout(10000)});if(!r.ok)throw Error('Synthetic API status '+r.status);return r.json();}
async function hash(name){const p=(await adb('shell','pm','path',name)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!p)throw Error('Installed package missing');return(await adb('shell','sha256sum',p)).split(/\s+/)[0];}
async function attach(){await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await safe();const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('Independent PID unavailable');await adb('forward',`tcp:${cdp}`,`localabstract:webview_devtools_remote_${pid}`);forward=true;browser=await chromium.connectOverCDP(`http://127.0.0.1:${cdp}`,{noDefaults:true});page=browser.contexts().flatMap(c=>c.pages()).find(p=>/^https?:\/\/localhost/.test(p.url()));if(!page)throw Error('Independent WebView missing');expect(await page.evaluate(()=>window.Capacitor?.isNativePlatform())).toBe(true);}
async function tab(name){await safe();await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name}).click();}
async function open(){await safe();await page.evaluate(v=>location.hash='/ai?session='+v,id);await expect(page.getByRole('combobox',{name:'最近对话'})).toHaveValue(id);}
async function check(name,fn){const row={name,status:'FAIL'};report.cases.push(row);try{await fn();await safe();const path=`/sdcard/Download/plan-ai-long-${Date.now()}.png`;await adb('shell','screencap','-p',path);await adb('pull',path,join(out,`case-${report.cases.length}.png`));row.status='PASS';console.log('[PASS] '+name);}finally{await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));}}
try{
 await mkdir(out,{recursive:true});await assertPortsFree([cdp]);serial=selectDevice(await adb('devices'));expect(createHash('sha256').update(await readFile(apk)).digest('hex')).toBe(expected);expect(await hash(pkg)).toBe(expected);formalBefore=await hash(formal);
 if((await adb('reverse','--list')).includes(`tcp:${port}`))throw Error('Reverse already owned; no replacement');await adb('reverse',`tcp:${port}`,`tcp:${port}`);reverse=true;await attach();
 await page.evaluate(async url=>{await window.Capacitor.Plugins.PlanNative.execute({command:'desktop_save_server',args:{serverUrl:url}});},`http://127.0.0.1:${port}`);await page.reload();await safe();
 await expect.poll(async()=>await page.getByLabel('邮箱',{exact:true}).isVisible()||await page.getByRole('navigation',{name:'手机主导航'}).isVisible()).toBe(true);
 if(await page.getByRole('navigation',{name:'手机主导航'}).isVisible()){await page.getByRole('navigation',{name:'手机主导航'}).getByRole('button',{name:'更多',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:/ · 退出$/}).click();await safe();}
 const email=`android-long-${randomUUID()}@example.test`,password=`Synthetic-${randomUUID()}!`;const a=await api('/auth/register',{email,password,displayName:'长规划Android合成验收'});token=a.accessToken;await writeFile(join(out,'synthetic-login.json'),JSON.stringify({email,password}),{flag:'wx'});
 await page.getByLabel('邮箱',{exact:true}).fill(email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await safe();await expect(page.getByRole('heading',{name:'今日控制台',exact:true})).toBeVisible();id=(await api('/ai/sessions',{mode:'ROUTE',consent:true})).id;await open();
 await check('完整大纲、单批明确发送及未完成应用保护',async()=>{
  await page.getByLabel('你的目标或修改要求').fill('完整12项基础学习路线，每项30分钟，保留前置依赖。');await page.getByRole('button',{name:'先生成完整大纲',exact:true}).click();await tab(/^草稿/);await expect(page.getByLabel('规划名称')).toHaveValue('合成完整规划',{timeout:25000});await expect(page.getByRole('button',{name:'确认生成',exact:true})).toBeDisabled();
  const p=page.getByLabel('长规划分批细化');await p.getByLabel('我确认的大纲覆盖范围',{exact:true}).fill('完整基础路线12项，所有前置依赖已核对。');await p.getByRole('checkbox',{name:/已逐项核对完整大纲/}).check();await p.getByRole('button',{name:'确认大纲，开启分批（不调用 AI）',exact:true}).click();await p.getByRole('button',{name:'发送第1批给 AI',exact:true}).click();await expect(p).toContainText('已完成 1 / 2批',{timeout:25000});expect((await api('/ai/sessions/'+id)).longPlan.submissions).toBe(1);await delay(2000);expect((await api('/ai/sessions/'+id)).turns).toHaveLength(2);
 });
 await check('第二批完成且草稿可核对，协作返回只读差异和明确保存',async()=>{
  const p=page.getByLabel('长规划分批细化');await p.getByRole('button',{name:'发送第2批给 AI',exact:true}).click();await expect(p).toContainText('已完成 2 / 2批',{timeout:25000});const s=await api('/ai/sessions/'+id);
  // Owner approved this selected synthetic content and local destination; no external AI.
  const material=await api(`/ai/sessions/${id}/collaboration/export`,{version:s.version,instructions:'第一项添加具体练习，其他保留',consent:true});material.draft.items[0].note='Android合成协作：练习十分钟并检查。';const c=page.getByLabel('外部AI协作');await c.locator('summary').first().click();await c.getByLabel('外部返回协作JSON',{exact:true}).fill(JSON.stringify(material));await c.getByRole('button',{name:'只读预检协作材料',exact:true}).click();await expect(c.getByLabel('协作差异预览')).toContainText('Android合成协作');expect((await api('/ai/sessions/'+id)).version).toBe(s.version);await c.getByRole('checkbox',{name:/我已核对差异，只保存/}).check();await c.getByRole('button',{name:'确认保存返回草稿',exact:true}).click();await expect(page.getByLabel('任务说明',{exact:true}).first()).toHaveValue('Android合成协作：练习十分钟并检查。');expect((await api('/ai/sessions/'+id)).applied).toBeNull();
 });
 await check('本机草稿保存和冷重开保留批次、预算、协作内容，不恢复应用确认',async()=>{
  await page.getByRole('button',{name:'保存到本机',exact:true}).click();await safe();await browser.close();browser=undefined;await adb('forward','--remove',`tcp:${cdp}`);forward=false;await adb('shell','am','force-stop',pkg);await attach();await open();await tab(/^草稿/);await expect(page.getByLabel('长规划分批细化')).toContainText('已完成 2 / 2批');await expect(page.getByLabel('任务说明',{exact:true}).first()).toHaveValue('Android合成协作：练习十分钟并检查。');await expect(page.getByRole('button',{name:'确认生成',exact:true})).toBeDisabled();expect((await api('/ai/sessions/'+id)).longPlan.submissions).toBe(2);
 });report.status='PASS';
}catch(e){report.error=e.message;console.error('Stopped: '+e.message);process.exitCode=1;}
finally{if(browser)await browser.close().catch(()=>{});if(forward)await adb('forward','--remove',`tcp:${cdp}`).catch(()=>{});if(reverse)await adb('reverse','--remove',`tcp:${port}`).catch(()=>{});if(formalBefore){report.formalApkUnchanged=await hash(formal)===formalBefore;if(!report.formalApkUnchanged){report.status='FAIL';process.exitCode=1;}}report.finishedAt=new Date().toISOString();await mkdir(out,{recursive:true});await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);}
