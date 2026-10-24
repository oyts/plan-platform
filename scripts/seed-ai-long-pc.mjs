// Synthetic local fixture only; no credentials printed or external network.
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const base='http://127.0.0.1:43174/api',out=resolve('acceptance-results/ai-long/pc');await mkdir(out,{recursive:true});
const email=`long-pc-${randomUUID()}@example.test`,password=`Synthetic-${randomUUID()}!`;
async function request(path,data,token,method=data?'POST':'GET'){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(data?{body:JSON.stringify(data)}:{})});if(!r.ok)throw Error(`Synthetic API status ${r.status}`);return r.json();}
const health=await request('/health');if(health.service!=='plan-platform-api')throw Error('Unexpected local fixture');
const a=await request('/auth/register',{email,password,displayName:'长规划PC合成验收'}),s=await request('/ai/sessions',{mode:'ROUTE',consent:true},a.accessToken);
const draft={schemaVersion:1,mode:'ROUTE',revision:0,title:'PC长规划合成路线',startDate:null,endDate:null,restDays:[],stages:[{localId:'s1',title:'基础'}],items:Array.from({length:12},(_,i)=>({localId:'t'+i,title:'合成练习'+(i+1),note:'待细化',dueDate:null,plannedMinutes:30,stageId:'s1',prerequisites:i?['t'+(i-1)]:[]}))};
const ready=await request('/ai/sessions/'+s.id+'/draft',{version:s.version,draftJson:JSON.stringify(draft)},a.accessToken,'PUT');
await writeFile(resolve(out,'synthetic-login.txt'),`Server: http://127.0.0.1:43174\nEmail: ${email}\nPassword: ${password}\nOnly independent regression client.\n`,{flag:'wx',mode:0o600});
await writeFile(resolve(out,'synthetic-session.json'),JSON.stringify({id:ready.id,createdAt:new Date().toISOString()}),{flag:'wx',mode:0o600});console.log('Synthetic credentials saved locally: '+resolve(out,'synthetic-login.txt'));
