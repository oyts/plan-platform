import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {join,basename,dirname,resolve} from 'node:path';
import {createHash} from 'node:crypto';
const root=fileURLToPath(new URL('../',import.meta.url));
const out=join(root,'acceptance-results/stable-trial/pc/2026-10-21T05-12-54-605Z');
const state=JSON.parse(await readFile(join(out,'state.json'),'utf8'));
const text=await readFile(join(out,'synthetic-login.txt'),'utf8');
const email=text.match(/^Email: ([^\r\n]+)$/m)?.[1],password=text.match(/^Password: ([^\r\n]+)$/m)?.[1];
if(!email?.endsWith('@example.test')||!password||state.port!==43162)throw Error('Only the fixed isolated synthetic account is allowed');
const base='http://127.0.0.1:43162/api';
const auth=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password}),signal:AbortSignal.timeout(10000)});
if(!auth.ok)throw Error('Synthetic verification authentication failed; no credentials printed');
const token=(await auth.json()).accessToken;
async function get(path){const r=await fetch(base+path,{headers:{Authorization:'Bearer '+token},signal:AbortSignal.timeout(10000)});if(!r.ok)throw Error('Synthetic read failed');return r.json();}
const tasks=await get(`/plans/${state.planId}/tasks`),plan=(await get('/plans')).find(x=>x.id===state.planId);
if(!plan||tasks.length!==1||tasks[0].id!==state.taskId||tasks[0].title!=='合成升级前任务－升级队列测试'||JSON.stringify(plan.restDays)!=='[7]')throw Error('Synthetic upgrade/sync expectation failed');
const report={schema:1,at:new Date().toISOString(),status:'PASS',originalTaskCount:tasks.length,exactTaskIdPreserved:true,queuedTitleReachedServer:true,restDaysPreserved:true,productionWrites:0,queueChangedByVerifier:false};
await writeFile(join(out,'server-verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
if(process.argv.length>2){
 if(process.argv.length!==4||process.argv[2]!=='--export')throw Error('Expected optional --export with this synthetic Windows download');
 const path=resolve(process.argv[3]);
 if(dirname(path)!==resolve('C:/Users/OYTS/Downloads')||!/^plan-cabin-private-\d{8}T\d{6}\.json$/.test(basename(path)))throw Error('Only the observed synthetic download is allowed');
 const bytes=await readFile(path),backup=JSON.parse(bytes.toString('utf8'));
 if(backup.schemaVersion!==4||backup.records.personal_templates.length!==1||backup.records.personal_templates[0].name!=='PC合成整套方法'||backup.records.plan_tasks.length!==1||backup.records.plan_tasks[0].title!=='合成升级前任务－升级队列测试')throw Error('Synthetic exported structure differs from UI-created records');
 const evidence={status:'PASS',schemaVersion:4,exportedTemplates:1,exportedTasks:1,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),nativeDownloadObserved:true,systemSaveDialogObserved:false,defaultDownloadsDirectory:true,sourceFileModified:false};
 await writeFile(join(out,'windows-export-verification.json'),JSON.stringify(evidence,null,2));console.log(JSON.stringify(evidence));
}
