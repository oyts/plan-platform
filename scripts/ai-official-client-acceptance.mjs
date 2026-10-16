// Bounded real provider requests. Persistent admission markers are never removed
// or replayed automatically. Only the pre-existing synthetic invited account.
import {mkdir,readFile,writeFile,open,unlink} from 'node:fs/promises';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {expect} from '../web/node_modules/@playwright/test/index.mjs';

export function validatePaidBudget(budget,kind){
  if(!['web','windows','android'].includes(kind)||budget?.maximum!==5||!Number.isInteger(budget.submitted)||budget.submitted<0||budget.submitted>5||!Array.isArray(budget.requests)||budget.requests.length!==budget.submitted||!budget.platforms||typeof budget.platforms!=='object')throw Error('Invalid retained paid budget; no request permitted');
  if(budget.platforms[kind])throw Error('This platform has already started official acceptance; inspect its retained marker, never replay it');
}

export async function exerciseOfficial({root,client,api,invoke,token,origin,report}){
  const directory=join(root,'acceptance-results/ai-official-three-clients/20261016-default20');
  await mkdir(directory,{recursive:true});
  const lockFile=join(directory,'request.lock');
  // A crashed or concurrent run fails closed. Never auto-remove another run's
  // lock or edit its persisted admission count to make a retry possible.
  const lock=await open(lockFile,'wx',0o600);
  try{
  const marker=join(directory,'paid-budget.json');
  let budget;
  try{budget=JSON.parse(await readFile(marker,'utf8'));}catch(e){if(e.code!=='ENOENT')throw e;budget={maximum:5,submitted:0,platforms:{},requests:[]};}
  validatePaidBudget(budget,client.kind);
  budget.platforms[client.kind]='STARTED';
  const persist=()=>writeFile(marker,JSON.stringify(budget,null,2),{mode:0o600});
  await persist();
  const baseline=await api('/ai/quota');expect(baseline.dailyLimit).toBe(20);expect(baseline.paused).toBe(false);
  report.official={maximumAcrossThreeClients:5,paidCalls:0,baselineUsed:baseline.used};
  async function navigate(path){if(client.kind==='web')await client.page.goto(origin+path);else await client.page.evaluate(path=>{location.hash=path;},path);}
  async function turn(session,message){
    if(budget.submitted>=budget.maximum)throw Error('Five-request admission bound reached');
    const row={client:client.kind,session:session.id,operationKey:randomUUID(),state:'SUBMITTED',startedAt:new Date().toISOString()};
    budget.requests.push(row);budget.submitted++;report.official.paidCalls++;await persist();
    const path='/ai/sessions/'+session.id;
    const body={message,version:session.version,operationKey:row.operationKey};
    let job;
    // The request goes through the platform's real transport, not a page mock.
    // Direct test API invocation avoids automating a paid Windows UI action.
    if(client.kind==='web'){
      const response=await client.page.request.post(origin+'/api'+path+'/jobs',{headers:{Authorization:'Bearer '+token},data:body});
      expect(response.status()).toBe(202);job=await response.json();
    }else{
      const response=await invoke('desktop_request',{request:{method:'POST',path:'/api'+path+'/jobs',query:null,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body)}});
      expect(response.status).toBe(202);job=JSON.parse(response.body);
    }
    row.job=job.id;await persist();
    for(let i=0;i<100;i++){
      const value=await api(path),result=value.turns.find(x=>x.id===job.id);
      if(result&&!['QUEUED','RUNNING'].includes(result.state)){
        row.state=result.state;row.error=result.error;row.finishedAt=new Date().toISOString();await persist();
        expect(result.state,'Official provider response; never retried').toBe('SUCCEEDED');
        await client.page.getByRole('button',{name:'刷新对话',exact:true}).click();
        await expect(client.page.getByRole('list',{name:'对话记录'})).toContainText(result.reply);
        return value;
      }
      await new Promise(resolve=>setTimeout(resolve,1000));
    }
    throw Error('Official result remains uncertain; marker retained and no retry submitted');
  }
  await expect(client.page.locator('.site-header')).toBeVisible({timeout:20000});
  const mode=client.kind==='web'?'PLAN':client.kind==='android'?null:'TODO';
  let session=await api('/ai/sessions',{mode,consent:true});
  await navigate('/ai?session='+session.id);
  await expect(client.page.getByRole('heading',{name:'AI 规划助手',exact:true})).toBeVisible();
  await expect(client.page.getByRole('button',{name:'刷新对话',exact:true})).toBeEnabled();
  const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());
  if(client.kind==='web'){
    session=await turn(session,'这是合成验收：我确认使用周期计划。计划名为“三天单词复习”。日期2026-10-17到2026-10-19，每周星期日休息。只在10月17日和19日各安排1条15分钟单词复习，总共2条，不要排在18日。请直接给出完整草稿，不必追问。');
    expect(session.draft.mode).toBe('PLAN');expect(session.draft.restDays).toEqual([7]);expect(session.draft.items).toHaveLength(2);
    expect(session.draft.items.every(x=>x.dueDate!=='2026-10-18')).toBe(true);
  }else if(client.kind==='android'){
    session=await turn(session,'这是合成验收：我想空闲时学习吉他，没有固定结束日期，也不要求每天学习。请先推荐最适合的模式，这一轮不要生成草稿，等我确认后再生成。');
    expect(session.mode).toBeNull();expect(session.recommendedMode).toBe('ROUTE');expect(session.draft).toBeNull();
    await client.page.getByRole('button',{name:'选择目标路线',exact:true}).click();
    await expect(client.page.getByRole('status').filter({hasText:'已选择目标路线'})).toBeVisible();
    session=await api('/ai/sessions/'+session.id);
    session=await turn(session,'我确认目标路线。路线名为“吉他入门合成验收”，不设日期。请生成2个阶段、总共3个步骤，每步10分钟：先认识吉他，再练习单音，最后练习简单和弦，后一步以前一步完成为前提。直接给完整草稿，不再追问。');
    expect(session.draft.mode).toBe('ROUTE');expect(session.draft.items).toHaveLength(3);expect(session.draft.stages).toHaveLength(2);
  }else{
    session=await turn(session,'这是合成验收：我确认今日待办。日期'+today+'，仅买牛奶和整理书桌两件事，请直接生成这2条待办的完整草稿，不做长期计划，不追问。');
    expect(session.draft.mode).toBe('TODO');expect(session.draft.items).toHaveLength(2);
    session=await turn(session,'请保留这两条待办、日期、localId与顺序，仅在两条备注中都加上“晚饭前完成”。不要新增或删除待办，请直接返回完整修订草稿。');
    expect(session.draft.items).toHaveLength(2);expect(session.draft.items.every(x=>x.note.includes('晚饭前完成'))).toBe(true);
  }
  // Only the visible explicit confirmation creates business records.
  await expect(client.page.getByLabel('任务标题',{exact:true})).toHaveCount(session.draft.items.length);
  expect(session.applied).toBeNull();
  await client.page.reload();await expect(client.page.getByLabel('任务标题',{exact:true}).first()).toHaveValue(session.draft.items[0].title);
  await client.page.getByRole('checkbox',{name:/我已核对 .* 项内容/}).check();
  await client.page.getByRole('button',{name:'确认生成',exact:true}).click();
  await expect(client.page.locator('.ai-applied')).toContainText('已生成 '+session.draft.items.length+' 项内容');
  const applied=await api('/ai/sessions/'+session.id);expect(applied.applied.count).toBe(session.draft.items.length);
  const usage=await api('/ai/quota');expect(usage.used-baseline.used).toBe(report.official.paidCalls);expect(usage.pending).toBe(0);expect(usage.dailyLimit).toBe(20);
  report.official.result={mode:applied.mode,itemCount:applied.applied.count,usedDelta:usage.used-baseline.used,pending:usage.pending,remaining:usage.remaining};
  report.cases.push({name:client.kind+' real official response, persisted draft, explicit confirmation and exact quota accounting',status:'PASS'});
  budget.platforms[client.kind]='PASS';await persist();
  console.log('[PASS] '+client.kind+' bounded real AI acceptance');
  }finally{await lock.close();await unlink(lockFile);}
}
