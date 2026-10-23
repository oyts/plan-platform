// Exact old release -> candidate, synthetic file database, loopback SMTP, independent Android only.
import {spawn,execFile} from 'node:child_process';import {promisify} from 'node:util';
import {readFile,writeFile,mkdir} from 'node:fs/promises';import {createWriteStream} from 'node:fs';
import {randomUUID,createHash} from 'node:crypto';import {join} from 'node:path';import {fileURLToPath} from 'node:url';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {assertPortsFree,selectDevice} from './native-regression-support.mjs';import {syntheticMailbox} from './synthetic-mailbox.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),out=join(root,'acceptance-results/historical-closure/android-flows',new Date().toISOString().replace(/[:.]/g,'-'));
const pkg='cn.zijun701.plan.mobile.regression',port=43174,cdp=43175,smtpPort=43172;
const hashes={old:'b77b2e0970b37225dce66cfdb776fa23aeaf363c4e356811ec9a0dc1883c91b2',api:'452809438722476dde9a7a9c890967a4140b033aa940f39c915809e4e476e7e6',apk:'362119e8b6a73412a49f892ed7d52099f55e470a0cda7040d978b995ac1f4624'};
const files={old:join(root,'backend/target-stable/plan-platform-api.jar'),api:join(root,'backend/target-historical/plan-platform-api.jar'),apk:join(root,'acceptance-results/historical-closure/android-candidate/PlanCabin-0.1.19-debug.apk')};
const exec=promisify(execFile),adbPath=join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe'),delay=ms=>new Promise(r=>setTimeout(r,ms));
let serial,child,browser,page,mailbox,reverse=false,forward=false;
const report={status:'FAIL',cases:[],hashes,scope:'Actual prior0.1.19 import -> candidate H2 migration; independent Android full recycling and local SMTP dual proof. No real mail/production account/paid AI.',realEmails:0,paidAiCalls:0,formalAppTouched:false};
async function adb(...args){return(await exec(adbPath,[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:20000,maxBuffer:3e6})).stdout.trim();}
async function wait(fn,label,ms=60000){const until=Date.now()+ms;while(Date.now()<until){if(await fn())return;await delay(400);}throw Error('Timed out: '+label);}
async function guard(){const dump=await adb('shell','dumpsys','window'),focus=dump.split(/\r?\n/).filter(x=>x.includes('mCurrentFocus=')&&!x.includes('mCurrentFocus=null'));if(focus.length!==1||!focus[0].includes(pkg+'/'))throw Error('System prompt or another app is foreground; input stopped.');}
async function safe(){try{await guard();}catch{console.log('WAITING_USER_RETURN: 请本人处理系统提示并返回独立测试包，不发送密码。');await wait(async()=>{try{await guard();return true;}catch{return false;}},'user return from system prompt',180000);}}
async function bridge(command,args={}){return page.evaluate(async({command,args})=>(await window.Capacitor.Plugins.PlanNative.execute({command,args})).value,{command,args});}
async function api(path,data,token,expected=200,method=data?'POST':'GET'){
 const r=await fetch(`http://127.0.0.1:${port}/api${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data?JSON.stringify(data):undefined,signal:AbortSignal.timeout(20000)});
 if(r.status!==expected){const body=await r.text();throw Error(`${path}: HTTP ${r.status}, expected ${expected}; jwtRejected=${(r.headers.get('www-authenticate')||'').includes('invalid_token')}; accountRejected=${body.includes('会话已撤销')||body.includes('账号已停用')}`);}return expected===204||expected===401?null:r.json();
}
async function stop(){if(child&&child.exitCode===null){const owned=child;owned.kill();await wait(()=>owned.exitCode!==null||owned.signalCode!==null,'owned Java exit',15000);}child=undefined;}
async function start(kind){
 await assertPortsFree([port]);const db=join(out,'synthetic-db').replaceAll('\\','/');
 // Windows process termination is not a graceful JVM exit. Flush each synthetic commit before switching JARs.
 const args=['-Duser.timezone=Asia/Shanghai','-jar',files[kind],'--spring.profiles.active=acceptance','--server.address=127.0.0.1',`--server.port=${port}`,`--spring.datasource.url=jdbc:h2:file:${db};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`,'--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=','--app.jwt.secret=historical-flow-synthetic-only-32-bytes-secret','--app.security.registration-enabled=true','--app.security.email-verification-enabled=false','--app.security.bootstrap-admin-email=','--app.security.refresh-cookie-secure=false','--app.ai.enabled=false','--app.operations.enabled=false','--app.operations.scheduler-enabled=false','--app.operations.mail-alerts-enabled=false'];
 if(kind==='api')args.push('--app.security.email-change-enabled=true',`--spring.mail.host=127.0.0.1`,`--spring.mail.port=${smtpPort}`,'--spring.mail.properties.mail.smtp.auth=false','--spring.mail.properties.mail.smtp.starttls.enable=false','--spring.mail.properties.mail.smtp.connectiontimeout=5000','--spring.mail.properties.mail.smtp.timeout=5000','--app.mail-from=synthetic-sender@example.test','--app.public-url=https://localhost/#');
 else args.push('--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration');
 const log=createWriteStream(join(out,kind+'-api.log'));child=spawn('E:/biancheng/JDK21/bin/java.exe',args,{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log);child.stderr.pipe(log);
 await wait(async()=>{if(child.exitCode!==null)throw Error('Isolated Java exited');try{return(await fetch(`http://127.0.0.1:${port}/actuator/health`,{signal:AbortSignal.timeout(3000)})).ok;}catch{return false;}},kind+' readiness',90000);
}
async function check(name,fn){const row={name,status:'FAIL'};report.cases.push(row);try{await fn();row.status='PASS';console.log('[PASS] '+name);}finally{await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));}}
async function settings(){await safe();await page.evaluate(()=>location.hash='/settings');await expect(page.getByRole('region',{name:'更改登录邮箱'})).toBeVisible();}
async function login(email,password){await safe();await expect(page.getByLabel('邮箱',{exact:true})).toBeVisible();await page.getByLabel('邮箱',{exact:true}).fill(email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page.getByRole('heading',{name:'今日控制台',exact:true})).toBeVisible();await safe();}
try{
 await mkdir(out,{recursive:true});await assertPortsFree([port,cdp,smtpPort]);serial=selectDevice(await adb('devices'));
 for(const kind of ['old','api','apk'])expect(createHash('sha256').update(await readFile(files[kind])).digest('hex')).toBe(hashes[kind]);
 const installed=(await adb('shell','pm','path',pkg)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!installed)throw Error('Independent APK missing');expect((await adb('shell','sha256sum',installed)).split(/\s+/)[0]).toBe(hashes.apk);
 const oldEmail=`old-${randomUUID()}@example.test`,newEmail=`new-${randomUUID()}@example.test`,password='SyntheticFlow123!';
 mailbox=await syntheticMailbox(smtpPort,[oldEmail,newEmail],{maxMessages:2});await start('old');
 const account=await api('/auth/register',{email:oldEmail,password,displayName:'合成手机完整收口'},null,201),token=account.accessToken;
 const source={schemaVersion:6,planVersion:'1.10.1',progress:{},reflections:{},workspace:{customPlans:[{id:'live',name:'合成旧版活动计划',startDate:'2026-10-01',endDate:'2026-10-31',tags:[],tasks:[]}],customMainlines:[],trash:[{id:'trash:plan:removed',type:'plan',deletedAt:'2026-10-20T01:00:00Z',payload:{plan:{id:'removed',name:'合成旧版删除计划',startDate:'2026-10-01',endDate:'2026-10-31',tags:[],tasks:[]},progress:{},reflections:{}}}],deletionMarkers:[],taskTemplates:[]}};
 const batch=await api('/migrations/legacy/import',source,token,201);expect(batch.createdPlanIds).toHaveLength(1);expect(batch.recyclingVersion).toBeUndefined();
 await api('/auth/me',undefined,token);
 await writeFile(join(out,'synthetic-state.json'),JSON.stringify({email:oldEmail,newEmail,password,token,owner:account.user.id,batchId:batch.importId}),{flag:'wx'});
 const oldPlan=(await api('/plans',undefined,token))[0],edited='合成迁移后本人编辑';await api(`/plans/${oldPlan.id}`,{name:edited,description:oldPlan.description,startDate:oldPlan.startDate,endDate:oldPlan.endDate,restDays:oldPlan.restDays,version:oldPlan.version},token,200,'PUT');
 await stop();await start('api');const preview=await api(`/migrations/legacy/${batch.importId}/recycling-preview`,undefined,token);expect(preview.alreadyIncluded).toBe(false);expect(preview.entries).toHaveLength(1);
 await adb('reverse',`tcp:${port}`,`tcp:${port}`);reverse=true;await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await safe();
 const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('No unique independent PID');await adb('forward',`tcp:${cdp}`,`localabstract:webview_devtools_remote_${pid}`);forward=true;
 browser=await chromium.connectOverCDP(`http://127.0.0.1:${cdp}`,{noDefaults:true});page=browser.contexts().flatMap(c=>c.pages()).find(p=>/^https?:\/\/localhost/.test(p.url()));if(!page)throw Error('No independent WebView');
 await safe();await bridge('reminder_clear');await bridge('desktop_save_server',{serverUrl:`http://127.0.0.1:${port}`});await page.reload();await login(oldEmail,password);
 await check('实际旧版批次的手机预检、明确确认补迁与当前编辑保护',async()=>{
  await settings();const panel=page.getByRole('region',{name:'补迁旧版回收站'});await panel.getByLabel('已完成批次').selectOption(batch.importId);await panel.getByRole('button',{name:'只读预检'}).click();await expect(panel.getByText('待补快照 1 项')).toBeVisible();
  const apply=panel.getByRole('button',{name:'确认补迁回收站'});await expect(apply).toBeDisabled();expect((await api(`/migrations/legacy/${batch.importId}/recycling-preview`,undefined,token)).alreadyIncluded).toBe(false);await safe();await panel.getByRole('checkbox').check();await apply.click();await expect(panel.getByRole('status')).toContainText('回收站补迁完成');
  expect((await api('/plans',undefined,token))[0].name).toBe(edited);expect((await api(`/migrations/legacy/${batch.importId}/recycling-preview`,undefined,token)).alreadyIncluded).toBe(true);await panel.screenshot({path:join(out,'supplement-applied.png')});
 });
 await check('手机二次邮箱校验和本地SMTP真实两封验证邮件',async()=>{
  await safe();const panel=page.getByRole('region',{name:'更改登录邮箱'});await panel.getByLabel('新邮箱',{exact:true}).fill(newEmail);await panel.getByLabel('再次输入新邮箱').fill('different@example.test');await panel.getByLabel('当前密码',{exact:true}).fill(password);await panel.getByRole('button',{name:'发送两封验证邮件'}).click();await expect(panel.getByRole('alert')).toContainText('两次输入的新邮箱不一致');expect(mailbox.messages).toHaveLength(0);
  await panel.getByLabel('再次输入新邮箱').fill(newEmail);await panel.getByRole('button',{name:'发送两封验证邮件'}).click();await expect(panel).toContainText('尚未更改登录邮箱');expect(mailbox.messages).toHaveLength(2);await expect(panel.getByRole('button',{name:'确认变更并退出全部设备'})).toBeDisabled();
 });
 async function proof(side){const message=mailbox.messages.find(m=>m.recipient===(side==='old'?oldEmail:newEmail));const raw=message?.body.match(/https:\/\/localhost\/#\/change-email#[^\s<>]+/)?.[0];if(!raw)throw Error('Local synthetic verification link unavailable');const route=raw.slice(raw.indexOf('/#/')+2);await safe();await page.evaluate(route=>location.hash=route,route);const button=page.getByRole('button',{name:'确认本邮箱验证'});await expect(button).toBeVisible();await safe();await button.click();await expect(page.getByRole('status')).toContainText('登录邮箱尚未更改');}
 await check('手机逐侧主动验证，一侧不足，两侧后仍需最终密码和确认',async()=>{
  await proof('old');await settings();let panel=page.getByRole('region',{name:'更改登录邮箱'});await panel.getByRole('button',{name:'重新读取变更状态'}).click();const state=await api('/auth/email-change/status',undefined,token);expect(state.oldVerified).toBe(true);expect(state.newVerified).toBe(false);await expect(panel.getByRole('button',{name:'确认变更并退出全部设备'})).toBeDisabled();
  await proof('new');await settings();panel=page.getByRole('region',{name:'更改登录邮箱'});await panel.getByRole('button',{name:'重新读取变更状态'}).click();await expect(panel.getByRole('button',{name:'确认变更并退出全部设备'})).toBeDisabled();await panel.getByLabel('再次输入当前密码').fill(password);await panel.getByRole('checkbox').check();await expect(panel.getByRole('button',{name:'确认变更并退出全部设备'})).toBeEnabled();await safe();await panel.getByRole('button',{name:'确认变更并退出全部设备'}).click();await expect(page.getByRole('status')).toContainText('使用新邮箱');await api('/auth/me',undefined,token,401);
 });
 await check('手机使用新邮箱重新登录，原计划和补迁结果仍在',async()=>{await login(newEmail,password);const fresh=await api('/auth/login',{email:newEmail,password});expect((await api('/plans',undefined,fresh.accessToken))[0].name).toBe(edited);expect((await api(`/migrations/legacy/${batch.importId}/recycling-preview`,undefined,fresh.accessToken)).alreadyIncluded).toBe(true);report.localSmtpMessages=mailbox.messages.length;});
 report.status='PASS';
}catch(error){report.error=error.message;console.error('Stopped: '+error.message);process.exitCode=1;}
finally{
 if(browser)await browser.close().catch(()=>{});if(forward)await adb('forward','--remove',`tcp:${cdp}`).catch(()=>{});if(reverse)await adb('reverse','--remove',`tcp:${port}`).catch(()=>{});await stop().catch(()=>{report.cleanupError='Owned Java exit not confirmed';});if(mailbox)await mailbox.close();
 report.finishedAt=new Date().toISOString();await mkdir(out,{recursive:true});await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);
}
