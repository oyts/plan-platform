// Isolated debug identity only. System security/installation UI is never driven.
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {selectDevice,assertPortsFree} from './native-regression-support.mjs';
const run=promisify(execFile),pkg='cn.zijun701.plan.mobile.updateprobe',formal='cn.zijun701.plan.app',cdp=43182;
const phase=process.argv[2];
if(!['inspect','prepare','download','allow-install','request-install','after'].includes(phase))throw Error('Explicit isolated acceptance phase required');
const pointer=resolve('.desktop-tools/update-experience-probe/android-acceptance.json');
let state;
try{state=JSON.parse(await readFile(pointer,'utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
if(!state){if(!['inspect','prepare'].includes(phase))throw Error('Prepare acceptance first');state={out:resolve('acceptance-results/update-experience/android-probe',new Date().toISOString().replace(/[:.]/g,'-')),title:'NativeUpgrade-update-'+randomUUID().slice(0,8),cases:[],productionChanged:false,paidAiCalls:0,realEmails:0};await mkdir(state.out,{recursive:true});}
const meta=JSON.parse(await readFile('client-update-artifacts/local/stage5-android/metadata.json','utf8'));
expect(meta.packageName).toBe(pkg);let serial,browser,page,forward=false;
async function adb(...args){return(await run(resolve('.desktop-tools/android-sdk/platform-tools/adb.exe'),[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:30000,maxBuffer:3000000})).stdout.trim();}
async function save(){await writeFile(pointer,JSON.stringify(state,null,2));await writeFile(join(state.out,'report.json'),JSON.stringify(state,null,2));}
async function hash(name){const path=(await adb('shell','pm','path',name)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!path)throw Error('Installed package missing');return(await adb('shell','sha256sum',path)).split(/\s+/)[0];}
async function safe(){const focus=(await adb('shell','dumpsys','window')).split(/\r?\n/).filter(s=>s.includes('mCurrentFocus=')&&!s.includes('mCurrentFocus=null'));if(focus.length!==1||!focus[0].includes(pkg+'/'))throw Error('OWNER_RETURN_REQUIRED: unlock/return to isolated update probe; no system UI driven');}
async function attach(){await safe();const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('Probe process missing');await assertPortsFree([cdp]);await adb('forward',`tcp:${cdp}`,`localabstract:webview_devtools_remote_${pid}`);forward=true;browser=await chromium.connectOverCDP(`http://127.0.0.1:${cdp}`,{noDefaults:true});page=browser.contexts().flatMap(c=>c.pages()).find(p=>/^https?:\/\/localhost/.test(p.url()));if(!page)throw Error('Isolated WebView missing');page.setDefaultTimeout(15000);expect(await page.evaluate(()=>window.Capacitor?.isNativePlatform())).toBe(true);}
async function detach(){if(browser)await browser.close();browser=undefined;if(forward)await adb('forward','--remove',`tcp:${cdp}`);forward=false;}
async function reopen(){await safe();await page.waitForTimeout(1500);await safe();await detach();await adb('shell','am','force-stop',pkg);await adb('shell','am','start','-n',pkg+'/cn.zijun701.plan.mobile.MainActivity');await expect.poll(async()=>{try{await safe();return true}catch{return false}},{timeout:15000}).toBe(true);await attach();await expect(page.getByRole('navigation',{name:'手机主导航'})).toBeVisible();}
async function go(path){await safe();await page.evaluate(p=>{location.hash=p},path);}
async function control(command){await run(process.execPath,['scripts/update-experience-probe.mjs','--control',command],{windowsHide:true,timeout:10000});await expect.poll(async()=>JSON.parse(await readFile('.desktop-tools/update-experience-probe/control.json','utf8')).command).toBe(command);await new Promise(r=>setTimeout(r,400));}
function panel(){return page.getByRole('region',{name:'客户端更新',exact:true});}
async function checkUpdate(){await safe();await panel().getByRole('button',{name:'检查新版本',exact:true}).click();await expect(panel().getByRole('button',{name:'检查新版本',exact:true})).toBeEnabled();}
async function queue(){await go('/sync');await expect(page.locator('.sync-summary strong')).toHaveText('1');await expect(page.locator('.sync-summary b')).toHaveText('0');await expect(page.locator('.sync-row')).toHaveCount(1);await expect(page.locator('.sync-method')).toContainText('0 次尝试');return[1,0];}
async function check(name,fn){const row={name,status:'FAIL'};state.cases.push(row);try{await safe();await fn();await safe();row.status='PASS';await page.screenshot({path:join(state.out,`case-${state.cases.length}.png`)});console.log('[PASS] '+name);}finally{await save();}}
try{
 serial=selectDevice(await adb('devices'));state.formalBefore??=await hash(formal);
 const expected=phase==='after'?meta.target:meta.bootstrap;expect(await hash(pkg)).toBe(expected.sha256);
 await attach();
 if(phase==='inspect'){console.log(JSON.stringify({url:page.url(),login:await page.getByLabel('邮箱',{exact:true}).count(),mobileNav:await page.getByRole('navigation',{name:'手机主导航'}).count(),version:expected.version,preferences:await page.evaluate(()=>JSON.parse(localStorage.getItem('plan-platform:update-preferences:v2')??'null'))}));}
 if(phase==='prepare'){
  await control('optional');await control('online');
  if(await page.getByLabel('邮箱',{exact:true}).isVisible()){
   const credentials=JSON.parse(await readFile('acceptance-results/admin-center/2026-10-27T08-08-36-956Z/synthetic-login.json','utf8'));
   await safe();await page.getByLabel('邮箱',{exact:true}).fill(credentials.member.email);await page.getByLabel('密码',{exact:true}).fill(credentials.member.password);await page.getByRole('button',{name:'登录',exact:true}).click();
  }
  await expect(page.getByRole('navigation',{name:'手机主导航'})).toBeVisible();
  await check('真实Java检查、默认关闭自动下载、版本亮点和忽略跨冷重开',async()=>{
   const highlights=page.getByRole('status',{name:'本版更新亮点'});if(await highlights.isVisible())await highlights.getByRole('button',{name:'知道了',exact:true}).click();
   await go('/desktop');await checkUpdate();await expect(panel()).toContainText('可更新至 0.1.26');await expect(panel().getByRole('checkbox')).not.toBeChecked();await panel().getByRole('button',{name:'忽略此版本',exact:true}).click();await expect(panel()).toContainText('已忽略此版本');
   await reopen();await expect(page.getByRole('status',{name:'本版更新亮点'})).toHaveCount(0);await go('/desktop');await checkUpdate();await expect(panel()).toContainText('已忽略此版本');await expect(page.getByLabel('客户端版本提示',{exact:true})).toHaveCount(0);
  });
  await check('真实断网UI新增建立1项合成队列，冷重开和联网不自动提交',async()=>{
   await go('/todos');await expect(page.getByRole('heading',{name:'今日待办',exact:true})).toBeVisible();await go('/sync');await expect(page.locator('.sync-summary strong')).toHaveText('0');
   await go('/todos');await control('offline');await page.getByLabel('快速记录',{exact:true}).fill(state.title);await page.getByRole('button',{name:'记一件事',exact:true}).click();await expect(page.getByRole('heading',{name:state.title,exact:true})).toBeVisible();await queue();await reopen();await queue();await control('online');await queue();
  });
  await check('必更覆盖已忽略目标，保留队列与安全出口',async()=>{await control('required');await go('/desktop');await checkUpdate();await expect(panel()).toContainText('必须更新至 0.1.26');await expect(page.getByLabel('客户端版本提示',{exact:true})).toContainText('当前版本已停止支持');await queue();await control('optional');await go('/desktop');await checkUpdate();await expect(panel()).toContainText('可更新至 0.1.26');});
  await check('篡改SHA清单被真实Java下载器拒装，未进入可安装状态',async()=>{await control('bad-hash');await checkUpdate();await panel().getByRole('button',{name:'下载更新',exact:true}).click();await expect(panel().getByRole('alert')).toContainText('完整性校验失败',{timeout:45000});await expect(panel().getByRole('button',{name:'安装并重新打开',exact:true})).toHaveCount(0);await queue();});
  await check('稍后处理与503冷重开持久退避，队列不受影响',async()=>{await control('optional');await go('/desktop');await checkUpdate();await panel().getByRole('button',{name:'恢复此版本提醒',exact:true}).click();await page.getByLabel('客户端版本提示',{exact:true}).getByRole('button',{name:'稍后处理',exact:true}).click();await reopen();await go('/desktop');await checkUpdate();await expect(panel().getByRole('button',{name:'恢复此版本提醒',exact:true})).toBeVisible();await control('unavailable');await checkUpdate();await expect(panel().getByRole('alert')).toContainText('更新源尚未就绪');const transport=JSON.parse(await readFile('.desktop-tools/update-experience-probe/control.json','utf8')).reportFile;if(typeof transport!=='string'||!resolve(transport).startsWith(resolve('acceptance-results/update-experience/pc-lab')+'/')&&!resolve(transport).startsWith(resolve('acceptance-results/update-experience/pc-lab')+'\\'))throw Error('Current isolated transport report required');const count=async()=>JSON.parse(await readFile(transport,'utf8')).requests.filter(r=>r.kind==='android-manifest').length;const before=await count();await reopen();await go('/desktop');await expect(panel()).toContainText('0.1.25 / 构建 30');expect(await count()).toBe(before);state.backoffCounts=[before,before];await queue();await control('optional');});
  state.prepared=true;state.status='PREINSTALL_ACCEPTED';
 }
 if(phase==='download'){
  if(!state.prepared)throw Error('Scoped preinstall checks required');await control('online');await control('optional');await queue();await go('/desktop');await checkUpdate();await panel().getByRole('button',{name:'下载更新',exact:true}).click();await expect(panel().getByRole('button',{name:'安装并重新打开',exact:true})).toBeEnabled({timeout:45000});await expect(panel().getByRole('checkbox')).not.toBeChecked();state.downloadVerified=true;state.status='DOWNLOADED_VERIFIED_NOT_INSTALLED';console.log('Actual Java download/hash/package/signer validated; owner installation remains.');
 }
 if(phase==='allow-install'){if(!state.downloadVerified)throw Error('Validated download required');await go('/desktop');await panel().getByRole('button',{name:'允许安装更新',exact:true}).click();state.status='OWNER_INSTALL_SOURCE_PERMISSION_REQUIRED';}
 if(phase==='request-install'){
  if(!state.downloadVerified)throw Error('Validated download required');await go('/desktop');page.once('dialog',d=>{if(d.type()==='confirm'&&d.message().startsWith('请先保存正在编辑的内容。安装会'))void d.accept();else void d.dismiss();});await safe();await panel().getByRole('button',{name:'安装并重新打开',exact:true}).click();state.status='OWNER_SYSTEM_INSTALL_REQUIRED';console.log('App confirmation accepted; Android system installation is owner-only.');
 }
 if(phase==='after'){
  if(!state.downloadVerified)throw Error('Actual prior download evidence required');
  await check('真实应用内25/30→26/31后，原合成登录、1/0队列和0次尝试保留',async()=>{await expect(page.getByRole('navigation',{name:'手机主导航'})).toBeVisible();await queue();await go('/todos');await expect(page.getByRole('heading',{name:state.title,exact:true})).toBeVisible();await go('/desktop');await expect(panel()).toContainText('0.1.26 / 构建 31');await expect(panel().getByRole('checkbox')).not.toBeChecked();await checkUpdate();await expect(panel()).toContainText('当前已是最新版本');});
  await check('升级冷重开后原队列和登录保留，安装版本历史准确',async()=>{await reopen();await queue();await go('/help#release-history');await expect(page.getByRole('region',{name:'版本说明与历史'})).toBeVisible();await go('/desktop');await expect(panel()).toContainText('0.1.26 / 构建 31');});state.status='PASS_SCOPED_REAL_UPGRADE';state.actualUpgradeAccepted=true;
 }
 state.formalApkUnchanged=await hash(formal)===state.formalBefore;expect(state.formalApkUnchanged).toBe(true);
}catch(e){state.lastError=e.message;state.lastPhase=phase;console.error('Stopped: '+e.message);process.exitCode=1;}
finally{if(browser)await browser.close().catch(()=>{});if(forward)await adb('forward','--remove',`tcp:${cdp}`).catch(()=>{});state.lastPhase=phase;state.updatedAt=new Date().toISOString();await save();await writeFile(join(state.out,`${phase}-${Date.now()}.json`),JSON.stringify(state,null,2),{flag:'wx'});console.log('Evidence: '+state.out);}
