// Real HTTPS/download acceptance; no real account login or installation.
import assert from 'node:assert/strict';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {sha256,releaseOrigin as origin} from './windows-public-release.mjs';
import {verifyUpdaterSignature} from './formal-release-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const plan=JSON.parse(readFileSync(join(resolve(process.argv[2]),'metadata/release-plan.json'),'utf8'));
assert.match(plan.version,/^\d+\.\d+\.\d+$/);assert.equal(plan.androidPublished,false);
const pub=readFileSync(join(root,'.release-signing/windows-updater.key.pub'),'utf8');
const output=join(root,'acceptance-results/windows-public-cloud',new Date().toISOString().replace(/[:.]/g,'-')+'-'+randomUUID().slice(0,8));mkdirSync(output,{recursive:true});
const report={origin,version:plan.version,scope:'Real public HTTPS, exact signed installer and isolated public browser. No native cross-version install.',cases:[],status:'RUNNING'};
let browser;
async function get(path){return fetch(origin+path,{signal:AbortSignal.timeout(60000)});}
async function check(name,run){await run();report.cases.push({name,status:'PASS'});console.log('PASS '+name);}
try {
  await check('Healthy Spring Boot, closed registration, HTTP redirects to HTTPS',async()=>{
    const health=await (await get('/api/health')).json();assert.equal(health.service,'plan-platform-api');assert.equal(health.version,plan.version);
    const config=await (await get('/api/auth/config')).json();assert.equal(config.registrationEnabled,false);assert.equal(config.invitationEnabled,true);assert.equal(config.todoEnabled,true);
    const http=await fetch('http://plan.zijun701.cn/',{redirect:'manual',signal:AbortSignal.timeout(10000)});assert.equal(http.status,301);assert.equal(http.headers.get('location'),origin+'/');
  });
  await check('Windows optional update and download manifest have no-store',async()=>{
    const ur=await get('/client-updates/windows/latest.json'),dr=await get('/downloads/manifest.json');assert.equal(ur.status,200);assert.equal(dr.status,200);
    assert.match(ur.headers.get('cache-control'),/no-store/);assert.match(dr.headers.get('cache-control'),/no-store/);
    const update=await ur.json(),downloads=await dr.json();assert.equal(update.version,plan.version);assert.equal(update.minimumSupportedVersion,'0.0.0');assert.equal(downloads.android,null);assert.equal(downloads.windows.sha256,plan.windows.sha256);
    const url=new URL(update.platforms['windows-x86_64'].url);assert.equal(url.origin,origin);
    for(const path of [url.pathname,downloads.windows.url]) {
      const response=await get(path);assert.equal(response.status,200);assert.match(response.headers.get('cache-control'),/immutable/);assert.equal(Number(response.headers.get('content-length')),plan.windows.bytes);
      const bytes=Buffer.from(await response.arrayBuffer());assert.equal(bytes.length,plan.windows.bytes);assert.equal(sha256(bytes),plan.windows.sha256);
      verifyUpdaterSignature(bytes,pub,update.platforms['windows-x86_64'].signature);
    }
  });
  await check('Android update and APK are unavailable publicly',async()=>{
    assert.equal((await get('/client-updates/android/latest.json')).status,404);
    assert.equal((await get(`/downloads/${plan.version}/PlanCabin-${plan.version}-release.apk`)).status,404);
  });
  browser=await chromium.launch({headless:true});
  for(const [name,viewport] of [['desktop',{width:1280,height:900}],['mobile-web',{width:390,height:844}]]) {
    await check(name+' official site downloads verified EXE and keeps Android QR disabled',async()=>{
      const context=await browser.newContext({viewport,locale:'zh-CN',timezoneId:'Asia/Shanghai',acceptDownloads:true,serviceWorkers:'block'});
      try {
        const page=await context.newPage(),errors=[];page.on('pageerror',error=>errors.push(error.message));
        await page.goto(origin+'/welcome',{waitUntil:'networkidle'});await expect(page.getByRole('heading',{level:1})).toContainText('把今天安排好');
        const link=page.getByRole('link',{name:'下载 Windows 安装包',exact:true});await expect(link).toBeVisible();
        const pending=page.waitForEvent('download');await link.click();const download=await pending;assert.equal(download.suggestedFilename(),plan.windows.file);assert.equal(sha256(readFileSync(await download.path())),plan.windows.sha256);
        await page.getByRole('button',{name:'查看 Android 发布状态',exact:true}).click();await expect(page.getByRole('dialog')).toContainText('暂不提供公开下载及二维码');await expect(page.getByRole('img',{name:'Android 安装包下载二维码'})).toHaveCount(0);
        await page.screenshot({path:join(output,name+'-download.png'),fullPage:true});await page.getByRole('button',{name:'关闭面板',exact:true}).click();
        await page.getByRole('button',{name:'开始使用网页版',exact:true}).click();await expect(page).toHaveURL(origin+'/login');await expect(page.getByText('当前未开放公开注册。',{exact:true})).toBeVisible();assert.deepEqual(errors,[]);
      } finally {await context.close();}
    });
  }
  report.status='PASS';
} catch(error) {report.status='FAIL';report.failure=error.message;process.exitCode=1;console.error('Public verification failed: '+error.message);}
finally {if(browser)await browser.close();writeFileSync(join(output,'report.json'),JSON.stringify(report,null,2)+'\n');console.log('Report: '+join(output,'report.json'));}
