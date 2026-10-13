// Dedicated ordinary synthetic accounts only. No administrator credentials.
import assert from 'node:assert/strict';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {sha256,PACKAGE} from './invited-android-release.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const stage=resolve(process.argv[2]||'');
const expected=JSON.parse(readFileSync(join(stage,'current.json'),'utf8'));
const accounts=JSON.parse(readFileSync(join(root,'.desktop-tools/public-web/private/accounts.json'),'utf8'));
const emails=['web-acceptance-a-20261012@example.test','web-acceptance-b-20261012@example.test'];
assert.deepEqual(Object.keys(accounts).sort(),[...emails].sort());
const origin='https://plan.zijun701.cn',latest='/api/client-updates/android/latest';
const apkPath=`/api/client-updates/android/releases/${expected.versionCode}/apk`;
const report={version:expected.version,versionCode:expected.versionCode,scope:'Authenticated HTTPS update endpoint and exact APK; not an installation test',cases:[],status:'RUNNING'};
const output=join(root,'acceptance-results/invited-android',new Date().toISOString().replace(/[:.]/g,'-')+'-'+randomUUID().slice(0,8));
mkdirSync(output,{recursive:true});
async function request(path,options={}){return fetch(origin+path,{...options,redirect:'error',signal:AbortSignal.timeout(60000)});}
async function check(name,run){await run();report.cases.push({name,status:'PASS'});console.log('PASS '+name);}
try {
  await check('Unauthenticated and invalid credentials rejected for manifest AND APK',async()=>{
    for(const path of [latest,apkPath])for(const headers of [{},{Authorization:'Bearer invalid.invalid.invalid'}])assert.equal((await request(path,{headers})).status,401);
  });
  await check('Public APK, legacy static Android feed and website QR remain unavailable',async()=>{
    assert.equal((await request('/client-updates/android/latest.json')).status,404);
    assert.equal((await request(`/downloads/${expected.version}/PlanCabin-${expected.version}-release.apk`)).status,404);
    assert.equal((await(await request('/downloads/manifest.json')).json()).android,null);
    const config=await(await request('/api/auth/config')).json();assert.equal(config.registrationEnabled,false);assert.equal(config.invitationEnabled,true);
  });
  for(let index=0;index<emails.length;index++)await check(`Invited USER ${index+1}: owner-bound private manifest and verified APK`,async()=>{
    const data=accounts[emails[index]];assert.equal(data.registered,true);
    let cookie='';
    try {
      const response=await request('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:emails[index],password:data.password})});
      assert.equal(response.status,200);cookie=(response.headers.get('set-cookie')||'').split(';')[0];
      const session=await response.json();assert.equal(session.user.role,'USER');
      const headers={Authorization:'Bearer '+session.accessToken};
      const feed=await request(latest,{headers});assert.equal(feed.status,200);assert.match(feed.headers.get('cache-control'),/private/);assert.match(feed.headers.get('cache-control'),/no-store/);
      const manifest=await feed.json();assert.equal(manifest.accountId,session.user.id);assert.equal(manifest.packageName,PACKAGE);
      for(const key of ['version','versionCode','minimumSupportedVersionCode','size','sha256'])assert.equal(manifest[key],expected[key]);
      assert.equal(manifest.url,origin+apkPath);
      const download=await request(apkPath,{headers});assert.equal(download.status,200);assert.match(download.headers.get('cache-control'),/no-store/);
      assert.equal(Number(download.headers.get('content-length')),expected.size);
      const bytes=Buffer.from(await download.arrayBuffer());assert.equal(bytes.length,expected.size);assert.equal(sha256(bytes),expected.sha256);
      assert.equal((await request('/api/client-updates/android/releases/2147483647/apk',{headers})).status,404);
    } finally {if(cookie)await request('/api/auth/logout',{method:'POST',headers:{Cookie:cookie}});}
  });
  report.status='PASS';
} catch {report.status='FAIL';report.failure='A protected update check failed; inspect endpoint/configuration without logging credentials.';process.exitCode=1;console.error(report.failure);}
finally {writeFileSync(join(output,'report.json'),JSON.stringify(report,null,2)+'\n');console.log('Report: '+join(output,'report.json'));}
