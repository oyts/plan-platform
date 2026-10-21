import {spawn} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {join,resolve,sep} from 'node:path';
import {randomUUID,createHash} from 'node:crypto';
import {assertPortsFree,createFaultProxy} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),port=43162,proxyPort=43163;
const resume=process.argv.length===4&&process.argv[2]==='--resume';
if(process.argv.length!==2&&!resume)throw Error('Use no arguments or --resume with an existing synthetic run.');
await assertPortsFree([port,proxyPort]);
const out=resume?resolve(process.argv[3]):join(root,'acceptance-results/stable-trial/pc',new Date().toISOString().replace(/[:.]/g,'-'));
if(!resolve(out).startsWith(resolve(root,'acceptance-results/stable-trial/pc')+sep))throw Error('Invalid private fixture directory');
await mkdir(out,{recursive:true});
const jar=join(root,'backend/target-stable/plan-platform-api.jar'),jarHash=createHash('sha256').update(await readFile(jar)).digest('hex');
const prior=resume?JSON.parse(await readFile(join(out,'state.json'),'utf8')):null;
if(resume&&(prior.jarSha256!==jarHash||prior.port!==port||prior.proxyPort!==proxyPort))throw Error('Resume requires identical accepted API bytes and original isolated ports.');
const args=['-Duser.timezone=Asia/Shanghai','-jar',jar,'--server.address=127.0.0.1',`--server.port=${port}`,'--spring.profiles.active=acceptance',
 `--spring.datasource.url=jdbc:h2:file:${join(out,'synthetic-db').replaceAll('\\','/')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE`,
 '--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=',
 '--spring.h2.console.enabled=false','--app.security.registration-enabled=true','--app.security.email-verification-enabled=false',
 '--app.security.bootstrap-admin-email=','--app.security.password-reset-enabled=false','--app.security.refresh-cookie-secure=false',
 '--app.jwt.secret=stable-trial-isolated-only-not-production-32-bytes','--app.jwt.issuer=stable-pc-synthetic',
 '--app.ai.enabled=false','--app.ai.worker-enabled=false','--app.operations.enabled=false','--app.governance.enabled=false',
 '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration'];
const log=createWriteStream(join(out,'api.log'),{flags:resume?'a':'wx'});
const child=spawn(join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe'),args,{cwd:join(root,'backend'),windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(log);child.stderr.pipe(log);let proxy,stopping=false;
async function stop(){if(stopping)return;stopping=true;clearTimeout(ttl);process.stdin.pause();if(proxy)await proxy.close();if(child.exitCode===null)child.kill();}
const ttl=setTimeout(()=>void stop(),3600_000);process.on('SIGINT',()=>void stop());process.on('SIGTERM',()=>void stop());child.on('exit',()=>{log.end();void stop();});
async function api(path,body,token,method='POST',expected=200){const r=await fetch(`http://127.0.0.1:${port}/api${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});if(r.status!==expected)throw Error(`Synthetic fixture ${path}: HTTP ${r.status}`);return r.json();}
try{
 let healthy=false;for(let i=0;i<90;i++){if(child.exitCode!==null)throw Error('Isolated API exited');try{healthy=(await fetch(`http://127.0.0.1:${port}/actuator/health`)).ok;}catch{}if(healthy)break;await new Promise(r=>setTimeout(r,1000));}if(!healthy)throw Error('Isolated API not ready');
 proxy=await createFaultProxy(proxyPort,port);
 if(resume){
  await writeFile(join(out,'resume-'+Date.now()+'.json'),JSON.stringify({at:new Date().toISOString(),apiProcessId:child.pid,jarSha256:jarHash,previousStateUnchanged:true},null,2),{flag:'wx'});
  console.log('RESUMED: '+out);console.log('Original synthetic account and database retained. Commands: offline | online | stop.');
  process.stdin.setEncoding('utf8');process.stdin.on('data',text=>{const command=text.trim();if(command==='stop')void stop();else if(['offline','online'].includes(command)){proxy.setOnline(command==='online');console.log('Synthetic proxy '+command);}});
 }else{
 const email=`stable-pc-${randomUUID()}@example.test`,password='Stable-'+randomUUID()+'!';
 const account=await api('/auth/register',{email,password,displayName:'稳定候选PC合成验收'},null,'POST',201),token=account.accessToken;
 const day=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());
 const plan=await api('/plans',{name:'合成升级保留计划',startDate:day,endDate:day,restDays:[7]},token,'POST',201);
 const task=await api(`/plans/${plan.id}/tasks`,{title:'合成升级前任务',date:day,startTime:'09:30',endTime:'10:00',plannedMinutes:30,category:'STUDY',required:true,sortOrder:0,acceptance:[]},token,'POST',201);
 const line=await api('/mainlines',{name:'合成模板路线',executionMode:'FLEXIBLE',color:'#002FA7'},token,'POST',201);
 await api(`/mainlines/${line.id}/items`,{title:'合成路线步骤',plannedMinutes:15,sortOrder:0},token,'POST',201);
 await api('/todos',{title:'合成导出待办',dueDate:day,note:'只在本机隔离测试使用'},token,'POST',201);
 await writeFile(join(out,'synthetic-login.txt'),`SYNTHETIC LOCAL ONLY\r\nServer: http://127.0.0.1:${proxyPort}\r\nEmail: ${email}\r\nPassword: ${password}\r\n`,{flag:'wx'});
 await writeFile(join(out,'state.json'),JSON.stringify({schema:1,status:'READY',jarSha256:jarHash,apiProcessId:child.pid,port,proxyPort,planId:plan.id,taskId:task.id,day,productionWrites:0,paidAiCalls:0,realEmailsSent:0},null,2),{flag:'wx'});
 console.log('READY: '+out);console.log('Commands: offline | online | status | stop. Authentication is entered by the user.');
 process.stdin.setEncoding('utf8');process.stdin.on('data',text=>{const command=text.trim();if(command==='stop')void stop();else if(['offline','online'].includes(command)){proxy.setOnline(command==='online');console.log('Synthetic proxy '+command);}else if(command==='status')void api(`/plans/${plan.id}/tasks`,undefined,token,'GET').then(items=>console.log(JSON.stringify({taskTitle:items.find(x=>x.id===task.id)?.title,source:'synthetic-only'}))).catch(()=>console.log('Synthetic status unavailable'));});
 }
}catch(e){console.error(e.message);await stop();process.exitCode=1;}
