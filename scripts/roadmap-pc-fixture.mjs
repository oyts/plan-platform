// Isolated fixtures only. Windows UI is intentionally driven separately through
// Computer Use; this process never attaches CDP or enters authentication fields.
import {spawn} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {randomUUID,createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {join,resolve,sep} from 'node:path';
import {PORTS,validateManifest,sourceFingerprint,verifyHash,assertPortsFree,createFaultProxy} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const out=join(root,'acceptance-results/roadmap-pc',new Date().toISOString().replace(/[:.]/g,'-'));
const manifest=validateManifest(JSON.parse(await readFile(join(root,'.desktop-tools/native-regression/build.json'),'utf8')));
if(await sourceFingerprint(root)!==manifest.sourceSha256)throw new Error('Rebuild current independent client first.');
await verifyHash(join(root,'.desktop-tools/native-regression/plan-regression.exe'),manifest.desktopSha256);
const jar=join(root,'backend/target-acceptance/plan-platform-api.jar');await verifyHash(jar,manifest.apiSha256);
await assertPortsFree([PORTS.api,PORTS.windowsProxy]);await mkdir(out,{recursive:true});
let email=`pc-roadmap-${randomUUID()}@example.test`,password='PcSynthetic-'+randomUUID()+'!';
if(process.argv[2]){
 const previous=resolve(process.argv[2]),allowed=resolve(root,'acceptance-results/roadmap-pc')+sep;
 if(!previous.startsWith(allowed)||!previous.endsWith(sep+'synthetic-login.txt'))throw new Error('Only an earlier isolated synthetic-login.txt may be reused.');
 const text=await readFile(previous,'utf8');email=text.match(/^Email: (.+)$/m)?.[1]?.trim();password=text.match(/^Password: (.+)$/m)?.[1]?.trim();
 if(!/^pc-roadmap-[a-f0-9-]+@example\.test$/.test(email||'')||!/^PcSynthetic-[a-f0-9-]+!$/.test(password||''))throw new Error('Refusing non-synthetic credentials.');
}
const bootstrap=randomUUID()+randomUUID();
const args=['-Duser.timezone=Asia/Shanghai','-jar',jar,'--server.address=127.0.0.1',`--server.port=${PORTS.api}`,'--spring.profiles.active=acceptance',
 `--spring.datasource.url=jdbc:h2:file:${join(out,'synthetic-db').replaceAll('\\','/')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE`,
 '--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=',
 '--spring.h2.console.enabled=false','--app.security.registration-enabled=true','--app.security.email-verification-enabled=false',
 '--app.security.invitation-enabled=false','--app.security.password-reset-enabled=false','--app.security.refresh-cookie-secure=false',
 '--app.security.max-sessions=40',`--app.security.bootstrap-admin-email=${email}`,
 `--app.bootstrap.token-hash=${createHash('sha256').update(bootstrap).digest('hex')}`,`--app.bootstrap.expires-at=${new Date(Date.now()+3600_000).toISOString()}`,
 '--app.jwt.secret=pc-roadmap-synthetic-only-at-least-32-bytes','--app.jwt.issuer=pc-roadmap-isolated',
 '--app.ai.enabled=false','--app.mail-from=','--management.health.mail.enabled=false',
 '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration',
 '--app.operations.enabled=true','--app.operations.status-file=','--app.operations.scheduler-enabled=false','--app.governance.enabled=true'];
const log=createWriteStream(join(out,'api.log'));
const child=spawn(join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe'),args,{cwd:join(root,'backend'),windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(log);child.stderr.pipe(log);
let proxy,stopping=false,networkTimer;
async function stop(){if(stopping)return;stopping=true;clearTimeout(ttl);clearInterval(networkTimer);process.stdin.pause();await proxy?.close();if(child.exitCode===null)child.kill();log.end();process.exitCode=0;}
process.on('SIGINT',()=>void stop());process.on('SIGTERM',()=>void stop());
const ttl=setTimeout(()=>void stop(),4*3600_000);
child.on('exit',()=>{clearTimeout(ttl);if(!stopping){console.error('Isolated API exited.');void stop().then(()=>{process.exitCode=1;});}});
async function api(path,body,token,method=body===undefined?'GET':'POST',expected=200){
 const r=await fetch(`http://127.0.0.1:${PORTS.api}/api${path}`,{method,headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...(token?{Authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
 if(r.status!==expected)throw new Error(`Synthetic setup ${method} ${path}: HTTP ${r.status}`);return expected===204?null:r.json();
}
try{
 let healthy=false;for(let i=0;i<90;i++){if(child.exitCode!==null)throw new Error('API startup failed.');try{const r=await fetch(`http://127.0.0.1:${PORTS.api}/actuator/health`);healthy=r.ok&&(await r.json()).status==='UP';}catch{}if(healthy)break;await new Promise(r=>setTimeout(r,1000));}
 if(!healthy)throw new Error('Isolated API not healthy.');
 const admin=await api('/auth/register',{email,password,displayName:'PC 合成验收管理员',invitationToken:bootstrap},undefined,'POST',201);
 if(admin.user.role!=='ADMIN')throw new Error('Synthetic administrator authorization failed.');
 const member=await api('/auth/register',{email:`pc-member-${randomUUID()}@example.test`,password,displayName:'PC 合成治理成员'},undefined,'POST',201);
 const token=admin.accessToken,day=(await api('/dashboard/today',undefined,token)).date;
 const shift=n=>{const d=new Date(day+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);};
 const plan=await api('/plans',{name:'PC 合成周期',startDate:day,endDate:shift(60),restDays:[7]},token,'POST',201);
 const tasks=await api(`/plans/${plan.id}/tasks/recurring`,{task:{title:'PC 重复练习',date:shift(1),plannedMinutes:20,category:'STUDY',acceptance:[],required:true,sortOrder:0},mode:'DAILY',until:shift(3)},token,'POST',201);
 const route=await api('/mainlines',{name:'PC 合成长路线',color:'#002FA7',executionMode:'SEQUENTIAL'},token,'POST',201);
 for(let i=0;i<12;i++)await api(`/mainlines/${route.id}/items`,{title:`PC 合成步骤${i}`,plannedMinutes:15,sortOrder:i,acceptance:[]},token,'POST',201);
 const todo=await api('/todos',{title:'PC 冲突共同基础',note:'合成原备注',dueDate:day},token,'POST',201);
 await api('/todos',{title:'PC 恢复样本',note:'仅合成备份',dueDate:day},member.accessToken,'POST',201);
 const backup=await api('/data/backup',undefined,member.accessToken);
 await writeFile(join(out,'synthetic-backup.json'),JSON.stringify(backup,null,2));
 await writeFile(join(out,'private-fixture.json'),JSON.stringify({schema:1,port:PORTS.api,proxyPort:PORTS.windowsProxy,pid:child.pid,admin,member,day,plan,tasks,route,todo},null,2));
 const loginFile=join(out,'synthetic-login.txt');await writeFile(loginFile,`ISOLATED SYNTHETIC ACCOUNT ONLY\r\nServer: http://127.0.0.1:${PORTS.windowsProxy}\r\nEmail: ${email}\r\nPassword: ${password}\r\nDo not use your real account here.\r\n`);
 proxy=await createFaultProxy(PORTS.windowsProxy,PORTS.api);
 const networkPath=join(out,'network-switch.json');await writeFile(networkPath,JSON.stringify({mode:'online'}));let mode='online';
 networkTimer=setInterval(async()=>{try{const text=await readFile(networkPath,'utf8');if(text.length>100)throw new Error('Invalid control');const next=JSON.parse(text).mode;if(!['online','offline','stop'].includes(next))throw new Error('Invalid mode');if(next==='stop')await stop();else if(next!==mode){proxy.setOnline(next==='online');mode=next;console.log(`Synthetic proxy ${mode.toUpperCase()}`);}}catch{proxy.setOnline(false);mode='offline';}},200);
 await writeFile(join(out,'state.json'),JSON.stringify({status:'READY',sourceSha256:manifest.sourceSha256,apiSha256:manifest.apiSha256,desktopSha256:manifest.desktopSha256,productionTouched:false,paidAiCalls:0,realEmailsSent:0},null,2));
 console.log(`READY: ${out}`);console.log(`Synthetic login: ${loginFile}`);
 process.stdin.setEncoding('utf8');process.stdin.on('data',data=>{for(const command of data.trim().split(/\r?\n/)){if(command==='offline'){proxy.setOnline(false);console.log('Synthetic proxy OFFLINE');}else if(command==='online'){proxy.setOnline(true);console.log('Synthetic proxy ONLINE');}else if(command==='stop')void stop();}});
}catch(error){console.error(error.message);await stop();process.exitCode=1;}
