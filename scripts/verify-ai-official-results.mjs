// Read-only follow-up: no job submission and no administrator credentials.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const directory=join(root,'acceptance-results/ai-official-three-clients/20261016-default20');
const budget=JSON.parse(await readFile(join(directory,'paid-budget.json'),'utf8'));
expect(budget.submitted).toBe(5);expect(budget.requests.every(r=>r.state==='SUCCEEDED')).toBe(true);
expect(budget.platforms).toEqual({web:'PASS',android:'PASS',windows:'PASS'});
const accounts=JSON.parse(await readFile(join(root,'.desktop-tools/public-web/private/accounts.json'),'utf8'));
const email='web-acceptance-a-20261012@example.test',origin='https://plan.zijun701.cn';
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai',serviceWorkers:'block'});
const output=join(directory,'read-only-'+new Date().toISOString().replace(/[:.]/g,'-'));
await mkdir(output,{recursive:true});
const report={scope:'Fresh browser views and real cloud records created by the three clients; no paid requests',status:'FAIL',cases:[]};
try{
  const login=await context.request.post(origin+'/api/auth/login',{data:{email,password:accounts[email].password}});expect(login.status()).toBe(200);
  const auth=await login.json();expect(auth.user.role).toBe('USER');
  async function api(path){const r=await context.request.get(origin+'/api'+path,{headers:{Authorization:'Bearer '+auth.accessToken}});expect(r.status()).toBe(200);return r.json();}
  const quota=await api('/ai/quota');expect(quota.dailyLimit).toBe(20);expect(quota.overrideLimit).toBeNull();expect(quota.paused).toBe(false);expect(quota.used).toBe(5);expect(quota.pending).toBe(0);expect(quota.remaining).toBe(15);
  const admin=await context.request.get(origin+'/api/admin/ai/settings',{headers:{Authorization:'Bearer '+auth.accessToken}});expect(admin.status()).toBe(403);
  const page=await context.newPage();
  for(const id of new Set(budget.requests.map(r=>r.session))){
    const session=await api('/ai/sessions/'+id);expect(session.applied).not.toBeNull();
    const mode=session.mode;let records;
    if(mode==='PLAN'){const planId=session.applied.path.match(/[a-f0-9-]{36}/)[0];records=await api('/plans/'+planId+'/tasks');}
    if(mode==='ROUTE'){const routeId=session.applied.path.match(/[a-f0-9-]{36}/)[0];records=(await api('/mainlines')).find(x=>x.id===routeId).items;}
    if(mode==='TODO'){const date=session.draft.items[0].dueDate;records=await api('/todos?from='+date+'&to='+date);}
    for(const item of session.draft.items)expect(records.some(r=>r.title===item.title)).toBe(true);
    await page.goto(origin+session.applied.path);
    if(mode==='TODO')await page.getByRole('searchbox').fill(session.draft.items[0].title);
    else await expect(page.getByRole('heading',{name:session.draft.title,exact:true})).toBeVisible();
    if(mode==='TODO')await expect(page.getByRole('heading',{name:session.draft.items[0].title,exact:true})).toBeVisible();
    report.cases.push({mode,itemCount:session.draft.items.length,cloudRecordsVerified:true,freshWebViewVerified:true,status:'PASS'});
  }
  report.quota={dailyLimit:quota.dailyLimit,used:quota.used,pending:quota.pending,remaining:quota.remaining,override:quota.overrideLimit,paused:quota.paused};report.status='PASS';report.paidCalls=0;
}catch{report.error='Read-only cross-client result check failed; no paid retry submitted';process.exitCode=1;}
finally{report.finishedAt=new Date().toISOString();await writeFile(join(output,'report.json'),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report));console.log('Evidence: '+output);}
