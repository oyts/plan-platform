// Only owner-approved lock/reboot, independent package and synthetic data. Never unlock or alter OS permissions.
import {spawn,execFile} from 'node:child_process';import {promisify} from 'node:util';
import {readFile,writeFile,mkdir} from 'node:fs/promises';import {createWriteStream} from 'node:fs';
import {randomUUID,createHash} from 'node:crypto';import {join} from 'node:path';import {fileURLToPath} from 'node:url';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {selectDevice,assertPortsFree,createFaultProxy} from './native-regression-support.mjs';
import {reminderWindow,requireFutureWindow,dueWaitBudget} from './reminder-boundary-clock.mjs';
const offlineFollowup=process.argv.length===3&&process.argv[2]==='--offline-followup';
const rebootOnly=process.argv.length===3&&process.argv[2]==='--owner-approved-reboot-only';
if(!offlineFollowup&&!rebootOnly&&!process.argv.includes('--owner-approved-lock-reboot'))throw Error('Explicit owner lock/reboot permission required');
const root=fileURLToPath(new URL('../',import.meta.url)),out=join(root,'acceptance-results/historical-closure/reminder-boundaries',new Date().toISOString().replace(/[:.]/g,'-'));
const pkg='cn.zijun701.plan.mobile.regression',ports={api:43180,proxy:43181,cdp:43182};
const jar=join(root,'backend/target-historical/plan-platform-api.jar'),apk=join(root,'acceptance-results/historical-closure/android-candidate/PlanCabin-0.1.19-debug.apk');
const hashes={api:'452809438722476dde9a7a9c890967a4140b033aa940f39c915809e4e476e7e6',apk:'362119e8b6a73412a49f892ed7d52099f55e470a0cda7040d978b995ac1f4624'};
const exec=promisify(execFile),adbPath=join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe'),delay=ms=>new Promise(r=>setTimeout(r,ms));
let serial,child,browser,page,proxy,user,registration;
const report={status:'FAIL',cases:[],hashes,formalAppTouched:false,realEmails:0,paidAiCalls:0};
async function adb(...args){return(await exec(adbPath,[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:20000,maxBuffer:4e6})).stdout.trim();}
async function wait(fn,label,ms=120000){const until=Date.now()+ms;while(Date.now()<until){if(await fn())return;await delay(600);}throw Error('Timed out: '+label);}
async function unlocked(){const s=await adb('shell','dumpsys','window');return /mAwake=true/.test(s)&&!/mShowingLockscreen=true|isStatusBarKeyguard=true/.test(s);}
async function guard(){const s=await adb('shell','dumpsys','window'),f=s.split(/\r?\n/).filter(x=>x.includes('mCurrentFocus=')&&!x.includes('mCurrentFocus=null'));if(f.length!==1||!f[0].includes(pkg+'/'))throw Error('Independent app not foreground; input stopped');}
async function attach(){const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('Unique regression PID required');await adb('forward',`tcp:${ports.cdp}`,`localabstract:webview_devtools_remote_${pid}`);browser=await chromium.connectOverCDP(`http://127.0.0.1:${ports.cdp}`,{noDefaults:true});page=browser.contexts().flatMap(c=>c.pages()).find(p=>/^https?:\/\/localhost/.test(p.url()));if(!page)throw Error('No independent WebView');}
async function bridge(command,args={}){return page.evaluate(async({command,args})=>(await window.Capacitor.Plugins.PlanNative.execute({command,args})).value,{command,args});}
async function api(path,data,token=user?.accessToken,method=data?'POST':'GET',expected=200){const r=await fetch(`http://127.0.0.1:${ports.api}/api${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data?JSON.stringify(data):undefined,signal:AbortSignal.timeout(15000)});if(r.status!==expected)throw Error(path+': HTTP '+r.status);return expected===204?null:r.json();}
async function check(name,fn){const r={name,status:'FAIL'};report.cases.push(r);try{await fn();r.status='PASS';console.log('[PASS] '+name);}finally{await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));}}
async function schedule(label){
 const window=reminderWindow(Date.now(),{reboot:label==='重启'}),{date,hm}=window;
 const plan=await api('/plans',{name:'合成历史提醒'+label,startDate:date,endDate:date,restDays:[]},undefined,'POST',201);
 const task=await api(`/plans/${plan.id}/tasks`,{date,startTime:hm,plannedMinutes:15,title:'合成历史提醒'+label,category:'STUDY',required:true,sortOrder:0,acceptance:[]},undefined,'POST',201);
 const prefs=await api('/reminders/preferences');await api('/reminders/preferences',{...prefs,advanceEnabled:true,advanceMinutes:1,quietEnabled:false,hideDetails:false},undefined,'PUT');
 await guard();await bridge('reminder_clear');registration=await api('/reminders/devices',{platform:'ANDROID',label:'合成历史边界测试'});await bridge('reminder_enable',{owner:user.user.id,registration});
 return {plan,task,...window};
}
async function ownerUnlock(){console.log('WAITING_OWNER_UNLOCK: 请本人解锁手机并返回独立测试包，不要发送密码。');await wait(async()=>{try{await guard();return await unlocked();}catch{return false;}},'owner unlock and return',600000);}
try{
 await mkdir(out,{recursive:true});await assertPortsFree(Object.values(ports));serial=selectDevice(await adb('devices'));
 expect(createHash('sha256').update(await readFile(jar)).digest('hex')).toBe(hashes.api);expect(createHash('sha256').update(await readFile(apk)).digest('hex')).toBe(hashes.apk);
 const installed=(await adb('shell','pm','path',pkg)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!installed)throw Error('Independent APK missing');expect((await adb('shell','sha256sum',installed)).split(/\s+/)[0]).toBe(hashes.apk);
 const log=createWriteStream(join(out,'api.log'));child=spawn('E:/biancheng/JDK21/bin/java.exe',['-jar',jar,'--server.address=127.0.0.1',`--server.port=${ports.api}`,'--spring.profiles.active=acceptance',`--spring.datasource.url=jdbc:h2:file:${join(out,'synthetic-db').replaceAll('\\','/')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`,'--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=','--app.jwt.secret=historical-boundaries-synthetic-secret-32bytes','--app.security.registration-enabled=true','--app.security.refresh-cookie-secure=false','--app.ai.enabled=false','--app.operations.scheduler-enabled=false','--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration'],{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log);child.stderr.pipe(log);
 await wait(async()=>{try{return(await fetch(`http://127.0.0.1:${ports.api}/actuator/health`)).ok;}catch{return false;}},'isolated health');
 proxy=await createFaultProxy(ports.proxy,ports.api);await adb('reverse',`tcp:${ports.proxy}`,`tcp:${ports.proxy}`);await guard();await attach();await bridge('reminder_clear');await bridge('desktop_save_server',{serverUrl:`http://127.0.0.1:${ports.proxy}`});
 const password='SyntheticBoundary123!';user=await api('/auth/register',{email:`boundary-${randomUUID()}@example.test`,password,displayName:'合成边界验收'},null,'POST',201);await page.reload();await guard();await page.getByLabel('邮箱',{exact:true}).fill(user.user.email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page.getByRole('heading',{name:'今日控制台',exact:true})).toBeVisible();
 const status=await bridge('reminder_status');if(!status.permission||!status.exact)throw Error('Existing notification and exact-alarm permission required; no OS setting changed');
 if(!offlineFollowup&&!rebootOnly)await check('锁屏期间原生唤醒与系统通知',async()=>{
  const s=await schedule('锁屏');await adb('shell','input','keyevent','223');const lockedAt=Date.now();console.log('LOCKED_WAIT: 正在锁屏等待独立合成提醒。');await wait(async()=>Date.now()>s.alertAt+20000,'due time',120000);report.lockedBackgroundPoll=(await api('/reminders/devices')).some(d=>d.id===registration.id&&new Date(d.lastSeenAt).getTime()>lockedAt);expect(report.lockedBackgroundPoll).toBe(true);await ownerUnlock();const d=await bridge('reminder_test_diagnostics');expect(d.http).toBe(200);expect(d.wake).toBeGreaterThan(lockedAt);expect((await bridge('reminder_test_active')).length).toBeGreaterThan(0);report.lockedNativeWake=d.wake;
 });
 if(!offlineFollowup)await check('真实重启后保留授权并重新核对投递',async()=>{
  await guard();const s=await schedule('重启');await writeFile(join(out,'private-resume.json'),JSON.stringify({user,password,registration,schedule:s}),{flag:'wx'});await browser.close();browser=null;page=null;const before=Date.now();console.log('REBOOTING: 已获本人许可，重启手机一次。');await adb('reboot');await delay(3000);
  await wait(async()=>{try{return(await adb('shell','getprop','sys.boot_completed'))==='1';}catch{return false;}},'boot completed',180000);await adb('reverse',`tcp:${ports.proxy}`,`tcp:${ports.proxy}`);await ownerUnlock();await attach();expect((await bridge('reminder_status')).enabled).toBe(true);
  await wait(async()=>{const d=await api('/reminders/devices');return d.some(x=>x.id===registration.id&&new Date(x.lastSeenAt).getTime()>before);},'boot receiver fresh server poll',120000);
  report.bootReceiverFreshPoll=true;requireFutureWindow(s,Date.now());
  await wait(async()=>Date.now()>s.alertAt+15000,'post boot alert time',dueWaitBudget(s,Date.now()));expect((await bridge('reminder_test_active')).length).toBeGreaterThan(0);
 });
 if(offlineFollowup)await check('点击本包合成系统通知返回正确计划且不自动完成任务',async()=>{
  const s=await schedule('通知点击');await adb('shell','input','keyevent','3');console.log('NOTIFICATION_WAIT: 等待合成通知，不锁屏或重启。');await wait(async()=>Date.now()>s.alertAt+20000,'notification due',120000);await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await guard();expect((await bridge('reminder_test_active')).length).toBeGreaterThan(0);
  await adb('shell','cmd','statusbar','expand-notifications');await delay(1000);await adb('shell','uiautomator','dump','/sdcard/plan-cabin-synthetic-notice.xml');const xml=await adb('shell','cat','/sdcard/plan-cabin-synthetic-notice.xml');await adb('shell','rm','/sdcard/plan-cabin-synthetic-notice.xml');
  // Temporary system dump removed immediately; do not log/copy other apps' notification text.
  const matches=[...xml.matchAll(/<node\b[^>]*>/g)].map(x=>x[0]).filter(x=>/text="[^"]*合成历史提醒通知点击/.test(x));if(matches.length!==1)throw Error('Own synthetic notice is not uniquely visible; no tap performed');
  const box=matches[0].match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);if(!box)throw Error('Own notification bounds unavailable');await adb('shell','input','tap',String(Math.floor((+box[1]+ +box[3])/2)),String(Math.floor((+box[2]+ +box[4])/2)));await wait(async()=>{try{await guard();return true;}catch{return false;}},'own notification return');await expect.poll(()=>page.evaluate(()=>location.hash)).toContain(`/plans/${s.plan.id}?date=${s.date}`);expect((await api(`/plans/${s.plan.id}/tasks`))[0].progress.status).toBe('PENDING');
 });
 if(!rebootOnly)await check('真实离线编辑产生队列时原生提醒立即关闭且不清队列',async()=>{
  await guard();await page.evaluate(()=>location.hash='/todos');await expect(page.getByRole('heading',{name:'今日待办',exact:true})).toBeVisible();proxy.setOnline(false);await page.getByLabel('快速记录',{exact:true}).fill('合成离线边界待办');await page.getByRole('button',{name:'记一件事',exact:true}).click();await expect(page.locator('.todo-row').filter({hasText:'合成离线边界待办'})).toBeVisible();expect((await bridge('reminder_status')).enabled).toBe(false);expect(await bridge('reminder_test_active')).toHaveLength(0);await page.evaluate(()=>location.hash='/sync');await expect(page.locator('.sync-summary strong')).toHaveText('1');proxy.setOnline(true);
 });
 report.status='PASS';
}catch(error){report.error=error.message;if(error.message.startsWith('INCONCLUSIVE_LATE_OWNER_RETURN'))report.status='INCONCLUSIVE';console.error('Stopped: '+error.message);process.exitCode=1;}
finally{
 if(page)try{await bridge('reminder_clear');}catch{report.localCleanupUnconfirmed=true;}
 if(user&&registration)await api(`/reminders/devices/${registration.id}`,undefined,undefined,'DELETE',204).catch(()=>{report.serverRevokeUnconfirmed=true;});
 if(browser)await browser.close().catch(()=>{});if(serial){await adb('shell','rm','-f','/sdcard/plan-cabin-synthetic-notice.xml').catch(()=>{});await adb('forward','--remove',`tcp:${ports.cdp}`).catch(()=>{});await adb('reverse','--remove',`tcp:${ports.proxy}`).catch(()=>{});}if(proxy)await proxy.close();if(child&&child.exitCode===null)child.kill();
 report.finishedAt=new Date().toISOString();await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);
}
