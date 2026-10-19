// Loopback-only synthetic data. UI and authentication are deliberately separate.
import {spawn} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {join,resolve,sep} from 'node:path';
import {assertPortsFree,sourceFingerprint} from './native-regression-support.mjs';
const resume=process.argv[2]==='--resume'&&process.argv.length===4;
if(process.argv.length>2&&!resume)throw new Error('Only --resume with an existing local synthetic run directory is accepted.');
const root=fileURLToPath(new URL('../',import.meta.url)),port=43137;
await assertPortsFree([port]);
const allowed=resolve(root,'acceptance-results/ai-workbench/pc')+sep;
const runDir=resume?resolve(process.argv[3]):join(allowed,new Date().toISOString().replace(/[:.]/g,'-'));
if(!runDir.startsWith(allowed))throw new Error('Resume is limited to this fixture’s private local output directory.');
await mkdir(runDir,{recursive:true});
const jar=join(root,'backend/target-acceptance/plan-platform-api.jar'),exe=join(root,'.desktop-tools/native-regression/plan-regression.exe');
const hash=async path=>createHash('sha256').update(await readFile(path)).digest('hex');
const manifest={sourceSha256:await sourceFingerprint(root),apiSha256:await hash(jar),desktopSha256:await hash(exe),formalApplicationTouched:false,paidAiCalls:0,realEmailsSent:0};
const args=['-Duser.timezone=Asia/Shanghai','-jar',jar,'--server.address=127.0.0.1',`--server.port=${port}`,'--spring.profiles.active=acceptance',
 `--spring.datasource.url=jdbc:h2:file:${join(runDir,'synthetic-db').replaceAll('\\','/')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE`,
 '--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=',
 '--spring.h2.console.enabled=false','--app.security.registration-enabled=true','--app.security.invitation-enabled=false','--app.security.email-verification-enabled=false',
 '--app.security.refresh-cookie-secure=false','--app.security.max-sessions=10','--app.security.bootstrap-admin-email=',
 '--app.jwt.secret=workbench-isolated-only-not-production-32-bytes','--app.jwt.issuer=workbench-pc-synthetic',
 '--app.ai.enabled=false','--app.ai.worker-enabled=false','--app.mail-from=','--management.health.mail.enabled=false',
 '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration','--app.operations.enabled=false','--app.governance.enabled=false'];
const log=createWriteStream(join(runDir,'api.log'));
const child=spawn(join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe'),args,{cwd:join(root,'backend'),windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(log);child.stderr.pipe(log);let stopping=false;
const stop=()=>{if(stopping)return;stopping=true;clearTimeout(ttl);process.stdin.pause();if(child.exitCode===null)child.kill();};
const ttl=setTimeout(stop,3600_000);process.on('SIGINT',stop);process.on('SIGTERM',stop);child.on('exit',()=>{clearTimeout(ttl);log.end();process.stdin.pause();});
async function api(path,body,token,method='POST',expected=200){const r=await fetch(`http://127.0.0.1:${port}/api${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify(body),signal:AbortSignal.timeout(10000)});if(r.status!==expected)throw new Error(`Synthetic setup failed: ${method} ${path} HTTP ${r.status}`);return r.json();}
try{
 let healthy=false;for(let i=0;i<90;i++){if(child.exitCode!==null)throw new Error('Isolated API exited.');try{const r=await fetch(`http://127.0.0.1:${port}/actuator/health`);healthy=r.ok&&(await r.json()).status==='UP';}catch{}if(healthy)break;await new Promise(resolve=>setTimeout(resolve,1000));}if(!healthy)throw new Error('Isolated API did not become ready.');
 if(resume){const prior=JSON.parse(await readFile(join(runDir,'state.json'),'utf8'));if(prior.apiSha256!==manifest.apiSha256||prior.desktopSha256!==manifest.desktopSha256||prior.formalApplicationTouched!==false)throw new Error('Resume requires the same verified isolated API/client bytes.');await writeFile(join(runDir,'state.json'),JSON.stringify({...prior,status:'READY',resumedAt:new Date().toISOString(),processId:child.pid},null,2));console.log(`READY (preserved synthetic accounts/data): ${runDir}`);process.stdin.setEncoding('utf8');process.stdin.on('data',text=>{if(text.trim()==='stop')stop();});}
 else {
 const email=`workbench-pc-${randomUUID()}@example.test`,password=`PcWorkbench-${randomUUID()}!`;
 const user=await api('/auth/register',{email,password,displayName:'AI工作台合成验收'},null,'POST',201),token=user.accessToken;
 const session=await api('/ai/sessions',{mode:'ROUTE',consent:true},token);
 const draft={schemaVersion:1,mode:'ROUTE',revision:0,title:'PC合成吉他路线',startDate:null,endDate:null,restDays:[],stages:[{localId:'base',title:'合成基础阶段'}],items:Array.from({length:21},(_,i)=>({localId:`step${i}`,title:`合成练习${i+1}`,note:'仅用于本机验收，不是用户真实计划。',dueDate:null,plannedMinutes:15,stageId:'base',prerequisites:[]}))};
 const ready=await api(`/ai/sessions/${session.id}/draft`,{version:session.version,draftJson:JSON.stringify(draft)},token,'PUT');
 await api(`/ai/saved-drafts/${randomUUID()}`,{name:'工作台原生合成草稿',version:-1,contentJson:JSON.stringify({schemaVersion:1,mode:'ROUTE',message:'下一次练习的要求（合成内容，暂不发送）',importJson:'',draft:ready.draft,sourceSessionId:ready.id,sourceVersion:ready.version})},token,'PUT');
 await writeFile(join(runDir,'synthetic-login.txt'),`ISOLATED SYNTHETIC ACCOUNT ONLY\r\nServer: http://127.0.0.1:${port}\r\nEmail: ${email}\r\nPassword: ${password}\r\nOnly use in Plan Cabin Regression. Never use a real account.\r\n`);
 await writeFile(join(runDir,'state.json'),JSON.stringify({...manifest,status:'READY',sessionId:ready.id,port,processId:child.pid},null,2));
 console.log(`READY: ${runDir}`);console.log('Authentication must be entered by the user in the isolated regression window.');
 process.stdin.setEncoding('utf8');process.stdin.on('data',text=>{if(text.trim()==='stop')stop();});
 }
}catch(error){console.error(error.message);stop();process.exitCode=1;}
