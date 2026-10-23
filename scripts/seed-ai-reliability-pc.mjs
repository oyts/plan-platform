// Synthetic fixture only. No real account, token output, mail or provider access.
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
const base='http://127.0.0.1:43171/api';
const out=resolve('acceptance-results/ai-reliability/pc');await mkdir(out,{recursive:true});
const email=`reliability-pc-${randomUUID()}@example.test`,password=`Synthetic-${randomUUID()}!`;
async function request(path,data,token){const r=await fetch(base+path,{method:data?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(data?{body:JSON.stringify(data)}:{})});if(!r.ok)throw Error(`Synthetic API status ${r.status}`);return r.json();}
const account=await request('/auth/register',{email,password,displayName:'AI可靠性PC合成验收'});
const session=await request('/ai/sessions',{mode:'PLAN',consent:true},account.accessToken);
await writeFile(resolve(out,'synthetic-login.txt'),`Server: http://127.0.0.1:43171\nEmail: ${email}\nPassword: ${password}\nOnly independent regression client; not a real account.\n`,{flag:'wx',mode:0o600});
await writeFile(resolve(out,'synthetic-session.json'),JSON.stringify({id:session.id,createdAt:new Date().toISOString()}),{flag:'wx',mode:0o600});
console.log('Synthetic credentials saved locally: '+resolve(out,'synthetic-login.txt'));
