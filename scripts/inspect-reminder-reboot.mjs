// Diagnose a retained failed synthetic fixture on a COPY, never rewrite its report or original DB.
import {spawn} from 'node:child_process';import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises';import {createWriteStream} from 'node:fs';
import {join,resolve,sep} from 'node:path';import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';import {assertPortsFree} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),base=join(root,'acceptance-results/historical-closure/reminder-boundaries');
if(process.argv.length!==3)throw Error('One retained synthetic boundary directory required');
const source=resolve(process.argv[2]);if(!source.startsWith(resolve(base)+sep))throw Error('Only retained synthetic boundary evidence allowed');
const out=join(source,'postboot-diagnostic'),port=43184;let child;
const report={status:'FAIL',productionChanged:false,sourceFixtureModified:false};
try{
 await assertPortsFree([port]);await mkdir(out,{recursive:true});const state=JSON.parse(await readFile(join(source,'private-resume.json'),'utf8')),prior=JSON.parse(await readFile(join(source,'report.json'),'utf8'));
 if(prior.status!=='FAIL'||!prior.error.includes('Received:   0'))throw Error('Only the exact zero-notification postboot failure can be inspected');
 const jar=join(root,'backend/target-historical/plan-platform-api.jar');if(createHash('sha256').update(await readFile(jar)).digest('hex')!==prior.hashes.api)throw Error('Exact JAR required');
 await copyFile(join(source,'synthetic-db.mv.db'),join(out,'synthetic-db.mv.db'));
 const log=createWriteStream(join(out,'api.log'));child=spawn('E:/biancheng/JDK21/bin/java.exe',['-Duser.timezone=Asia/Shanghai','-jar',jar,'--spring.profiles.active=acceptance','--server.address=127.0.0.1',`--server.port=${port}`,`--spring.datasource.url=jdbc:h2:file:${join(out,'synthetic-db').replaceAll('\\','/')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`,'--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=','--app.jwt.secret=historical-boundaries-synthetic-secret-32bytes','--app.security.registration-enabled=false','--app.security.refresh-cookie-secure=false','--app.ai.enabled=false','--app.operations.scheduler-enabled=false','--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration'],{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log);child.stderr.pipe(log);
 for(let n=0;n<90;n++){try{if((await fetch(`http://127.0.0.1:${port}/actuator/health`)).ok)break;}catch{}await new Promise(r=>setTimeout(r,1000));}
 async function api(path,data,token){const r=await fetch(`http://127.0.0.1:${port}/api${path}`,{method:data?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data?JSON.stringify(data):undefined});if(!r.ok)throw Error('Synthetic diagnostic HTTP '+r.status);return r.json();}
 const login=await api('/auth/login',{email:state.user.user.email,password:state.password});const tasks=await api(`/plans/${state.schedule.plan.id}/tasks`,undefined,login.accessToken);const task=tasks.find(t=>t.id===state.schedule.task.id);if(!task)throw Error('Retained task missing');
 const devices=await api('/reminders/devices',undefined,login.accessToken),device=devices.find(d=>d.id===state.registration.id);if(!device?.lastSeenAt)throw Error('No retained fresh native poll evidence');
 const start=new Date(`${task.date}T${task.startTime}+08:00`).getTime(),seen=new Date(device.lastSeenAt).getTime();
 report.taskTimeReadback=task.startTime;report.scheduledTime=state.schedule.hm;report.taskReadbackMatches=task.startTime.slice(0,5)===state.schedule.hm;report.pollAfterTaskStart=seen>start;report.lastPollAt=device.lastSeenAt;report.finishedAt=prior.finishedAt;
 if(!report.taskReadbackMatches||!report.pollAfterTaskStart)throw Error('Late-return explanation was not confirmed');
 report.status='CONFIRMED_LATE_RETURN_AFTER_TASK_START';report.explanation='Advance reminders require start.isAfter(now). The retained postboot live poll happened after task start, so absent advance notice is expected. This does not prove a rebooted future alarm delivered.';
 console.log('Late return after task start confirmed against copied fixture. Original failure retained; future postboot delivery still unproven.');
}catch(error){report.error=error.message;process.exitCode=1;console.error('Stopped: '+error.message);}
finally{if(child&&child.exitCode===null)child.kill();await mkdir(out,{recursive:true});await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);}
