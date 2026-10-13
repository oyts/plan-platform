// Real production HTTPS + real Rust/Java bridges. Invited synthetic USERs only.
// No local API, intercepted requests, auth bypass, main-app install or trace.
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile, writeFile, mkdir, open, unlink } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { chromium, expect as baseExpect } from '../web/node_modules/@playwright/test/index.mjs';
import { PORTS, ANDROID_PACKAGE, selectDevice, validateManifest, verifyHash, sourceFingerprint, assertPortsFree, reportHtml } from './native-regression-support.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const releaseVersion=JSON.parse(await readFile(join(root,'web/package.json'),'utf8')).version;
const releaseCode=Number((await readFile(join(root,'web/android/app/build.gradle'),'utf8')).match(/versionCode[^\n]*'([0-9]+)'/)?.[1]);
const origin = 'https://plan.zijun701.cn';
const emails = ['web-acceptance-a-20261012@example.test', 'web-acceptance-b-20261012@example.test'];
const artifacts = join(root, '.desktop-tools/native-regression');
const adbPath = join(root, '.desktop-tools/android-sdk/platform-tools/adb.exe');
const exec = promisify(execFile);
const expect = baseExpect.configure({ timeout: 25000 });
const runId = new Date().toISOString().replace(/[:.]/g, '-')+'-'+randomUUID().slice(0,8);
const base = join(root, 'acceptance-results/cloud-native');
const out = join(base, runId);
const lockPath = join(root, 'acceptance-results/native/run.lock');
const report = { title: '正式云端原生登录与三端联测', scope: '真实 HTTPS 云端、受邀请普通 USER 账号、独立 Windows/Android 客户端及网页；仅写入带云端联测标记的合成数据。不使用本地内存数据库，不修改正式应用或管理员数据。', startedAt: new Date().toISOString(), platform: 'Windows + Android + real HTTPS Web', status: 'FAIL', environment: { origin, dataScope: 'Dedicated invited ordinary USER accounts; synthetic records only.' }, cases: [] };
const secrets = [];
const redact = error => secrets.reduce((text, value) => text.replaceAll(value,'[redacted]'), String(error));
const clients = [{kind:'windows',cdp:PORTS.desktopCdp},{kind:'android',cdp:PORTS.androidCdp}];
const state = {};
const prefix = '云端联测-'+runId.slice(11,19).replaceAll('-','')+'-'+randomUUID().slice(0,4);
let serial, lock, webBrowser, webContext, web, users, authA, authB, forwarded = false, androidLaunched = false, mainBefore, reverseBefore;
const apiSessions = [];
const pause = ms => new Promise(resolve => setTimeout(resolve,ms));
async function waitFor(check, label) {
  for (let i=0;i<100;i++) { if(await check()) return; await pause(300); }
  throw new Error('Timeout: '+label);
}
async function adb(...args) { return (await exec(adbPath,serial?['-s',serial,...args]:args,{windowsHide:true,timeout:45000,maxBuffer:2*1024*1024})).stdout.trim(); }
async function mainVersion() { return (await adb('shell','dumpsys','package','cn.zijun701.plan.mobile')).match(/versionCode=\d+[^\r\n]*|versionName=[^\r\n]+/g)?.join('\n')||'not installed'; }
async function api(path, data, token, method = data === undefined?'GET':'POST', status=200) {
  const response = await fetch(origin+'/api'+path,{method,headers:{...(data===undefined?{}:{'Content-Type':'application/json'}),...(token?{Authorization:'Bearer '+token}:{})},body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(20000)});
  expect(response.status,method+' '+path.split('?')[0]).toBe(status);
  return status>=400||status===204?null:response.json();
}
async function apiLogin(email) {
  const response = await fetch(origin+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:users[email].password}),signal:AbortSignal.timeout(20000)});
  expect(response.status).toBe(200); const body=await response.json();
  expect(body.user.role).toBe('USER'); expect(body.user.email).toBe(email);
  secrets.push(body.accessToken);
  const cookie=response.headers.getSetCookie().find(value=>value.startsWith('plan_refresh='))?.split(';')[0];
  if(cookie) { secrets.push(cookie); apiSessions.push(cookie); }
  return body;
}
async function native(client,command,args={}) {
  return client.page.evaluate(async({kind,command,args})=>kind==='android'?(await window.Capacitor.Plugins.PlanNative.execute({command,args})).value:window.__TAURI_INTERNALS__.invoke(command,args),{kind:client.kind,command,args});
}
async function attach(client) {
  if(client.kind==='android') {
    let socket;
    await waitFor(async()=>{
      const pid=await adb('shell','pidof',ANDROID_PACKAGE); if(!/^\d+$/.test(pid)) throw new Error('Regression Android PID is not unique.');
      socket=(await adb('shell','cat','/proc/net/unix')).split(/\r?\n/).map(line=>line.split(/\s+/).at(-1)).find(name=>name===`@webview_devtools_remote_${pid}`); return Boolean(socket);
    },'regression Android WebView');
    await adb('forward',`tcp:${client.cdp}`,`localabstract:${socket.slice(1)}`); forwarded=true;
  }
  await waitFor(async()=>{ if(client.process?.exitCode!==null&&client.process?.exitCode!==undefined) throw new Error('Owned regression EXE exited.'); try { return (await (await fetch(`http://127.0.0.1:${client.cdp}/json/list`)).json()).some(p=>p.type==='page'); } catch { return false; } },'WebView debugger');
  client.browser=await chromium.connectOverCDP(`http://127.0.0.1:${client.cdp}`,{noDefaults:true});
  await waitFor(async()=>{
    const pages=client.browser.contexts().flatMap(c=>c.pages()).filter(p=>(client.kind==='android'?/^https?:\/\/localhost\//:/^https?:\/\/tauri\.localhost\//).test(p.url()));
    if(pages.length>1) throw new Error('Multiple bundled regression pages.'); client.page=pages[0]; return pages.length===1;
  },'bundled page');
  client.page.setDefaultTimeout(25000);
  await expect(client.page.locator('html')).toHaveClass(new RegExp(client.kind==='android'?'android-client':'desktop-client'));
  const info=await native(client,'desktop_info');
  expect(info.version).toBe(releaseVersion);
  if(client.kind==='android') { expect(info.versionCode).toBe(releaseCode); expect(info.debug).toBe(true); }
}
async function launch(client) {
  if(client.kind==='android') { await adb('shell','am','start','-W','-n',`${ANDROID_PACKAGE}/cn.zijun701.plan.mobile.MainActivity`); androidLaunched=true; }
  else {
    const env={...process.env}; for(const key of ['WEBVIEW2_BROWSER_EXECUTABLE_FOLDER','WEBVIEW2_USER_DATA_FOLDER','WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS','WEBVIEW2_PIPE_FOR_SCRIPT_DEBUGGER']) delete env[key];
    client.process=spawn(join(artifacts,'plan-regression.exe'),[],{cwd:artifacts,env,windowsHide:false,stdio:'ignore'});
    client.process.on('error',error=>{client.launchError=error.message;});
  }
  await pause(1200); if(client.launchError) throw new Error(client.launchError); await attach(client);
}
async function stop(client) {
  if(client.browser) { await client.browser.close(); client.browser=null; }
  if(client.kind==='android') {
    if(forwarded) { await adb('forward','--remove',`tcp:${client.cdp}`); forwarded=false; }
    if(androidLaunched) { await adb('shell','am','force-stop',ANDROID_PACKAGE); androidLaunched=false; }
  } else if(client.process&&client.process.exitCode===null) { client.process.kill(); await waitFor(()=>client.process.exitCode!==null||client.process.signalCode!==null,'owned EXE exit'); }
}
async function restart(client) { await stop(client); await launch(client); }
async function route(client,path) { if(client.kind==='web') await client.page.goto(origin+path,{waitUntil:'networkidle'}); else await client.page.evaluate(path=>{location.hash=path;},path); }
async function reload(client,path) { await client.page.reload(); await expect(client.page.locator('.site-header')).toBeVisible(); await route(client,path); }
async function login(client,email) {
  await expect(client.page.getByRole('heading',{name:'登录计划平台',exact:true})).toBeVisible();
  await client.page.getByLabel('邮箱',{exact:true}).fill(email); await client.page.getByLabel('密码',{exact:true}).fill(users[email].password);
  await client.page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(client.page.locator('.site-header')).toBeVisible();
  client.loggedIn=true; client.email=email;
}
async function logout(client) {
  await route(client,'/settings');
  const mobileMore=client.page.getByRole('button',{name:'更多',exact:true});
  if(await mobileMore.isVisible()) await mobileMore.click();
  await client.page.getByRole('button',{name:users[client.email].name+' · 退出',exact:true}).click();
  await expect(client.page.getByRole('heading',{name:'登录计划平台',exact:true})).toBeVisible(); client.loggedIn=false;
}
async function configure(client) {
  client.before=await native(client,'desktop_info');
  if(!/^(http:\/\/127\.0\.0\.1:\d+|https:\/\/plan\.zijun701\.cn)\/?$/.test(client.before.serverUrl)) throw new Error('Unexpected regression server; no existing settings changed.');
  await route(client,'/desktop');
  const input=client.page.getByLabel('服务器根地址'); await expect(input).toHaveValue(client.before.serverUrl);
  await input.fill(origin); await client.page.getByRole('button',{name:'测试连接',exact:true}).click();
  await expect(client.page.getByRole('status').filter({hasText:/^连接成功，/})).toBeVisible();
  await client.page.getByRole('button',{name:'保存并重新连接',exact:true}).click(); client.changed=true;
  await login(client,emails[0]); expect((await native(client,'desktop_info')).serverUrl).toBe(origin);
}
async function shot(row) {
  for(const client of [...clients,...(web?[web]:[])]) {
    if(!client.page||client.page.isClosed()) continue;
    if(await client.page.getByLabel('密码',{exact:true}).count()) continue;
    const name=`${String(report.cases.length).padStart(2,'0')}-${client.kind}-${row.status.toLowerCase()}.png`;
    try { await client.page.screenshot({path:join(out,name),timeout:5000}); row.screenshots.push(runId+'/'+name); }
    catch {
      // Android WebView may not implement CDP screenshot capture. Only capture
      // the physical display when our isolated synthetic app is foreground.
      if(client.kind==='android') try {
        const activity=await adb('shell','dumpsys','activity','activities');
        if(!activity.split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&line.includes(ANDROID_PACKAGE+'/'))) continue;
        const {stdout}=await exec(adbPath,['-s',serial,'exec-out','screencap','-p'],{windowsHide:true,encoding:'buffer',timeout:10000,maxBuffer:8*1024*1024});
        await writeFile(join(out,name),stdout); row.screenshots.push(runId+'/'+name);
      } catch { /* Never hide the primary assertion. */ }
    }
  }
}
async function scenario(name,action) {
  const row={name,status:'PASS',durationMs:0,screenshots:[]}; report.cases.push(row); const begin=Date.now();
  try { await action(); } catch(error) { row.status='FAIL'; row.error=redact(error.stack||error.message); throw error; }
  finally { row.durationMs=Date.now()-begin; await shot(row); console.log(`[${row.status}] ${name}`); }
}
async function editTask(client,title,next) {
  const row=client.page.locator('.task-row').filter({has:client.page.getByRole('heading',{name:title,exact:true})});
  await row.getByRole('button',{name:'编辑',exact:true}).click(); const panel=client.page.getByRole('dialog');
  await panel.getByLabel('任务标题',{exact:true}).fill(next); await panel.getByRole('button',{name:'保存修改',exact:true}).click();
  await expect(panel).not.toBeVisible(); await expect(client.page.getByRole('heading',{name:next,exact:true})).toBeVisible();
}
async function run() {
  users=JSON.parse(await readFile(join(root,'.desktop-tools/public-web/private/accounts.json'),'utf8'));
  expect(Object.keys(users).sort()).toEqual([...emails].sort());
  for(const email of emails) { expect(users[email].registered).toBe(true); expect(typeof users[email].password).toBe('string'); secrets.push(users[email].password); }
  const manifest=validateManifest(JSON.parse(await readFile(join(artifacts,'build.json'),'utf8')));
  expect(await sourceFingerprint(root)).toBe(manifest.sourceSha256);
  await verifyHash(join(artifacts,'plan-regression.exe'),manifest.desktopSha256);
  await verifyHash(join(artifacts,manifest.apkName),manifest.apkSha256);
  await assertPortsFree([PORTS.desktopCdp,PORTS.androidCdp]);
  serial=selectDevice(await adb('devices'),'');
  if((await adb('forward','--list')).split(/\r?\n/).some(line=>[PORTS.desktopCdp,PORTS.androidCdp].some(port=>line.split(/\s+/)[1]===`tcp:${port}`))) throw new Error('Existing debugger mapping not replaced.');
  let pid=''; try { pid=await adb('shell','pidof',ANDROID_PACKAGE); } catch(error) { if(error.code!==1) throw error; }
  if(pid) throw new Error('Close the regression APK first; existing process not stopped.');
  // Reject a pre-existing regression EXE even when its debugging port is not ready.
  const processes=(await exec('powershell',['-NoProfile','-Command',"Get-Process -Name plan-regression -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id; exit 0"],{windowsHide:true})).stdout.trim();
  if(processes) throw new Error('Close the regression EXE first; existing process not stopped.');
  mainBefore=await mainVersion(); reverseBefore=await adb('reverse','--list');
  report.environment.android={model:await adb('shell','getprop','ro.product.model'),release:await adb('shell','getprop','ro.build.version.release'),mainVersionBefore:mainBefore};
  await scenario(`正式 HTTPS ${releaseVersion}、公开注册关闭与两个普通云端账号权限`,async()=>{
    const config=await api('/auth/config'); expect(config.serverVersion).toBe(releaseVersion); expect(config.registrationEnabled).toBe(false);
    expect((await api('/health')).service).toBe('plan-platform-api');
    authA=await apiLogin(emails[0]); authB=await apiLogin(emails[1]);
    await api('/admin/invitations',undefined,authA.accessToken,'GET',403);
    state.today=(await api('/dashboard/today',undefined,authA.accessToken)).date;
  });
  await scenario('Windows Rust 与 Android Java 原生登录正式云端，无 USB 业务转发',async()=>{
    for(const client of clients) { await launch(client); await configure(client); }
    expect(await adb('reverse','--list')).toBe(reverseBefore);
    webBrowser=await chromium.launch({executablePath:join(root,'.desktop-tools/playwright/chromium-1243/chrome-win64/chrome.exe'),headless:true});
    webContext=await webBrowser.newContext({locale:'zh-CN',timezoneId:'Asia/Shanghai',serviceWorkers:'block'});
    web={kind:'web',page:await webContext.newPage()}; await route(web,'/login'); await login(web,emails[0]);
    expect((await webContext.cookies()).filter(cookie=>cookie.name==='plan_refresh').every(cookie=>cookie.secure&&cookie.httpOnly)).toBe(true);
  });
  const pc=clients[0], phone=clients[1];
  await scenario('PC 创建云端周期计划，手机与网页可见并交叉修改任务',async()=>{
    await pc.page.getByLabel('工作模式').selectOption('plan'); await route(pc,'/plans');
    await pc.page.getByLabel('计划名称',{exact:true}).fill(prefix+'周期计划');
    await pc.page.getByLabel('开始日期',{exact:true}).fill(state.today); await pc.page.getByLabel('结束日期',{exact:true}).fill(state.today);
    await pc.page.getByRole('button',{name:'保存计划',exact:true}).click(); await expect(pc.page.getByRole('heading',{name:prefix+'周期计划',exact:true})).toBeVisible();
    state.plan=(await api('/plans',undefined,authA.accessToken)).find(plan=>plan.name===prefix+'周期计划'); expect(state.plan.restDays).toEqual([]);
    await pc.page.getByRole('button',{name:'添加任务',exact:true}).click(); const panel=pc.page.getByRole('dialog');
    await panel.getByLabel('任务标题',{exact:true}).fill(prefix+'任务'); await panel.getByRole('button',{name:'保存任务',exact:true}).click(); await expect(panel).not.toBeVisible();
    for(const client of [phone,web]) { await route(client,`/plans/${state.plan.id}`); await expect(client.page.getByRole('heading',{name:prefix+'任务',exact:true})).toBeVisible(); }
    await editTask(phone,prefix+'任务',prefix+'手机修改');
    await reload(web,`/plans/${state.plan.id}`); await expect(web.page.getByRole('heading',{name:prefix+'手机修改',exact:true})).toBeVisible();
    await editTask(web,prefix+'手机修改',prefix+'网页修改');
    for(const client of clients) { await reload(client,`/plans/${state.plan.id}`); await expect(client.page.getByRole('heading',{name:prefix+'网页修改',exact:true})).toBeVisible(); }
  });
  await scenario('原生休息日配置云端保存，三端刷新一致且任务仍在',async()=>{
    await pc.page.getByRole('button',{name:'编辑计划设置',exact:true}).click();
    await pc.page.getByRole('form',{name:'编辑计划设置',exact:true}).getByRole('checkbox',{name:'星期日',exact:true}).check();
    await pc.page.getByRole('button',{name:'保存计划设置',exact:true}).click();
    for(const client of [pc,phone,web]) { await reload(client,`/plans/${state.plan.id}`); await expect(client.page.getByText('每周 星期日 休息',{exact:true})).toBeVisible(); await expect(client.page.getByRole('heading',{name:prefix+'网页修改',exact:true})).toBeVisible(); }
    expect((await api('/plans',undefined,authA.accessToken)).find(p=>p.id===state.plan.id).restDays).toEqual([7]);
  });
  await scenario('手机创建目标路线阶段和前置步骤，PC 完成后网页与手机解锁',async()=>{
    await phone.page.getByLabel('工作模式').selectOption('route'); await route(phone,'/mainlines');
    await expect(phone.page.locator('.line-list')).toBeVisible();
    // Wait for the populated route list before opening the creation details.
    await expect(phone.page.locator('.line-list > button').first()).toBeVisible();
    if(!await phone.page.getByLabel('路线名称',{exact:true}).isVisible()) await phone.page.locator('.route-create summary').click();
    await phone.page.getByLabel('路线名称',{exact:true}).fill(prefix+'目标路线'); await phone.page.getByRole('button',{name:'新建路线',exact:true}).click();
    await expect(phone.page.getByRole('heading',{name:prefix+'目标路线',exact:true})).toBeVisible();
    state.line=(await api('/mainlines',undefined,authA.accessToken)).find(line=>line.name===prefix+'目标路线'); expect(state.line.executionMode).toBe('FLEXIBLE');
    await phone.page.getByRole('button',{name:'管理阶段',exact:true}).click(); let panel=phone.page.getByRole('dialog');
    await panel.getByLabel('阶段名称',{exact:true}).fill('云端基础阶段'); await panel.getByRole('button',{name:'添加阶段',exact:true}).click();
    await expect(panel.locator('.route-stage-row')).toContainText('云端基础阶段'); await panel.getByRole('button',{name:'关闭面板',exact:true}).click();
    const stage=(await api('/mainlines',undefined,authA.accessToken)).find(line=>line.id===state.line.id).stages[0];
    for(const title of ['第一步','第二步']) {
      await phone.page.getByRole('button',{name:'添加步骤',exact:true}).click(); panel=phone.page.getByRole('dialog');
      await panel.getByLabel('步骤标题',{exact:true}).fill(prefix+title); await panel.getByRole('combobox',{name:/^所属阶段/}).selectOption(stage.id);
      await panel.getByLabel('验收项（每行一项）',{exact:true}).fill('');
      if(title==='第二步') { await panel.getByRole('combobox',{name:/^前置条件/}).selectOption('EXPLICIT'); await panel.getByLabel(prefix+'第一步',{exact:true}).check(); }
      await panel.getByRole('button',{name:'保存步骤',exact:true}).click(); await expect(panel).not.toBeVisible();
    }
    for(const client of [pc,web]) { await route(client,`/mainlines?line=${state.line.id}`); await expect(client.page.locator('.mainline-step').filter({hasText:prefix+'第二步'}).getByRole('button',{name:'开始',exact:true})).toBeDisabled(); }
    await pc.page.locator('.mainline-step').filter({has:pc.page.getByRole('heading',{name:prefix+'第一步',exact:true})}).getByRole('button',{name:'完成',exact:true}).click();
    for(const client of [pc,phone,web]) { await reload(client,`/mainlines?line=${state.line.id}`); await expect(client.page.locator('.mainline-step').filter({hasText:prefix+'第二步'}).getByRole('button',{name:'开始',exact:true})).toBeEnabled(); }
  });
  await scenario('手机新增今日待办、PC 编辑、网页完成，三端刷新一致',async()=>{
    await phone.page.getByLabel('工作模式').selectOption('todo'); await route(phone,'/todos');
    await phone.page.getByLabel('查看日期',{exact:true}).fill(state.today);
    await phone.page.getByLabel('搜索',{exact:true}).fill(prefix+'待办');
    await phone.page.getByLabel('快速记录',{exact:true}).fill(prefix+'待办'); await phone.page.getByRole('button',{name:'记一件事',exact:true}).click();
    await expect(phone.page.getByRole('heading',{name:prefix+'待办',exact:true})).toBeVisible();
    await route(pc,'/todos'); await pc.page.getByLabel('搜索',{exact:true}).fill(prefix+'待办');
    await pc.page.locator('.todo-row').getByRole('button',{name:'编辑',exact:true}).click(); const panel=pc.page.getByRole('dialog');
    await panel.getByLabel('待办标题',{exact:true}).fill(prefix+'待办PC版'); await panel.getByLabel('备注',{exact:true}).fill('真实云端原生互通');
    await panel.getByRole('button',{name:'保存待办',exact:true}).click(); await expect(panel).not.toBeVisible();
    await route(web,'/todos'); await web.page.getByLabel('搜索',{exact:true}).fill(prefix+'待办PC版');
    await web.page.locator('.todo-row').getByRole('checkbox').check(); await expect(web.page.locator('.todo-row')).toHaveCount(0);
    for(const client of [pc,phone,web]) { await reload(client,'/todos'); await client.page.getByRole('combobox',{name:/^状态/}).selectOption('DONE'); await client.page.getByLabel('搜索',{exact:true}).fill(prefix+'待办PC版'); await expect(client.page.locator('.todo-row').getByRole('checkbox')).toBeChecked(); }
    state.todo=(await api(`/todos?from=${state.today}&to=${state.today}`,undefined,authA.accessToken)).find(todo=>todo.title===prefix+'待办PC版'); expect(state.todo.done).toBe(true);
  });
  await scenario('正式云端原生会话：终止重开后免重新登录、三模式数据仍在',async()=>{
    for(const client of clients) {
      await restart(client); await expect(client.page.locator('.site-header')).toBeVisible();
      expect((await native(client,'desktop_info')).serverUrl).toBe(origin);
      await route(client,`/plans/${state.plan.id}`); await expect(client.page.getByRole('heading',{name:prefix+'网页修改',exact:true})).toBeVisible();
      await route(client,`/mainlines?line=${state.line.id}`); await expect(client.page.getByRole('heading',{name:prefix+'第二步',exact:true})).toBeVisible();
      await route(client,'/todos'); await client.page.getByRole('combobox',{name:/^状态/}).selectOption('DONE'); await client.page.getByLabel('搜索',{exact:true}).fill(prefix+'待办PC版'); await expect(client.page.locator('.todo-row').getByRole('checkbox')).toBeChecked();
    }
  });
  await scenario('PC 新写入与手机过期版本冲突：不覆盖云端新值，勾选回退',async()=>{
    await pc.page.locator('.todo-row').getByRole('button',{name:'编辑',exact:true}).click(); const panel=pc.page.getByRole('dialog');
    await panel.getByLabel('待办标题',{exact:true}).fill(prefix+'冲突PC新值'); await panel.getByRole('button',{name:'保存待办',exact:true}).click(); await expect(panel).not.toBeVisible();
    await phone.page.locator('.todo-row').getByRole('checkbox').uncheck();
    await expect(phone.page.getByRole('alert')).toContainText('其他设备修改'); await expect(phone.page.locator('.todo-row').getByRole('checkbox')).toBeChecked();
    const saved=(await api(`/todos?from=${state.today}&to=${state.today}`,undefined,authA.accessToken)).find(todo=>todo.id===state.todo.id);
    expect(saved.title).toBe(prefix+'冲突PC新值'); expect(saved.done).toBe(true); expect(saved.version).toBeGreaterThan(state.todo.version);
  });
  await scenario('原生退出重开不恢复原账号；切换另一个 USER 后云端数据隔离',async()=>{
    for(const client of clients) {
      await logout(client); await restart(client); await expect(client.page.getByRole('heading',{name:'登录计划平台',exact:true})).toBeVisible();
      await login(client,emails[1]); await route(client,'/plans'); await expect(client.page.getByText(prefix+'周期计划',{exact:true})).toHaveCount(0);
      await route(client,'/mainlines'); await expect(client.page.getByText(prefix+'目标路线',{exact:true})).toHaveCount(0);
      await route(client,'/todos'); await client.page.getByRole('combobox',{name:/^状态/}).selectOption('ALL'); await client.page.getByLabel('搜索',{exact:true}).fill(prefix); await expect(client.page.locator('.todo-row')).toHaveCount(0);
      await route(client,'/sync'); await expect(client.page.getByText('队列为空',{exact:true})).toBeVisible();
    }
    await api(`/plans/${state.plan.id}/tasks`,undefined,authB.accessToken,'GET',404);
    await api(`/todos/${state.todo.id}`,{title:'拒绝越权',dueDate:state.today,done:false,deleted:false,version:state.todo.version},authB.accessToken,'PUT',404);
    await reload(web,`/plans/${state.plan.id}`); await expect(web.page.getByRole('heading',{name:prefix+'网页修改',exact:true})).toBeVisible();
  });
  report.status='PASS';
}
await mkdir(out,{recursive:true});
try { await mkdir(join(root,'acceptance-results/native'),{recursive:true}); lock=await open(lockPath,'wx'); await lock.writeFile(String(process.pid)); await run(); }
catch(error) { report.error=redact(error.stack||error.message); console.log(redact(error.message)); process.exitCode=1; }
finally {
  const errors=[]; const attempt=async action=>{try{await action();}catch(error){errors.push(redact(error.message));}};
  for(const client of [...clients,...(web?[web]:[])]) {
    if(client.loggedIn&&client.page&&!client.page.isClosed()) await attempt(()=>logout(client));
    if(client.kind!=='web'&&client.changed&&client.page&&!client.page.isClosed()) await attempt(async()=>{
      // Restore only our isolated test client's prior validated setting. The
      // old local test API may no longer exist, so restoration is a real native
      // configuration command rather than an untestable UI connectivity probe.
      await native(client,'desktop_save_server',{serverUrl:client.before.serverUrl});
      expect((await native(client,'desktop_info')).serverUrl).toBe(client.before.serverUrl);
      client.changed=false;
    });
    if(client.kind!=='web') await attempt(()=>stop(client));
  }
  for(const cookie of apiSessions) await attempt(async()=>{const response=await fetch(origin+'/api/auth/logout',{method:'POST',headers:{Cookie:cookie},signal:AbortSignal.timeout(15000)});expect(response.ok).toBe(true);});
  if(webBrowser) await attempt(()=>webBrowser.close());
  if(forwarded) await attempt(async()=>{await adb('forward','--remove',`tcp:${PORTS.androidCdp}`);forwarded=false;});
  if(mainBefore!==undefined) await attempt(async()=>{report.environment.android.mainVersionAfter=await mainVersion();expect(report.environment.android.mainVersionAfter).toBe(mainBefore);expect(await adb('reverse','--list')).toBe(reverseBefore);});
  await attempt(()=>assertPortsFree([PORTS.desktopCdp,PORTS.androidCdp]));
  if(errors.length){report.status='FAIL';report.cleanupError=errors.join('; ');report.error=[report.error,report.cleanupError].filter(Boolean).join('\n');process.exitCode=1;}
  if(lock){await lock.close();await unlink(lockPath);}
  await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));await writeFile(join(base,'latest.html'),reportHtml(report));
  console.log(`Report: ${join(base,'latest.html')}`);
}
