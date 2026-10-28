// Real built PWA/Service Worker + IndexedDB, synthetic local API only. No device UI.
import {createServer} from 'node:http';
import {readFile,readdir,mkdir,writeFile} from 'node:fs/promises';
import {resolve,join,extname,relative} from 'node:path';
const root=resolve('web/dist'),out=resolve('acceptance-results/update-experience/pwa',new Date().toISOString().replace(/[:.]/g,'-'));
process.env.PLAYWRIGHT_BROWSERS_PATH??=resolve('.desktop-tools/playwright');
const {chromium,expect}=await import('../web/node_modules/@playwright/test/index.mjs');
const files=new Map();
async function visit(dir){for(const e of await readdir(dir,{withFileTypes:true})){const p=join(dir,e.name);if(e.isDirectory())await visit(p);else files.set('/'+relative(root,p).replaceAll('\\','/'),p);}}
await visit(root);await mkdir(out,{recursive:true});
const originalWorker=await readFile(join(root,'sw.js'),'utf8');
if(!originalWorker.includes('plan-platform-shell-')||originalWorker.includes('plan-platform-shell-dev'))throw Error('Build the actual versioned PWA first.');
let generation=1,businessWrites=0,browser;
const user={id:'a12ce9b6-7d94-4107-95b1-7a5d77b20390',email:'pwa-update@example.test',displayName:'PWA合成刷新验收',role:'USER',emailVerified:true};
const report={status:'FAIL',cases:[],actualServiceWorker:true,syntheticApi:true,nativeDeviceTested:false,productionChanged:false};
function reply(res,status,body,type='application/json'){res.writeHead(status,{'Content-Type':type,'Cache-Control':'no-store'});res.end(typeof body==='string'?body:JSON.stringify(body));}
const server=createServer(async(req,res)=>{
 try{
  const path=new URL(req.url,'http://127.0.0.1').pathname;
  if(path==='/__fixture/advance'&&req.method==='POST'){generation=2;return reply(res,200,{generation});}
  if(path==='/__fixture/state')return reply(res,200,{generation,businessWrites});
  if(path.startsWith('/api/')){
   if(path==='/api/auth/config')return reply(res,200,{apiVersion:1,serverVersion:'0.1.25',registrationEnabled:false,planRestDaysEnabled:true,todoEnabled:true});
   if(path==='/api/auth/refresh')return reply(res,200,{accessToken:'synthetic-pwa-fixture-only',expiresInSeconds:3600,user});
   if(path==='/api/auth/me')return reply(res,200,user);
   if(path==='/api/daily/reminders')return reply(res,200,{enabled:false});
   if(path==='/api/reflection/guides/claim')return reply(res,200,[]);
   if(req.method!=='GET'){businessWrites++;return reply(res,500,{message:'No business writes allowed in refresh test'});}
   return reply(res,200,[]);
  }
  if(path==='/sw.js')return reply(res,200,originalWorker.replace(/(const CACHE_NAME = ')([^']+)(';)/,`$1$2-refresh-fixture-${generation}$3`),'text/javascript');
  const filename=files.get(path==='/'?'/index.html':path)??(!extname(path)?files.get('/index.html'):null);
  if(!filename)return reply(res,404,'not found','text/plain');
  const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.webmanifest':'application/manifest+json'};
  res.writeHead(200,{'Content-Type':types[extname(filename)]??'application/octet-stream','Cache-Control':'no-cache'});res.end(await readFile(filename));
 }catch{reply(res,500,'Local fixture failed','text/plain');}
});
async function check(name,fn){const row={name,status:'FAIL'};report.cases.push(row);await fn();row.status='PASS';console.log('[PASS] '+name);}
try{
 await new Promise((ok,bad)=>{server.once('error',bad);server.listen(43186,'127.0.0.1',ok);});
 browser=await chromium.launch({headless:true});const ctx=await browser.newContext({viewport:{width:1280,height:900},locale:'zh-CN',timezoneId:'Asia/Shanghai',serviceWorkers:'allow'}),page=await ctx.newPage();
 const origin='http://127.0.0.1:43186';
 await check('实际构建PWA注册并控制页面，关闭一次性本版亮点',async()=>{
  await page.goto(origin+'/todos');await expect(page.getByRole('heading',{name:'今日待办',exact:true})).toBeVisible();
  await page.evaluate(()=>navigator.serviceWorker.ready);await expect.poll(()=>page.evaluate(()=>!!navigator.serviceWorker.controller)).toBe(true);
  const notice=page.getByRole('status',{name:'本版更新亮点',exact:true});await expect(notice).toContainText('0.1.25');await notice.getByRole('button',{name:'知道了',exact:true}).click();
  await expect(page.getByText('没有匹配的待办。可以随手记一件事，或切换状态与日期查看。',{exact:true})).toBeVisible();
 });
 await check('通过真实离线新增建立1项合成队列，另留未提交输入',async()=>{
  await ctx.setOffline(true);await page.getByLabel('快速记录',{exact:true}).fill('PWA刷新保留合成事项');await page.getByRole('button',{name:'记一件事',exact:true}).click();
  await expect(page.getByRole('heading',{name:'PWA刷新保留合成事项',exact:true})).toBeVisible();await expect(page.getByRole('banner').getByRole('link',{name:'待同步 1',exact:true})).toBeVisible();
  await page.getByLabel('快速记录',{exact:true}).fill('尚未提交的合成输入');await ctx.setOffline(false);
 });
 await check('真正等待中的新Worker不强制刷新，取消仍保留输入和队列',async()=>{
  await ctx.request.post(origin+'/__fixture/advance');await page.evaluate(async()=>{await (await navigator.serviceWorker.getRegistration()).update();});
  await expect.poll(()=>page.evaluate(async()=>!!(await navigator.serviceWorker.getRegistration()).waiting)).toBe(true);
  const refresh=page.getByRole('button',{name:'刷新网页版本',exact:true});await expect(refresh).toBeVisible();await expect(page.getByLabel('快速记录',{exact:true})).toHaveValue('尚未提交的合成输入');
  page.once('dialog',d=>d.dismiss());await refresh.click();await expect(refresh).toBeVisible();await expect(page.getByLabel('快速记录',{exact:true})).toHaveValue('尚未提交的合成输入');await expect(page.getByRole('banner').getByRole('link',{name:'待同步 1',exact:true})).toBeVisible();
  await page.screenshot({path:join(out,'cancel-keeps-input.png')});
 });
 await check('明确确认后由真实controllerchange重载，队列不自动提交且亮点不重复',async()=>{
  page.once('dialog',d=>d.accept());await Promise.all([page.waitForEvent('framenavigated',{predicate:f=>f===page.mainFrame()}),page.getByRole('button',{name:'刷新网页版本',exact:true}).click()]);
  await expect(page.getByRole('heading',{name:'今日待办',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'PWA刷新保留合成事项',exact:true})).toBeVisible();await expect(page.getByRole('banner').getByRole('link',{name:'待同步 1',exact:true})).toBeVisible();
  await expect(page.getByRole('status',{name:'本版更新亮点'})).toHaveCount(0);await expect(page.getByRole('button',{name:'刷新网页版本',exact:true})).toHaveCount(0);
  expect(await page.evaluate(()=>caches.keys())).toEqual([expect.stringContaining('refresh-fixture-2')]);expect(businessWrites).toBe(0);
  await page.screenshot({path:join(out,'confirmed-refresh-retains-queue.png')});
 });
 report.status='PASS';await ctx.close();
}catch(e){report.error=String(e.message);console.error('Stopped: '+e.message);process.exitCode=1;}
finally{if(browser)await browser.close();await new Promise(ok=>server.close(ok));report.businessWrites=businessWrites;await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);}
