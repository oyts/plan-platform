// Focused AI UI integration via the real HTTPS API and Rust/Android Java bridges.
// Synthetic invited ordinary account only. No password is typed in a Windows UI.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {spawn,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {sourceFingerprint,PORTS,ANDROID_PACKAGE,selectDevice} from './native-regression-support.mjs';
import {exerciseOfficial} from './ai-official-client-acceptance.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const origin='https://plan.zijun701.cn';
const version=JSON.parse(await readFile(join(root,'web/package.json'),'utf8')).version;
const androidOnly=process.argv.includes('--android-only');
const serialArgument=process.argv.indexOf('--serial');
let serial=serialArgument<0?'':process.argv[serialArgument+1];
const exec=promisify(execFile);
const adbFile=join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe');
let forwardOwned=false;
async function adb(...args){return (await exec(adbFile,[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:45000,maxBuffer:2*1024*1024})).stdout.trim();}
const run=new Date().toISOString().replace(/[:.]/g,'-')+'-'+randomUUID().slice(0,6);
const out=join(root,'acceptance-results/ai-native-web',run);
const users=JSON.parse(await readFile(join(root,'.desktop-tools/public-web/private/accounts.json'),'utf8'));
const account=users['web-acceptance-a-20261012@example.test'];
if(!account?.password)throw Error('Existing invited synthetic acceptance account required');
const secrets=[account.password];
let token,browser,context,page,child,nativeBrowser,nativePage,previousServer;
const report={version,startedAt:new Date().toISOString(),scope:androidOnly?'New AI functionality only, real Android device, isolated regression identity and real Java HTTPS transport; synthetic invited USER, no paid calls. Formal APK separately checked through accessibility.':'New AI functionality only, real HTTPS, synthetic invited USER, no paid calls; isolated Windows package with real Rust transport.',status:'FAIL',cases:[]};
if(process.argv.includes('--official'))report.scope='Real official DeepSeek acceptance, synthetic invited USER only, persistent maximum 5 requests across Web/Rust Windows/Java Android; exact per-run charges in official.paidCalls. Formal native identities are not modified.';
await mkdir(out,{recursive:true});
async function api(path,data,method=data===undefined?'GET':'POST',status=200){
  const r=await fetch(origin+'/api'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(20000)});
  expect(r.status,method+' '+path.split('?')[0]).toBe(status);
  return status>=400||status===204?null:r.json();
}
async function invoke(command,args={}){return nativePage.evaluate(async({command,args,android})=>android?(await window.Capacitor.nativePromise('PlanNative','execute',{command,args})).value:window.__TAURI_INTERNALS__.invoke(command,args),{command,args,android:androidOnly});}
async function capture(client,name){
  if(!androidOnly)return client.page.screenshot({path:join(out,name),fullPage:true});
  // CDP screenshots are unsupported by some physical WebViews. Never capture
  // another app or a password screen as a fallback.
  const focus=await adb('shell','dumpsys','window');
  if(!focus.split(/\r?\n/).some(line=>line.includes('mCurrentFocus=')&&line.includes(ANDROID_PACKAGE+'/')))throw Error('Regression app is not focused; capture withheld');
  if(await client.page.locator('input[type=password]').count())throw Error('Authentication screen; capture withheld');
  const result=await exec(adbFile,['-s',serial,'exec-out','screencap','-p'],{windowsHide:true,encoding:'buffer',timeout:10000,maxBuffer:10*1024*1024});
  await writeFile(join(out,name),result.stdout);
}
async function waitFor(check){for(let i=0;i<80;i++){if(await check())return;await new Promise(r=>setTimeout(r,250));}throw Error('Owned regression WebView not ready');}
async function path(client,value){if(client.kind==='web')await client.page.goto(origin+value);else await client.page.evaluate(path=>{location.hash=path;},value);}
async function scenario(name,action){const row={name,status:'PASS'};report.cases.push(row);try{await action();}catch(e){row.status='FAIL';throw e;}console.log('[PASS] '+name);}
async function exercise(client){
  const quotaBefore=await api('/ai/quota');
  await expect(client.page.locator('.site-header')).toBeVisible({timeout:20000});
  for(const mode of ['PLAN','ROUTE','TODO']){
    const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());
    const draft={schemaVersion:1,mode,revision:0,title:'AI原生合成-'+run.slice(11,19)+'-'+mode,startDate:mode==='PLAN'?today:null,endDate:mode==='PLAN'?today:null,restDays:[],stages:mode==='ROUTE'?[{localId:'s1',title:'合成阶段'}]:[],items:[{localId:'i1',title:'AI合成事项 '+randomUUID().slice(0,8),note:'自动验收合成内容，不是用户资料',dueDate:mode==='ROUTE'?null:today,plannedMinutes:mode==='TODO'?0:15,stageId:mode==='ROUTE'?'s1':null,prerequisites:[]}]};
    let session=await api('/ai/sessions',{mode,consent:true});
    const endpoint='/ai/sessions/'+session.id;
    session=await api(endpoint+'/draft',{version:session.version,draftJson:JSON.stringify(draft)},'PUT');
    const applied=await api(endpoint+'/apply',{version:session.version,confirmed:true});
    await api(endpoint+'?version='+(await api(endpoint)).version,undefined,'DELETE',204);
    const target=mode==='TODO'?(await api('/todos?from='+today+'&to='+today)).find(x=>x.title===draft.items[0].title).id:applied.path.match(/[a-f0-9-]{36}/)[0];
    let adjustmentId;
    await scenario(client.kind+' '+mode+' consent/diff/manual save/resume/confirm',async()=>{
      // Enter through the real business link rather than a forged UI fixture.
      await path(client,applied.path);
      if(mode==='TODO')await client.page.getByRole('searchbox').fill(draft.items[0].title);
      const link=mode==='TODO'?client.page.locator('.todo-row').filter({hasText:draft.items[0].title}).getByRole('link',{name:'AI 调整',exact:true}):client.page.getByRole('link',{name:mode==='PLAN'?'使用 AI 调整此周期计划':'使用 AI 调整此目标路线',exact:true});
      await expect(link).toBeVisible();await link.click();
      await expect(client.page.getByRole('heading',{name:'AI 规划助手',exact:true})).toBeVisible();
      const load=client.page.getByRole('button',{name:'载入所选内容（不调用 AI）',exact:true});await expect(load).toBeDisabled();
      await client.page.getByRole('checkbox',{name:/我同意将所选对象/}).check();await load.click();
      await expect(client.page.getByRole('region',{name:'调整前后对照'})).toContainText('目前没有改动');
      await client.page.waitForURL(url=>url.searchParams.has('session')||url.hash.includes('session='));
      const location=client.kind==='web'?new URL(client.page.url()).search:new URLSearchParams(client.page.url().split('?')[1]).toString();
      adjustmentId=new URLSearchParams(location).get('session');expect(adjustmentId).toMatch(/^[a-f0-9-]{36}$/);
      const title=draft.items[0].title+'－新节奏';
      await client.page.getByLabel('任务标题',{exact:true}).fill(title);
      const addedTitle='新增合成事项 '+randomUUID().slice(0,8);
      await client.page.getByRole('button',{name:'添加步骤',exact:true}).click();
      await client.page.getByLabel('任务标题',{exact:true}).nth(1).fill(addedTitle);
      await expect(client.page.getByRole('region',{name:'调整前后对照'})).toContainText('新增');
      await expect(client.page.getByRole('button',{name:'确认应用修改',exact:true})).toBeDisabled();
      await client.page.getByRole('button',{name:'保存草稿修改',exact:true}).click();
      await expect(client.page.getByRole('status').filter({hasText:'调整草稿已保存'})).toBeVisible();
      await client.page.reload();await expect(client.page.getByLabel('任务标题',{exact:true}).nth(0)).toHaveValue(title);
      await expect(client.page.getByLabel('任务标题',{exact:true}).nth(1)).toHaveValue(addedTitle);
      await expect(client.page.getByRole('region',{name:'调整前后对照'})).toContainText('新节奏');
      await expect(client.page.locator('html')).toHaveClass(new RegExp(client.kind==='android'?'android-client':client.kind==='windows'?'desktop-client':'.*'));
      expect(await client.page.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth)).toBeLessThanOrEqual(1);
      await capture(client,client.kind+'-'+mode+'-preview.png');
      await client.page.getByRole('checkbox',{name:/我已逐项核对前后差异/}).check();await client.page.getByRole('button',{name:'确认应用修改',exact:true}).click();
      await expect(client.page.locator('.ai-applied')).toContainText('执行历史保留');
      const result=await api('/ai/sessions/'+adjustmentId);expect(result.applied.path).toBe(applied.path);
      if(mode==='TODO')expect((await api('/todos?from='+today+'&to='+today)).find(x=>x.id===target).title).toBe(title);
      if(mode==='ROUTE')expect((await api('/mainlines')).find(x=>x.id===target).items[0].title).toBe(title);
      if(mode==='PLAN')expect((await api('/plans/'+target+'/tasks')).find(x=>x.title===title)).toBeTruthy();
      const items=mode==='TODO'?await api('/todos?from='+today+'&to='+today):mode==='ROUTE'?(await api('/mainlines')).find(x=>x.id===target).items:await api('/plans/'+target+'/tasks');
      expect(items.find(x=>x.title===addedTitle)).toBeTruthy();
    });
    if(adjustmentId){const s=await api('/ai/sessions/'+adjustmentId);await api('/ai/sessions/'+adjustmentId+'?version='+s.version,undefined,'DELETE',204);}
  }
  await scenario(client.kind+' free manual fallback does not consume quota',async()=>{
    await path(client,'/todos');await expect(client.page.getByRole('heading',{name:'今日待办',exact:true})).toBeVisible();
    await path(client,'/ai?mode=TODO');await expect(client.page.getByRole('heading',{name:'AI 规划助手'})).toBeVisible();
    const quota=await api('/ai/quota');expect(quota.used).toBe(quotaBefore.used);expect(quota.pending).toBe(quotaBefore.pending);
    await expect(client.page.getByRole('button',{name:'发送给 AI',exact:true})).toBeDisabled();
    await client.page.getByText('给外部 AI 的格式说明',{exact:true}).click();await expect(client.page.getByLabel('外部 AI 格式说明',{exact:true})).toHaveValue(/schemaVersion/);
  });
}
async function exerciseCreation(client){
  const quotaBefore=await api('/ai/quota');
  await expect(client.page.locator('.site-header')).toBeVisible({timeout:20000});
  for(const mode of ['PLAN','ROUTE','TODO'])await scenario(client.kind+' '+mode+' external draft import/edit/resume/explicit creation',async()=>{
    const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date());
    const draft={schemaVersion:1,mode,revision:0,title:'AI手机导入合成 '+randomUUID().slice(0,8),startDate:mode==='PLAN'?today:null,endDate:mode==='PLAN'?today:null,restDays:[],stages:mode==='ROUTE'?[{localId:'s1',title:'合成阶段'}]:[],items:[{localId:'i1',title:'AI手机合成 '+randomUUID().slice(0,8),note:'自动化合成内容',dueDate:mode==='ROUTE'?null:today,plannedMinutes:mode==='TODO'?0:15,stageId:mode==='ROUTE'?'s1':null,prerequisites:[]}]};
    await path(client,'/ai?mode='+mode);
    await expect(client.page.getByRole('heading',{name:'AI 规划助手',exact:true})).toBeVisible();
    await client.page.getByRole('button',{name:'新建对话',exact:true}).click();
    await client.page.getByLabel(/^创建方式/).selectOption(mode);
    await client.page.getByText('导入外部 AI 草稿',{exact:true}).click();
    await client.page.getByLabel('草稿 JSON',{exact:true}).fill(JSON.stringify(draft));
    const submit=client.page.getByRole('button',{name:'导入并校验草稿',exact:true});
    await expect(submit).toBeDisabled();
    await client.page.getByRole('checkbox',{name:'同意上方对话处理说明（仅导入不会调用 AI）。',exact:true}).check();
    await submit.click();
    await expect(client.page.getByRole('status').filter({hasText:'草稿已导入'})).toBeVisible();
    const id=new URLSearchParams(client.page.url().split('?')[1]).get('session');expect(id).toMatch(/^[a-f0-9-]{36}$/);
    const title=draft.items[0].title+'－手工调整';
    await client.page.getByLabel('任务标题',{exact:true}).fill(title);
    await expect(client.page.getByRole('button',{name:'确认生成',exact:true})).toBeDisabled();
    await client.page.getByRole('button',{name:'保存草稿修改',exact:true}).click();
    await expect(client.page.getByRole('status').filter({hasText:'草稿已保存'})).toBeVisible();
    await client.page.reload();await expect(client.page.getByLabel('任务标题',{exact:true})).toHaveValue(title);
    await client.page.getByRole('checkbox',{name:/我已核对 1 项内容/}).check();
    await client.page.getByRole('button',{name:'确认生成',exact:true}).click();
    await expect(client.page.locator('.ai-applied')).toContainText('已生成 1 项内容');
    const s=await api('/ai/sessions/'+id);expect(s.applied.count).toBe(1);
    await client.page.getByRole('link',{name:'进入查看',exact:true}).click();
    if(mode==='TODO')await client.page.getByRole('searchbox').fill(title);
    await expect(client.page.getByRole('heading',{name:title,exact:true})).toBeVisible();
    expect(await client.page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
    await capture(client,client.kind+'-'+mode+'-created.png');
    await api('/ai/sessions/'+id+'?version='+s.version,undefined,'DELETE',204);
  });
  const quota=await api('/ai/quota');expect(quota.used).toBe(quotaBefore.used);expect(quota.pending).toBe(quotaBefore.pending);
}
try{
  const auth=await api('/auth/login',{email:'web-acceptance-a-20261012@example.test',password:account.password});token=auth.accessToken;secrets.push(token);expect(auth.user.role).toBe('USER');
  expect((await api('/health')).version).toBe(version);expect((await api('/ai/capabilities')).adjustmentEnabled).toBe(true);
  if(!androidOnly&&!process.argv.includes('--windows-only')){
  browser=await chromium.launch({headless:true});context=await browser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai',viewport:{width:1280,height:900},serviceWorkers:'block'});
  // API session setup for an isolated test context, not UI password automation.
  const response=await context.request.post(origin+'/api/auth/login',{data:{email:'web-acceptance-a-20261012@example.test',password:account.password}});expect(response.status()).toBe(200);
  page=await context.newPage();await page.goto(origin+'/plans');
  if(process.argv.includes('--official'))await exerciseOfficial({root,client:{kind:'web',page},api,invoke,token,origin,report});
  else await exercise({kind:'web',page});
  }
  if(process.argv.includes('--web-only')){report.status='PASS';if(!process.argv.includes('--official'))report.paidCalls=0;}
  else{
  // Use the independent regression identity. Refuse an occupied debugger port.
  const debuggerUrl='http://127.0.0.1:'+(androidOnly?PORTS.androidCdp:PORTS.desktopCdp);
  try{await fetch(debuggerUrl+'/json/list',{signal:AbortSignal.timeout(1000)});throw Error('Regression debugger already running');}catch(e){if(e.message==='Regression debugger already running')throw e;}
  report.sourceSha256=await sourceFingerprint(root);
  if(androidOnly){
    serial=selectDevice(await adb('devices'),serial);
    const metadata=await adb('shell','dumpsys','package',ANDROID_PACKAGE);
    if(!metadata.includes('versionName='+version))throw Error('Rebuild and install the current independent regression APK first');
    const forwards=await adb('forward','--list');
    if(forwards.split(/\r?\n/).some(line=>line.split(/\s+/)[1]==='tcp:'+PORTS.androidCdp))throw Error('Existing Android debugger forward preserved');
    await adb('shell','am','start','-W','-n',ANDROID_PACKAGE+'/cn.zijun701.plan.mobile.MainActivity');
    let pid='';await waitFor(async()=>{pid=await adb('shell','pidof',ANDROID_PACKAGE);return /^\d+$/.test(pid);});
    await waitFor(async()=>(await adb('shell','cat','/proc/net/unix')).includes('@webview_devtools_remote_'+pid));
    await adb('forward','tcp:'+PORTS.androidCdp,'localabstract:webview_devtools_remote_'+pid);forwardOwned=true;
    report.model=await adb('shell','getprop','ro.product.model');
    report.androidRelease=await adb('shell','getprop','ro.build.version.release');
    report.formalPackageVersion=(await adb('shell','dumpsys','package','cn.zijun701.plan.app')).split(/\r?\n/).filter(line=>/versionName=|versionCode=/.test(line)).map(line=>line.trim());
  }else child=spawn(join(root,'.desktop-tools/native-regression/plan-regression.exe'),[],{cwd:root,windowsHide:false,stdio:'ignore'});
  await waitFor(async()=>{try{return(await(await fetch(debuggerUrl+'/json/list')).json()).some(x=>x.type==='page');}catch{return false;}});
  nativeBrowser=await chromium.connectOverCDP(debuggerUrl,{...(androidOnly?{noDefaults:true}:{})});
  await waitFor(async()=>{nativePage=nativeBrowser.contexts().flatMap(x=>x.pages()).find(p=>(androidOnly?/^https?:\/\/localhost\//:/^https?:\/\/tauri\.localhost\//).test(p.url()));return Boolean(nativePage);});
  const info=await invoke('desktop_info');expect(info.version).toBe(version);previousServer=info.serverUrl;
  if(!/^(http:\/\/127\.0\.0\.1:\d+|https:\/\/plan\.zijun701\.cn)\/?$/.test(previousServer))throw Error('Unexpected isolated server; not changed');
  await invoke('desktop_save_server',{serverUrl:origin});
  const login=await invoke('desktop_request',{request:{method:'POST',path:'/api/auth/login',query:null,headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'web-acceptance-a-20261012@example.test',password:account.password})}});expect(login.status).toBe(200);
  // The dedicated regression profile may carry its own previous logout marker.
  // Match rememberIdentity after the REAL authenticated API login; do not touch
  // responses, plans, queues, tokens, credentials or the formal app profile.
  const syntheticUser=JSON.parse(login.body).user;expect(syntheticUser.id).toBe(auth.user.id);
  await nativePage.evaluate(async({scope,user})=>{
    const db=await new Promise((resolve,reject)=>{const r=indexedDB.open('plan-platform-cache',1);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
    await new Promise((resolve,reject)=>{const tx=db.transaction('identities','readwrite');tx.objectStore('identities').put({scope,owner:scope+'|'+user.id,user,expiresAt:Date.now()+86400000});tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);});db.close();
  },{scope:origin,user:syntheticUser});
  const nativeKind=androidOnly?'android':'windows';
  await nativePage.reload();await path({kind:nativeKind,page:nativePage},'/plans');
  if(process.argv.includes('--official'))await exerciseOfficial({root,client:{kind:nativeKind,page:nativePage},api,invoke,token,origin,report});
  else if(process.argv.includes('--creation-only'))await exerciseCreation({kind:nativeKind,page:nativePage});
  else await exercise({kind:nativeKind,page:nativePage});
  report.status='PASS';if(!process.argv.includes('--official'))report.paidCalls=0;
  }
}catch(error){report.error=secrets.reduce((text,value)=>text.replaceAll(value,'[redacted]'),String(error.stack||error));if(nativePage&&!nativePage.isClosed())report.visibleAlerts=await nativePage.getByRole('alert').allTextContents().catch(()=>[]);console.error('AI native/web acceptance failed; details in ignored local report.');process.exitCode=1;}
finally{
  if(nativePage&&!nativePage.isClosed()&&previousServer)try{await invoke('desktop_save_server',{serverUrl:previousServer});}catch{}
  if(nativeBrowser)await nativeBrowser.close();if(child&&child.exitCode===null)child.kill();if(browser)await browser.close();
  if(forwardOwned)await adb('forward','--remove','tcp:'+PORTS.androidCdp).catch(()=>{});
  report.finishedAt=new Date().toISOString();await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);
}
