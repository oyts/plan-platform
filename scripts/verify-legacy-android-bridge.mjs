import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {sha256} from './invited-android-release.mjs';
import {ORIGIN} from './legacy-android-bridge.mjs';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const plan=JSON.parse(readFileSync(join(resolve(process.argv[2]),'bridge-plan.json'),'utf8'));
assert.equal(plan.scope,'TEMPORARY_UNAUTHENTICATED_LEGACY_ANDROID_MIGRATION');
assert.ok(Math.floor(Date.now()/1000)<plan.expires,'Window expired; never extend implicitly');
const output=join(root,'acceptance-results/legacy-android',new Date().toISOString().replace(/[:.]/g,'-')+'-'+randomUUID().slice(0,8));mkdirSync(output,{recursive:true});
const report={status:'RUNNING',version:plan.version,run:plan.run,expiresAtUtc:plan.expiresAtUtc,scope:'Real unauthenticated legacy HTTPS contract and exact APK bytes; NOT friend-device installation',cases:[]};
async function get(path,options={}){return fetch(ORIGIN+path,{...options,redirect:'error',signal:AbortSignal.timeout(60000)});}
async function check(name,run){await run();report.cases.push({name,status:'PASS'});console.log('PASS '+name);}
try {
  let manifest;
  await check('Old anonymous manifest is optional 0.1.7, no-store, with exact expiration',async()=>{
    const response=await get('/client-updates/android/latest.json');assert.equal(response.status,200);assert.match(response.headers.get('cache-control'),/no-store/);
    manifest=await response.json();assert.equal(manifest.version,plan.version);assert.equal(manifest.versionCode,plan.versionCode);assert.equal(manifest.minimumSupportedVersionCode,1);
    assert.equal(manifest.packageName,'cn.zijun701.plan.app');assert.equal(manifest.migrationExpiresAt,plan.expiresAtUtc);assert.equal(manifest.sha256,plan.apkSha256);assert.equal(manifest.size,plan.apkBytes);assert.equal(manifest.url,ORIGIN+plan.apkUri);
    assert.ok(manifest.versionCode>8);assert.match(manifest.version,/^\d+\.\d+\.\d+$/);assert.ok(!/token|password|cookie/i.test(JSON.stringify(manifest)));
  });
  await check('Old no-credential downloader obtains identical signed APK over HTTPS',async()=>{
    const response=await get(plan.apkUri,{headers:{'Accept-Encoding':'identity'}});assert.equal(response.status,200);assert.match(response.headers.get('cache-control'),/no-store/);
    assert.equal(Number(response.headers.get('content-length')),plan.apkBytes);
    const bytes=Buffer.from(await response.arrayBuffer());assert.equal(bytes.length,plan.apkBytes);assert.equal(sha256(bytes),plan.apkSha256);
  });
  await check('Bridge scope cannot expose private metadata or alternative APK paths',async()=>{
    for(const path of ['/client-updates/android/'+plan.run+'/bridge-plan.json','/client-updates/android/'+plan.run+'/nginx.actual-before.conf','/client-updates/android/'+plan.run+'/publish.sh','/client-updates/android/'+plan.run+'/','/downloads/'+plan.version+'/PlanCabin-'+plan.version+'-release.apk'])assert.equal((await get(path)).status,404);
  });
  await check('Protected new API remains authenticated; public signup and website APK remain closed',async()=>{
    for(const path of ['/api/client-updates/android/latest','/api/client-updates/android/releases/'+plan.versionCode+'/apk','/api/plans','/api/mainlines','/api/todos'])assert.equal((await get(path)).status,401);
    const config=await(await get('/api/auth/config')).json();assert.equal(config.registrationEnabled,false);assert.equal(config.invitationEnabled,true);
    const downloads=await(await get('/downloads/manifest.json')).json();assert.equal(downloads.android,null);
    const windows=await(await get('/client-updates/windows/latest.json')).json();assert.equal(windows.version,plan.version);
    const health=await(await get('/api/health')).json();assert.equal(health.service,'plan-platform-api');assert.equal(health.version,plan.version);
  });
  await check('Desktop and mobile website still show Windows download, no Android APK link or QR',async()=>{
    const browser=await chromium.launch({headless:true});
    try {
      for(const viewport of [{width:1280,height:900},{width:390,height:844}]) {
        const context=await browser.newContext({viewport,locale:'zh-CN',serviceWorkers:'block'});
        try {
          const page=await context.newPage();await page.goto(ORIGIN+'/welcome',{waitUntil:'networkidle'});
          await expect(page.getByRole('link',{name:'下载 Windows 安装包',exact:true})).toBeVisible();
          assert.equal(await page.locator('a[href*=".apk"]').count(),0);
          await page.getByRole('button',{name:'查看 Android 发布状态',exact:true}).click();
          await expect(page.getByRole('img',{name:'Android 安装包下载二维码'})).toHaveCount(0);
          await expect(page.getByRole('dialog')).toContainText('正式签名包尚未发布');
        } finally {await context.close();}
      }
    } finally {await browser.close();}
  });
  report.status='PASS';
} catch(error){report.status='FAIL';report.failure=error.message;process.exitCode=1;console.error('Legacy bridge verification failed: '+error.message);}
finally {writeFileSync(join(output,'report.json'),JSON.stringify(report,null,2)+'\n');console.log('Report: '+join(output,'report.json'));}
