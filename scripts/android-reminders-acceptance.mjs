// Independent debug Android only. Real Java bridge and isolated H2; no production writes.
import {spawn,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {randomUUID,createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {selectDevice,assertPortsFree,createFaultProxy} from './native-regression-support.mjs';
import {acceptReflection} from './android-reflection-cases.mjs';
const followup=process.argv.length===3&&process.argv[2]==='--review-followup';
const boundaries=process.argv.length===3&&process.argv[2]==='--cold-start-followup';
const remindersOnly=boundaries||process.argv.length===3&&process.argv[2]==='--reminders-followup';
const combined=process.argv.length===3&&['--combined','--review-followup','--reminders-followup','--cold-start-followup'].includes(process.argv[2]);
if(process.argv.length>2&&!combined)throw Error('Only --combined, --review-followup, --reminders-followup or --cold-start-followup is supported.');
const root=fileURLToPath(new URL('../',import.meta.url)),pkg='cn.zijun701.plan.mobile.regression';
const run=join(root,combined?'acceptance-results/reflection/android':'acceptance-results/reminders/android',new Date().toISOString().replace(/[:.]/g,'-'));
const adbPath=join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe'),exec=promisify(execFile);
const ports={api:43150,proxy:43151,cdp:43152},password='SyntheticReminder123!';
const report={status:'FAIL',cases:[],scope:'Android independent debug / real Java bridge / isolated H2; no production account or paid API'};
let serial,child,proxy,browser,page,reminderTask;
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function adb(...args){return(await exec(adbPath,[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:15000,maxBuffer:2*1024*1024})).stdout.trim();}
async function wait(fn,label,ms=60000){const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await delay(500);}throw Error('Timed out: '+label);}
async function foreground(){const out=await adb('shell','dumpsys','activity','activities');return out.split(/\r?\n/).some(l=>/mResumedActivity|topResumedActivity/.test(l)&&l.includes(pkg+'/'));}
async function guard(){if(!await foreground())throw Error('Independent app not foreground; stopped UI input.');}
async function bridge(command,args={}){return page.evaluate(async({command,args})=>(await window.Capacitor.Plugins.PlanNative.execute({command,args})).value,{command,args});}
async function api(path,body,token,method=body?'POST':'GET',expected=200){const r=await fetch(`http://127.0.0.1:${ports.api}/api${path}`,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});expect(r.status,path).toBe(expected);return expected===204?null:r.json();}
async function scenario(name,fn){const row={name,status:'FAIL'};report.cases.push(row);try{await fn();row.status='PASS';console.log('[PASS] '+name);}finally{if(page&&await foreground().catch(()=>false)&&!await page.locator('input[type=password]').count())await page.screenshot({path:join(run,`case-${report.cases.length}.png`),timeout:5000}).catch(()=>{});await writeFile(join(run,'report.json'),JSON.stringify(report,null,2));}}
async function attach(){const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('No unique independent process');await adb('forward',`tcp:${ports.cdp}`,`localabstract:webview_devtools_remote_${pid}`);browser=await chromium.connectOverCDP(`http://127.0.0.1:${ports.cdp}`,{noDefaults:true});page=browser.contexts().flatMap(c=>c.pages()).find(p=>/^https?:\/\/localhost/.test(p.url()));if(!page)throw Error('No independent WebView');}
try{
 await mkdir(run,{recursive:true});await assertPortsFree(Object.values(ports));serial=selectDevice(await adb('devices'));
 const location=(await adb('shell','pm','path',pkg)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!location)throw Error('Test APK not installed');
 const apk=await readFile(join(root,boundaries?'.desktop-tools/native-regression/PlanCabin-0.1.19-debug.apk':combined?'acceptance-results/reflection/android-final/PlanCabin-0.1.17-debug.apk':'.desktop-tools/reminder-android-candidate/PlanCabin-0.1.17-debug.apk'));
 if(!(await adb('shell','sha256sum',location)).startsWith(createHash('sha256').update(apk).digest('hex')))throw Error('Installed test APK differs');
 const apiJar=join(root,boundaries?'backend/target-stable/plan-platform-api.jar':remindersOnly?'backend/target-reminder-timezone/plan-platform-api.jar':combined?'backend/target-reflection/plan-platform-api.jar':'backend/target-reminders/plan-platform-api.jar');
 report.apiSha256=createHash('sha256').update(await readFile(apiJar)).digest('hex');report.apkSha256=createHash('sha256').update(apk).digest('hex');
 if(boundaries){
  const released=JSON.parse(await readFile(join(root,'docs/release-0119-resume.json'),'utf8'));
  const independent=JSON.parse(await readFile(join(root,'.desktop-tools/native-regression/build.json'),'utf8'));
  if(report.apiSha256!==released.apiSha256||report.apkSha256!==independent.apkSha256.toLowerCase()||independent.androidPackage!==pkg)throw Error('Exact released API / independent0.1.19 APK baseline required');
  report.scope='Android independent0.1.19 released reminder cold-process followup only; NOT new legacy-recycling candidate acceptance';
 }
 const args=['-Duser.timezone=Asia/Shanghai','-jar',apiJar,'--spring.profiles.active=acceptance','--server.address=127.0.0.1',`--server.port=${ports.api}`,`--spring.datasource.url=jdbc:h2:mem:reminder_${randomUUID().replaceAll('-','')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`,'--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=','--app.security.registration-enabled=true','--app.security.invitation-enabled=false','--app.security.email-verification-enabled=false','--app.security.refresh-cookie-secure=false','--app.jwt.secret=isolated-reminder-tests-not-production-at-least-32-bytes','--app.ai.enabled=false','--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration','--app.operations.mail-alerts-enabled=false','--app.operations.scheduler-enabled=false'];
 const log=createWriteStream(join(run,'api.log'));child=spawn('E:/biancheng/JDK21/bin/java.exe',args,{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log);child.stderr.pipe(log);
 await wait(async()=>{if(child.exitCode!==null)throw Error('Isolated API stopped');try{return(await fetch(`http://127.0.0.1:${ports.api}/actuator/health`)).ok;}catch{return false;}},'API health',90000);
 proxy=await createFaultProxy(ports.proxy,ports.api);await adb('reverse',`tcp:${ports.proxy}`,`tcp:${ports.proxy}`);
 await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await wait(foreground,'independent app foreground');await attach();
 await guard();await bridge('reminder_clear');
 const user=await api('/auth/register',{email:`reminder-${randomUUID()}@example.test`,password,displayName:'合成提醒验收'},null,'POST',201);
 await scenario('真实 Java 连接与默认关闭',async()=>{
  await guard();await bridge('desktop_save_server',{serverUrl:`http://127.0.0.1:${ports.proxy}`});await page.reload();
  await expect(page.getByLabel('邮箱',{exact:true})).toBeVisible();await guard();await page.getByLabel('邮箱',{exact:true}).fill(user.user.email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(page.getByRole('heading',{name:'今日控制台',exact:true})).toBeVisible();await page.evaluate(()=>location.hash='/reminders');await expect(page.getByRole('heading',{name:'系统通知与事项提醒',exact:true})).toBeVisible();
  expect((await bridge('reminder_status')).enabled).toBe(false);expect(await page.getByLabel('提前提醒',{exact:true}).isChecked()).toBe(false);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 });
 if(combined&&!remindersOnly)await acceptReflection({page,api,scenario,guard,proxy,user,bridge,followup});
 await scenario('本人选择通知权限和设备开启',async()=>{
  await guard();await page.getByRole('button',{name:'开启本设备提醒',exact:true}).click();console.log('WAITING_USER_NOTIFICATION_PERMISSION: 请自行选择系统通知权限，然后返回独立测试包。');
  await wait(async()=>{try{return(await bridge('reminder_status')).enabled;}catch{return false;}},'user permission and native enable',180000);
  await wait(foreground,'return from permission');expect((await bridge('reminder_status')).permission).toBe(true);
  if(!(await bridge('reminder_status')).exact){await guard();await page.getByRole('button',{name:'申请精确提醒',exact:true}).click();console.log('WAITING_USER_EXACT_PERMISSION: 请本人选择精确提醒权限并返回测试包。');await wait(async()=>await foreground()&&(await bridge('reminder_status')).exact,'user exact alarm choice',180000);}
  report.exactAlarmAuthorized=(await bridge('reminder_status')).exact;
 });
 await scenario(boundaries?'完整测试进程退出后的真实系统冷启动与通知':'后台系统唤醒前的新鲜状态核对',async()=>{
  const now=new Date(Date.now()+120000),date=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(now),hm=new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit'}).format(now);
  const p=await api('/plans',{name:'合成提醒计划',startDate:date,endDate:date,restDays:[]},user.accessToken,'POST',201);
  reminderTask=await api(`/plans/${p.id}/tasks`,{date,startTime:hm,endTime:null,plannedMinutes:15,title:'合成提醒任务',category:'STUDY',required:true,sortOrder:0,acceptance:[]},user.accessToken,'POST',201);
  const prefs=await api('/reminders/preferences',null,user.accessToken);await api('/reminders/preferences',{...prefs,advanceEnabled:true,advanceMinutes:1},user.accessToken,'PUT');
  report.syntheticSchedule={date,startTime:hm,configuredAt:Date.now(),task:reminderTask,preferences:await api('/reminders/preferences',null,user.accessToken)};
  // Re-enable schedules a new near-term live check. No system permission is changed.
  await guard();await page.getByRole('button',{name:'关闭本设备提醒',exact:true}).click();await expect(page.getByRole('button',{name:'开启本设备提醒',exact:true})).toBeVisible();await page.getByRole('button',{name:'开启本设备提醒',exact:true}).click();await expect(page.getByRole('button',{name:'关闭本设备提醒',exact:true})).toBeVisible();
  await adb('shell','input','keyevent','3');console.log('BACKGROUND_WAIT: 等待原生系统唤醒；不使用 JS 定时器投递。');
  let coldAfter=0;
  if(boundaries){
   const processId=await adb('shell','pidof',pkg);if(!/^\d+$/.test(processId))throw Error('One exact independent process required');
   report.processBeforeCold=1;
   await browser.close();browser=null;page=null;
   // Simulate abrupt death of this owned debug process only. Some vendors do
   // not terminate cached apps with am kill. Never force-stop the package:
   // force-stop deliberately disables alarms and is a different lifecycle.
   await adb('shell','run-as',pkg,'kill','-9',processId);
   report.processExitMethod='owned-debug-process-death-not-force-stop-or-normal-close';
   await wait(async()=>{
    try{return !await adb('shell','pidof',pkg);}
    catch(error){if(error.code===1&&!String(error.stdout||'').trim()&&!String(error.stderr||'').trim())return true;throw error;}
   },'independent process actually exited',30000);
   coldAfter=Date.now();report.processExitObserved=true;
  }
  // Only this synthetic account's device last-seen is read; no other app's notifications are inspected.
  await wait(async()=>{const d=await api('/reminders/devices',null,user.accessToken);return d.some(v=>v.enabled&&v.lastSeenAt&&(!coldAfter||new Date(v.lastSeenAt).getTime()>coldAfter));},'native background poll',180000);
  report.backgroundPollObserved=true;
  // Do not evaluate JS in a frozen background WebView. OS/native work must stand on its own.
  const alertAt=new Date(`${date}T${hm}:00+08:00`).getTime()-60000;
  while(Date.now()<alertAt+15000)await delay(1000);
  await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await wait(foreground,'return after native alarm');
  if(boundaries)await attach();
  report.nativeDiagnostics=await bridge('reminder_test_diagnostics');report.backgroundActiveCount=(await bridge('reminder_test_active')).length;
  if(!report.backgroundActiveCount){
   await bridge('reminder_test_tick');report.foregroundDiagnostics=await bridge('reminder_test_diagnostics');report.foregroundActiveCount=(await bridge('reminder_test_active')).length;
   // A separate synthetic device can inspect eligibility without consuming the tested device's claim.
   const probe=await api('/reminders/devices',{platform:'ANDROID',label:'合成只读条件诊断'},user.accessToken);
   try{const response=await fetch(`http://127.0.0.1:${ports.api}/api/reminders/poll`,{method:'POST',headers:{'X-Reminder-Key':probe.token},signal:AbortSignal.timeout(15000)});expect(response.status).toBe(200);const data=await response.json();report.eligibilityProbe={serverTime:data.serverTime,nextWakeAt:data.nextWakeAt,notices:data.notices.length,activeKeys:data.activeKeys.length};}
   finally{await api(`/reminders/devices/${probe.id}`,null,user.accessToken,'DELETE',204);}
   report.syntheticSchedule.currentTasks=await api(`/plans/${p.id}/tasks`,null,user.accessToken);report.syntheticSchedule.currentPreferences=await api('/reminders/preferences',null,user.accessToken);
  }
  expect(report.backgroundActiveCount,'Actual background notification, not foreground fallback').toBeGreaterThan(0);
  report.osActiveNotificationObserved=true;
 });
 await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await wait(foreground,'return to regression app');await guard();
 await scenario('合成任务完成后原生实时核对撤回通知',async()=>{
  await guard();expect((await bridge('reminder_test_active')).length).toBeGreaterThan(0);
  await api(`/tasks/${reminderTask.id}/complete`,{note:'',acceptanceChecks:[],version:reminderTask.progress.version},user.accessToken,'POST');
  // Debug entry executes the actual native live-check implementation; this is not an OS wake assertion.
  await bridge('reminder_test_tick');report.completionDiagnostics=await bridge('reminder_test_diagnostics');
  expect(report.completionDiagnostics.http).toBe(200);expect((await bridge('reminder_test_active')).length).toBe(0);
 });
 await scenario('断网关闭与重开保持关闭',async()=>{proxy.setOnline(false);await bridge('reminder_clear');expect((await bridge('reminder_status')).enabled).toBe(false);proxy.setOnline(true);await page.reload();expect((await bridge('reminder_status')).enabled).toBe(false);});
 report.status='PASS';
}catch(e){report.error=String(e.message).replaceAll(password,'[redacted]');console.error(report.error);process.exitCode=1;}
finally{
 if(page&&await foreground().catch(()=>false))try{await Promise.race([bridge('reminder_clear'),delay(5000)]);}catch{}
 if(browser)await browser.close().catch(()=>{});if(serial){await adb('forward','--remove',`tcp:${ports.cdp}`).catch(()=>{});await adb('reverse','--remove',`tcp:${ports.proxy}`).catch(()=>{});}if(proxy)await proxy.close();if(child)child.kill();
 await writeFile(join(run,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+run);
}
