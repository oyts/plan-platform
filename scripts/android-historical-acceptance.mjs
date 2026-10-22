// Independent debug app + real Java transport + isolated H2. No real email, AI or formal account.
import {spawn,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {createHash,randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {selectDevice,assertPortsFree} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),pkg='cn.zijun701.plan.mobile.regression',ports={api:43170,cdp:43171};
const out=join(root,'acceptance-results/historical-closure/android',new Date().toISOString().replace(/[:.]/g,'-'));
const apk=join(root,'acceptance-results/historical-closure/android-candidate/PlanCabin-0.1.19-debug.apk'),jar=join(root,'backend/target-historical/plan-platform-api.jar');
const expectedApk='362119e8b6a73412a49f892ed7d52099f55e470a0cda7040d978b995ac1f4624',expectedJar='452809438722476dde9a7a9c890967a4140b033aa940f39c915809e4e476e7e6';
const exec=promisify(execFile),adbPath=join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe');
let serial,child,browser,page,reverse=false,forward=false;const delay=ms=>new Promise(r=>setTimeout(r,ms));
const report={status:'FAIL',cases:[],scope:'New shared settings on independent Android; actual Java/API transport. No transactional old-batch apply or real dual-mailbox proof claimed.',paidAiCalls:0,realEmails:0,formalAppTouched:false};
async function adb(...args){return(await exec(adbPath,[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:20000,maxBuffer:2e6})).stdout.trim();}
async function wait(fn,label,ms=60000){const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await delay(500);}throw Error('Timed out: '+label);}
async function guard(){const text=await adb('shell','dumpsys','window');const focus=text.split(/\r?\n/).filter(x=>x.includes('mCurrentFocus=')&&!x.includes('mCurrentFocus=null'));if(focus.length!==1||!focus[0].includes(pkg+'/'))throw Error('Independent app not uniquely foreground or system prompt; user action needed.');}
async function bridge(command,args={}){return page.evaluate(async({command,args})=>(await window.Capacitor.Plugins.PlanNative.execute({command,args})).value,{command,args});}
async function api(path,data,token,status=200){const response=await fetch(`http://127.0.0.1:${ports.api}/api${path}`,{method:data?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data?JSON.stringify(data):undefined,signal:AbortSignal.timeout(15000)});expect(response.status,path).toBe(status);return response.json();}
async function check(name,fn){const row={name,status:'FAIL'};report.cases.push(row);await fn();row.status='PASS';await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('[PASS] '+name);}
try{
 await mkdir(out,{recursive:true});await assertPortsFree(Object.values(ports));serial=selectDevice(await adb('devices'));
 for(const [file,hash]of[[apk,expectedApk],[jar,expectedJar]])expect(createHash('sha256').update(await readFile(file)).digest('hex')).toBe(hash);
 const installed=(await adb('shell','pm','path',pkg)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!installed)throw Error('Independent app missing');expect((await adb('shell','sha256sum',installed)).split(/\s+/)[0]).toBe(expectedApk);
 report.apkSha256=expectedApk;report.apiSha256=expectedJar;
 const args=['-Duser.timezone=Asia/Shanghai','-jar',jar,'--spring.profiles.active=acceptance','--server.address=127.0.0.1',`--server.port=${ports.api}`,`--spring.datasource.url=jdbc:h2:mem:historical_${randomUUID().replaceAll('-','')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`,'--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=','--app.security.registration-enabled=true','--app.security.email-verification-enabled=false','--app.security.email-change-enabled=true','--app.security.bootstrap-admin-email=','--app.security.refresh-cookie-secure=false','--app.jwt.secret=historical-synthetic-only-at-least-32-bytes','--app.ai.enabled=false','--app.operations.enabled=false','--app.operations.scheduler-enabled=false','--app.operations.mail-alerts-enabled=false','--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration'];
 const log=createWriteStream(join(out,'api.log'));child=spawn('E:/biancheng/JDK21/bin/java.exe',args,{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log);child.stderr.pipe(log);
 await wait(async()=>{if(child.exitCode!==null)throw Error('Isolated API exited');try{return(await fetch(`http://127.0.0.1:${ports.api}/actuator/health`,{signal:AbortSignal.timeout(3000)})).ok;}catch{return false;}},'isolated API',90000);
 const password='SyntheticHistorical123!',user=await api('/auth/register',{email:`historical-${randomUUID()}@example.test`,password,displayName:'合成手机历史收口'},null,201);
 const batch=await api('/migrations/legacy/import',{schemaVersion:6,planVersion:'1.10.1',progress:{},reflections:{},workspace:{customPlans:[{id:'synthetic-plan',name:'合成旧版计划',startDate:'2026-10-01',endDate:'2026-10-31',tags:[],tasks:[]}],customMainlines:[],trash:[],deletionMarkers:[],taskTemplates:[]}},user.accessToken,201);
 await adb('reverse',`tcp:${ports.api}`,`tcp:${ports.api}`);reverse=true;
 await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await wait(async()=>{try{await guard();return true;}catch{return false;}},'independent foreground');
 const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('No unique test process');await adb('forward',`tcp:${ports.cdp}`,`localabstract:webview_devtools_remote_${pid}`);forward=true;
 browser=await chromium.connectOverCDP(`http://127.0.0.1:${ports.cdp}`,{noDefaults:true});page=browser.contexts().flatMap(x=>x.pages()).find(x=>/^https?:\/\/localhost/.test(x.url()));if(!page)throw Error('No independent WebView');
 await check('真实Java连接与合成账号登录',async()=>{await guard();await bridge('reminder_clear');await bridge('desktop_save_server',{serverUrl:`http://127.0.0.1:${ports.api}`});await page.reload();await expect(page.getByLabel('邮箱',{exact:true})).toBeVisible();await guard();await page.getByLabel('邮箱',{exact:true}).fill(user.user.email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page.getByRole('heading',{name:'今日控制台',exact:true})).toBeVisible();});
 await check('新增邮箱变更页面：未配置邮件不能发送且布局无横溢',async()=>{await guard();await page.evaluate(()=>location.hash='/settings');const panel=page.getByRole('region',{name:'更改登录邮箱'});await expect(panel.getByText('邮件服务暂不可用，不能发起新的邮箱变更。')).toBeVisible();await expect(panel.getByRole('button',{name:'发送两封验证邮件'})).toBeDisabled();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await panel.screenshot({path:join(out,'email-unconfigured.png')});});
 await check('新迁移批次真实只读预检：已包含不重复写入',async()=>{await guard();const panel=page.getByRole('region',{name:'补迁旧版回收站'});await panel.getByLabel('已完成批次').selectOption(batch.importId);await panel.getByRole('button',{name:'只读预检'}).click();await expect(panel.getByRole('status')).toContainText('已经包含回收站转换');await expect(panel.getByRole('button',{name:'确认补迁回收站'})).toHaveCount(0);await panel.screenshot({path:join(out,'recycling-included.png')});});
 report.status='PASS';
}catch(error){report.error=error.message;console.error('Stopped: '+error.message);process.exitCode=1;}
finally{
 if(browser)await browser.close().catch(()=>{});if(forward)await adb('forward','--remove',`tcp:${ports.cdp}`).catch(()=>{});if(reverse)await adb('reverse','--remove',`tcp:${ports.api}`).catch(()=>{});
 if(child&&child.exitCode===null){child.kill();await wait(()=>child.exitCode!==null||child.signalCode!==null,'owned API exit',15000).catch(()=>{report.cleanupError='Owned API exit not confirmed';});}
 report.finishedAt=new Date().toISOString();await mkdir(out,{recursive:true});await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);
}
