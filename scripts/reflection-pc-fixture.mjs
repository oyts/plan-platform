// Loopback-only Stage 4 fixture. Login is deliberately left to the user.
import {spawn} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {join,resolve,sep} from 'node:path';
import {assertPortsFree,sourceFingerprint,createFaultProxy} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),port=43153,proxyPort=43154;
const resume=process.argv[2]==='--resume'&&process.argv.length===4;
if(process.argv.length>2&&!resume)throw Error('Only --resume with an existing synthetic run is supported.');
await assertPortsFree([port,proxyPort]);
const allowed=resolve(root,'acceptance-results/reflection/pc')+sep;
const out=resume?resolve(process.argv[3]):join(allowed,new Date().toISOString().replace(/[:.]/g,'-'));
if(!out.startsWith(allowed))throw Error('Resume is limited to reflection synthetic output.');
await mkdir(out,{recursive:true});
const jar=join(root,'backend/target-reflection/plan-platform-api.jar'),exe=join(root,'.desktop-tools/native-regression/plan-regression.exe');
const hash=async file=>createHash('sha256').update(await readFile(file)).digest('hex');
const manifest={sourceSha256:await sourceFingerprint(root),apiSha256:await hash(jar),desktopSha256:await hash(exe),formalApplicationTouched:false,paidAiCalls:0,realEmailsSent:0,port,proxyPort};
const args=['-Duser.timezone=Asia/Shanghai','-jar',jar,'--server.address=127.0.0.1',`--server.port=${port}`,'--spring.profiles.active=acceptance',
 `--spring.datasource.url=jdbc:h2:file:${join(out,'synthetic-db').replaceAll('\\','/')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE`,
 '--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=',
 '--spring.h2.console.enabled=false','--app.security.registration-enabled=true','--app.security.email-verification-enabled=false','--app.security.bootstrap-admin-email=',
 '--app.security.refresh-cookie-secure=false','--app.jwt.secret=reflection-isolated-only-not-production-32-bytes','--app.jwt.issuer=reflection-pc-synthetic',
 '--app.ai.enabled=false','--app.ai.worker-enabled=false','--app.operations.enabled=false','--app.governance.enabled=false',
 '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration'];
// Resume must preserve the preceding run's startup/failure evidence.
const log=createWriteStream(join(out,'api.log'),{flags:resume?'a':'w'});
const child=spawn(join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe'),args,{cwd:join(root,'backend'),windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(log);child.stderr.pipe(log);let stopping=false,proxy;
const stop=async()=>{if(stopping)return;stopping=true;clearTimeout(ttl);process.stdin.pause();if(proxy)await proxy.close();if(child.exitCode===null)child.kill();};
const ttl=setTimeout(()=>void stop(),3600_000);process.on('SIGINT',()=>void stop());process.on('SIGTERM',()=>void stop());
child.on('exit',()=>{log.end();clearTimeout(ttl);process.stdin.pause();if(proxy)void proxy.close();});
async function api(path,body,token,method='POST',expected=200){const r=await fetch(`http://127.0.0.1:${port}/api${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body)});if(r.status!==expected)throw Error(`Synthetic setup: ${method} ${path} HTTP ${r.status}`);return r.json();}
try{
 let healthy=false;for(let i=0;i<90;i++){if(child.exitCode!==null)throw Error('Isolated API exited.');try{const r=await fetch(`http://127.0.0.1:${port}/actuator/health`);healthy=r.ok&&(await r.json()).status==='UP';}catch{}if(healthy)break;await new Promise(r=>setTimeout(r,1000));}if(!healthy)throw Error('Isolated API not ready.');
 proxy=await createFaultProxy(proxyPort,port);
 if(resume){const prior=JSON.parse(await readFile(join(out,'state.json'),'utf8'));if(prior.apiSha256!==manifest.apiSha256||prior.desktopSha256!==manifest.desktopSha256)throw Error('Resume requires identical verified bytes.');}
 else{
  const email=`reflection-pc-${randomUUID()}@example.test`,password=`Reflection-${randomUUID()}!`;
  const user=await api('/auth/register',{email,password,displayName:'日周复盘合成验收'},null,'POST',201),token=user.accessToken;
  const date=new Date(Date.now()+8*3600_000).toISOString().slice(0,10);
  await api('/todos',{title:'合成复盘待办',dueDate:date,note:'仅用于本机隔离验收'},token,'POST',201);
  await api(`/reflection/days/${date}`,{version:null,wins:'合成云端收获',blockers:'',nextFocus:'小步推进',energy:'STEADY',burden:'MANAGEABLE',reviewed:false},token,'PUT');
  await writeFile(join(out,'synthetic-login.txt'),`ISOLATED SYNTHETIC ONLY\r\nServer: http://127.0.0.1:${proxyPort}\r\nEmail: ${email}\r\nPassword: ${password}\r\n`);
 }
 await writeFile(join(out,'state.json'),JSON.stringify({...manifest,status:'READY',processId:child.pid},null,2));
 console.log(`READY: ${out}`);console.log('Commands: offline | online | stop. Authentication must be entered by the user.');
 process.stdin.setEncoding('utf8');process.stdin.on('data',text=>{const command=text.trim();if(command==='stop')void stop();else if(command==='offline'||command==='online'){proxy.setOnline(command==='online');console.log(`Synthetic proxy ${command}`);}});
}catch(e){console.error(e.message);await stop();process.exitCode=1;}
