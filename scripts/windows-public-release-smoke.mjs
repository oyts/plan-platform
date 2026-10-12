// Local packaging/browser test only. No production requests, login or installation.
import assert from 'node:assert/strict';
import {readFileSync,readdirSync,statSync,realpathSync,mkdirSync,writeFileSync} from 'node:fs';
import {join,resolve,relative,isAbsolute,sep,extname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createServer} from 'node:http';
import {once} from 'node:events';
import {randomUUID} from 'node:crypto';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
import {verifyUpdaterSignature} from './formal-release-support.mjs';
import {sha256} from './windows-public-release.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const stage=realpathSync(resolve(process.argv[2]||''));
const production=realpathSync(join(root,'production-artifacts'));
const stageRelative=relative(production,stage);
if(!stageRelative||stageRelative.startsWith('..')||isAbsolute(stageRelative)) throw new Error('Supply a local prepared publication under production-artifacts.');
const plan=JSON.parse(readFileSync(join(stage,'metadata/release-plan.json'),'utf8'));
assert.equal(plan.status,'PREPARED_NOT_PUBLISHED');assert.equal(plan.publicReleaseApproved,false);assert.equal(plan.androidPublished,false);
const publicRoot=realpathSync(join(stage,'public')),distRoot=realpathSync(join(root,'web/dist'));
const publicKey=readFileSync(join(root,'.release-signing/windows-updater.key.pub'),'utf8');
const files=new Map();
function collect(base,publication=false) {
  for(const path of readdirSync(base,{recursive:true})) {
    const full=join(base,path);if(!statSync(full).isFile())continue;
    const actual=realpathSync(full),part=relative(base,actual);
    if(part.startsWith('..')||isAbsolute(part))throw new Error('Static test file escapes its root.');
    const url='/'+part.split(sep).join('/');
    if(publication&&!/^(\/client-updates\/windows\/latest\.json|\/client-updates\/windows\/releases\/\d+\.\d+\.\d+\/Plan Cabin_\d+\.\d+\.\d+_x64-setup\.exe(?:\.sig)?|\/downloads\/manifest\.json|\/downloads\/\d+\.\d+\.\d+\/Plan Cabin_\d+\.\d+\.\d+_x64-setup\.exe)$/.test(url))throw new Error('Non-Windows or private file in public staging tree.');
    files.set(url,readFileSync(actual));
  }
}
collect(distRoot);collect(publicRoot,true);
const expectedPublicFiles=readdirSync(publicRoot,{recursive:true}).filter(p=>statSync(join(publicRoot,p)).isFile());assert.equal(expectedPublicFiles.length,5);
const types={'.html':'text/html; charset=utf-8','.js':'application/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json; charset=utf-8','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'};
const authFixture={apiVersion:1,registrationEnabled:false,invitationEnabled:true,passwordResetEnabled:false,emailVerificationEnabled:false,planRestDaysEnabled:true,todoEnabled:true};
const server=createServer((req,res)=>{
  if(!['GET','HEAD','POST'].includes(req.method)){res.writeHead(405).end();return;}
  const pathname=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
  if(pathname==='/api/auth/config'&&req.method==='GET'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify(authFixture));return;}
  if(pathname.startsWith('/api/')){res.writeHead(401,{'Content-Type':'application/json'}).end('{"message":"Isolated browser fixture; no real account"}');return;}
  if(req.method==='POST'){res.writeHead(405).end();return;}
  const bytes=files.get(pathname)||(['/','/welcome','/login'].includes(pathname)?files.get('/index.html'):null);
  if(!bytes){res.writeHead(404).end();return;}
  const type=extname(pathname)||'.html';res.setHeader('Content-Type',types[type]||'application/octet-stream');res.setHeader('Content-Length',bytes.length);
  res.setHeader('Cache-Control',pathname.endsWith('/latest.json')||pathname==='/downloads/manifest.json'?'no-store':pathname.includes('/releases/')||type==='.exe'?'public, max-age=31536000, immutable':'no-cache');
  if(type==='.exe')res.setHeader('Content-Disposition',"attachment; filename*=UTF-8''"+encodeURIComponent(plan.windows.file));
  res.end(req.method==='HEAD'?undefined:bytes);
});
server.listen(0,'127.0.0.1');await once(server,'listening');
const origin=`http://127.0.0.1:${server.address().port}`;
const output=join(root,'acceptance-results/windows-public',new Date().toISOString().replace(/[:.]/g,'-')+'-'+randomUUID().slice(0,8));mkdirSync(output,{recursive:true});
const report={scope:'Local built website + actual signed staged installer; synthetic closed-auth fixture. Not production HTTPS or native upgrade acceptance.',version:plan.version,status:'RUNNING',cases:[]};
let browser;
async function check(name,run){await run();report.cases.push({name,status:'PASS'});console.log('PASS '+name);}
try {
  await check('Windows manifest is optional and Android download remains disabled',async()=>{
    const updateResponse=await fetch(origin+'/client-updates/windows/latest.json'),downloadResponse=await fetch(origin+'/downloads/manifest.json');
    assert.equal(updateResponse.headers.get('cache-control'),'no-store');assert.equal(downloadResponse.headers.get('cache-control'),'no-store');
    const update=await updateResponse.json(),downloads=await downloadResponse.json();
    assert.equal(update.version,plan.version);assert.equal(update.minimumSupportedVersion,'0.0.0');assert.equal(downloads.android,null);assert.equal(downloads.windows.sha256,plan.windows.sha256);
    const url=new URL(update.platforms['windows-x86_64'].url);assert.equal(url.origin,plan.origin);
    const installerResponse=await fetch(origin+url.pathname);assert.match(installerResponse.headers.get('cache-control'),/immutable/);
    const installer=Buffer.from(await installerResponse.arrayBuffer());assert.equal(sha256(installer),plan.windows.sha256);assert.equal(installer.length,plan.windows.bytes);
    verifyUpdaterSignature(installer,publicKey,update.platforms['windows-x86_64'].signature);
  });
  await check('Static public tree exposes no APK, release metadata or backup files',async()=>{
    for(const path of ['/client-updates/android/latest.json','/downloads/0.1.4/PlanCabin-0.1.4-release.apk','/metadata/release-plan.json','/.release-signing/windows-updater.key','/PlanCabin.plan-signing-backup']) assert.equal((await fetch(origin+path)).status,404);
  });
  browser=await chromium.launch({headless:true});
  for(const [name,viewport] of [['desktop',{width:1280,height:900}],['mobile',{width:390,height:844}]]) {
    await check(name+' website downloads the real verified EXE; no Android QR',async()=>{
      const context=await browser.newContext({viewport,locale:'zh-CN',timezoneId:'Asia/Shanghai',acceptDownloads:true});
      try {
        const page=await context.newPage(),errors=[];page.on('pageerror',error=>errors.push(error.message));
        await page.goto(origin,{waitUntil:'networkidle'});
        await expect(page.getByRole('heading',{level:1})).toContainText('把今天安排好');
        const link=page.getByRole('link',{name:'下载 Windows 安装包',exact:true});await expect(link).toBeVisible();
        const downloadPromise=page.waitForEvent('download');await link.click();const download=await downloadPromise;
        assert.equal(download.suggestedFilename(),plan.windows.file);const bytes=readFileSync(await download.path());assert.equal(sha256(bytes),plan.windows.sha256);assert.equal(bytes.length,plan.windows.bytes);
        await page.getByRole('button',{name:'查看 Android 发布状态',exact:true}).click();
        await expect(page.getByRole('dialog')).toContainText('Android 正式签名包尚未发布');await expect(page.getByRole('img',{name:'Android 安装包下载二维码'})).toHaveCount(0);
        await page.screenshot({path:join(output,name+'-android-held.png'),fullPage:true});await page.getByRole('button',{name:'关闭面板',exact:true}).click();
        await page.getByRole('button',{name:'开始使用网页版',exact:true}).click();await expect(page).toHaveURL(/\/login$/);await expect(page.getByText('当前未开放公开注册。')).toBeVisible();
        assert.deepEqual(errors,[]);
      } finally {await context.close();}
    });
  }
  report.status='PASS';
} catch(error){report.status='FAIL';report.failure=error.message;process.exitCode=1;console.error('Local publication smoke failed: '+error.message);}
finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));writeFileSync(join(output,'report.json'),JSON.stringify(report,null,2)+'\n');console.log('Report: '+join(output,'report.json'));}
