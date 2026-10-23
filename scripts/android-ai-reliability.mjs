// Independent Android WebView only. Real Java transport to the loopback fake-provider fixture.
// No production credentials, provider calls, formal-app input or system-dialog automation.
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {assertPortsFree,selectDevice} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const pkg='cn.zijun701.plan.mobile.regression',formal='cn.zijun701.plan.app',port=43171,cdp=43176;
const apk=join(root,'.desktop-tools/ai-reliability-android/PlanCabin-0.1.20-debug.apk');
const expected='ff23b8d601c0ebd953944293fb8eb0044d6c3844b5737181c9a1fdb32071fdfa';
const out=join(root,'acceptance-results/ai-reliability/android',new Date().toISOString().replace(/[:.]/g,'-'));
const run=promisify(execFile),delay=ms=>new Promise(r=>setTimeout(r,ms));
let serial,browser,page,reverse=false,forward=false,token,sessionId,formalBefore;
const report={status:'FAIL',startedAt:new Date().toISOString(),cases:[],apkSha256:expected,paidAiCalls:0,realEmails:0,formalAppTouched:false,
  scope:'Independent debug APK; actual Java HTTP/session transport; local synthetic H2 and test-classpath fake upstream. Not production, signing or formal upgrade acceptance.'};
async function adb(...args){return(await run(join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe'),[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:20000,maxBuffer:3e6})).stdout.trim();}
async function wait(fn,label,ms=30000){const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await delay(400);}throw Error('Timed out: '+label);}
async function safe(){
 const ready=async()=>{const dump=await adb('shell','dumpsys','window');const focus=dump.split(/\r?\n/).filter(s=>s.includes('mCurrentFocus=')&&!s.includes('mCurrentFocus=null'));return focus.length===1&&focus[0].includes(pkg+'/');};
 if(!await ready()){console.log('WAITING_USER_RETURN: 请本人处理系统提示并返回独立测试包，自动化不会点击安全或密码窗口。');await wait(ready,'owner return to independent app',180000);}
}
async function api(path,data,expectedStatus=200,method=data?'POST':'GET'){
 const response=await fetch(`http://127.0.0.1:${port}/api${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data?JSON.stringify(data):undefined,signal:AbortSignal.timeout(10000)});
 if(response.status!==expectedStatus)throw Error(`Synthetic ${path}: HTTP ${response.status}, expected ${expectedStatus}`);
 return expectedStatus===204?null:response.json();
}
async function installedHash(name){const path=(await adb('shell','pm','path',name)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!path)throw Error('Expected installed package missing');return(await adb('shell','sha256sum',path)).split(/\s+/)[0];}
async function attach(){
 await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await safe();
 const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('Independent PID unavailable');
 await adb('forward',`tcp:${cdp}`,`localabstract:webview_devtools_remote_${pid}`);forward=true;
 browser=await chromium.connectOverCDP(`http://127.0.0.1:${cdp}`,{noDefaults:true});
 page=browser.contexts().flatMap(c=>c.pages()).find(p=>/^https?:\/\/localhost/.test(p.url()));if(!page)throw Error('Independent WebView missing');
 expect(await page.evaluate(()=>window.Capacitor?.isNativePlatform())).toBe(true);
}
async function tab(name){await safe();await page.getByRole('navigation',{name:'AI 工作区'}).getByRole('button',{name}).click();}
async function openSession(){await safe();await page.evaluate(id=>location.hash='/ai?session='+id,sessionId);await expect(page.getByLabel('你的目标或修改要求')).toBeVisible();}
async function check(name,fn){const row={name,status:'FAIL'};report.cases.push(row);const started=Date.now();try{await fn();await safe();
  // Some OEM WebViews hang on CDP capture while the IME is open. Capture the real screen instead.
  const screen=`/sdcard/Download/plan-ai-reliability-${report.startedAt.replace(/[:.]/g,'-')}-${report.cases.length}.png`;
  await adb('shell','screencap','-p',screen);await adb('pull',screen,join(out,`case-${report.cases.length}.png`));
  row.status='PASS';console.log('[PASS] '+name);
 }finally{row.durationMs=Date.now()-started;await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));}}
try{
 await mkdir(out,{recursive:true});await assertPortsFree([cdp]);serial=selectDevice(await adb('devices'));
 expect(createHash('sha256').update(await readFile(apk)).digest('hex')).toBe(expected);expect(await installedHash(pkg)).toBe(expected);formalBefore=await installedHash(formal);
 const reverses=await adb('reverse','--list');if(reverses.includes(`tcp:${port}`))throw Error('Required reverse is already owned; no replacement performed');
 await adb('reverse',`tcp:${port}`,`tcp:${port}`);reverse=true;await attach();
 await page.evaluate(async serverUrl=>{await window.Capacitor.Plugins.PlanNative.execute({command:'desktop_save_server',args:{serverUrl}});},`http://127.0.0.1:${port}`);await page.reload();await safe();
 // Never reuse a real account. This isolated package is explicitly authorized for synthetic login.
 await wait(async()=>await page.getByLabel('邮箱',{exact:true}).isVisible()||await page.getByRole('navigation',{name:'手机主导航'}).isVisible(),'synthetic bootstrap');
 if(await page.getByRole('navigation',{name:'手机主导航'}).isVisible()){
  await page.getByRole('navigation',{name:'手机主导航'}).getByRole('button',{name:'更多',exact:true}).click();
  await page.getByRole('dialog').getByRole('button',{name:/ · 退出$/}).click();await safe();
 }
 const email=`android-reliability-${randomUUID()}@example.test`,password=`Synthetic-${randomUUID()}!`;
 const auth=await api('/auth/register',{email,password,displayName:'AI可靠性Android合成验收'},201);token=auth.accessToken;
 await writeFile(join(out,'synthetic-login.json'),JSON.stringify({email,password}),{flag:'wx'});
 await expect(page.getByLabel('邮箱',{exact:true})).toBeVisible();await page.getByLabel('邮箱',{exact:true}).fill(email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await safe();await expect(page.getByRole('heading',{name:'今日控制台',exact:true})).toBeVisible();
 sessionId=(await api('/ai/sessions',{mode:'PLAN',consent:true})).id;await openSession();
 await check('Android只讨论不造草稿；早期需求保存及编辑未保存防护',async()=>{
  await safe();await page.getByLabel('你的目标或修改要求').fill('零基础学Java，两个月，每天两小时，星期日休息');await page.getByRole('button',{name:'发送并讨论',exact:true}).click();
  await expect(page.locator('.ai-assistant-message')).toContainText('已记住你的目标',{timeout:20000});
  expect((await api('/ai/sessions/'+sessionId)).draft).toBeNull();const used=(await api('/ai/quota')).used;
  await page.getByText('需求笔记（发送过的要求持续保留）',{exact:false}).click();const note=page.getByLabel('需求笔记',{exact:true});await expect(note).toHaveValue(/星期日休息/);
  await note.fill((await note.inputValue())+'\n补充：每天最多一小时。');await expect(page.getByRole('button',{name:'生成规划草稿',exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'保存需求笔记',exact:true}).click();await expect(page.getByText('需求笔记已保存；下次发送才会交给AI，没有扣次数。')).toBeVisible();expect((await api('/ai/quota')).used).toBe(used);
 });
 await check('Android一次格式修复、两次诊断、只计一次并明确应用门槛',async()=>{
  await safe();await page.getByLabel('你的目标或修改要求').fill('请根据已确认的要求生成草稿');await page.getByRole('button',{name:'生成规划草稿',exact:true}).click();
  await tab(/^草稿/);await expect(page.getByLabel('规划名称')).toHaveValue('合成Java规划',{timeout:25000});await expect(page.getByRole('button',{name:'确认生成',exact:true})).toBeDisabled();
  const current=await api('/ai/sessions/'+sessionId);expect(current.turns.at(-1).diagnostic.attempts).toBe(2);expect(current.turns.at(-1).diagnostic.details.map(x=>x.reason??x.state)).toEqual(['DRAFT_INVALID','ACCEPTED']);expect(current.requirementsText).toContain('星期日休息');
  const quota=await api('/ai/quota');expect(quota.used).toBe(2);expect(quota.pending).toBe(0);expect(await api('/plans')).toHaveLength(0);
  await tab('对话');await page.getByText('请求诊断（不含对话正文）',{exact:true}).last().click();await expect(page.locator('.ai-assistant-message').last()).toContainText('DRAFT_INVALID');await expect(page.locator('.ai-assistant-message').last()).toContainText('ACCEPTED');
 });
 await check('Android手改保存与二次修订，均不自动创建真实计划',async()=>{
  await tab(/^草稿/);await page.getByLabel('任务标题',{exact:true}).fill('合成第一步－Android手改');await expect(page.getByRole('button',{name:'确认生成',exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'保存草稿修改',exact:true}).click();await expect(page.getByText('草稿已保存，还没有生成真实计划。')).toBeVisible();expect((await api('/ai/quota')).used).toBe(2);
  await tab('对话');await page.getByLabel('你的目标或修改要求').fill('把标题改回合成第一步，给我预览即可');await page.getByRole('button',{name:'按要求修改草稿',exact:true}).click();await tab(/^草稿/);
  await expect(page.getByLabel('任务标题',{exact:true})).toHaveValue('合成第一步',{timeout:25000});expect((await api('/ai/quota')).used).toBe(3);expect(await api('/plans')).toHaveLength(0);
 });
 await check('Android关闭重开保留会话、需求和修订草稿，不恢复确认勾选',async()=>{
  await safe();await browser.close();browser=undefined;await adb('forward','--remove',`tcp:${cdp}`);forward=false;await adb('shell','am','force-stop',pkg);await attach();await openSession();
  await page.getByText('需求笔记（发送过的要求持续保留）',{exact:false}).click();await expect(page.getByLabel('需求笔记',{exact:true})).toHaveValue(/每天最多一小时/);await tab(/^草稿/);
  await expect(page.getByLabel('任务标题',{exact:true})).toHaveValue('合成第一步');await expect(page.getByRole('button',{name:'确认生成',exact:true})).toBeDisabled();expect((await api('/ai/quota')).used).toBe(3);
 });
 await check('Android确定两次无效后保留可读文字和旧草稿，退回本次额度',async()=>{
  await tab('对话');await page.getByLabel('你的目标或修改要求').fill('[invalid-always] 合成两次无效输出');await page.getByRole('button',{name:'按要求修改草稿',exact:true}).click();
  await expect(page.locator('.ai-assistant-message').last()).toContainText('本次额度已退回',{timeout:25000});await expect(page.locator('.ai-assistant-message').last()).toContainText('文字仅供参考');await expect(page.locator('.ai-assistant-message').last()).toContainText('已尝试 2 次');
  const current=await api('/ai/sessions/'+sessionId);expect(current.draft.items[0].title).toBe('合成第一步');expect(current.turns.at(-1).diagnostic.attempts).toBe(2);expect((await api('/ai/quota')).used).toBe(3);expect((await api('/ai/quota')).pending).toBe(0);expect(await api('/plans')).toHaveLength(0);
 });
 report.status='PASS';
}catch(error){report.error=error.message;console.error('Stopped: '+error.message);process.exitCode=1;}
finally{
 if(browser)await browser.close().catch(()=>{});if(forward)await adb('forward','--remove',`tcp:${cdp}`).catch(()=>{});if(reverse)await adb('reverse','--remove',`tcp:${port}`).catch(()=>{});
 if(formalBefore){try{report.formalApkUnchanged=await installedHash(formal)===formalBefore;if(!report.formalApkUnchanged)throw Error('Formal APK hash changed');}catch{report.status='FAIL';report.cleanupError='Formal APK comparison not confirmed';process.exitCode=1;}}
 report.finishedAt=new Date().toISOString();await mkdir(out,{recursive:true});await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);
}
