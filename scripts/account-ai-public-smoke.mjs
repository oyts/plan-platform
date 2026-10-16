// Production smoke for changed UX only, using the existing invited synthetic
// ordinary account. No paid AI, invitation, plan or quota write.
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
const root=fileURLToPath(new URL('../',import.meta.url)),origin='https://plan.zijun701.cn';
const version=JSON.parse(await readFile(join(root,'web/package.json'))).version;
const email='web-acceptance-a-20261012@example.test';
const account=JSON.parse(await readFile(join(root,'.desktop-tools/public-web/private/accounts.json')))[email];
if(!account?.password)throw Error('Existing synthetic invited account required');
const out=join(root,'acceptance-results/account-ai-public',new Date().toISOString().replace(/[:.]/g,'-')+'-'+randomUUID().slice(0,6));await mkdir(out,{recursive:true});
const report={version,status:'FAIL',paidCalls:0,cases:[]};let browser;
try{
 const health=await(await fetch(origin+'/api/health')).json();expect(health.version).toBe(version);
 const config=await(await fetch(origin+'/api/auth/config')).json();expect(config.registrationEnabled).toBe(false);expect(config.passwordResetEnabled).toBe(false);expect(config.invitationEnabled).toBe(true);
 browser=await chromium.launch({headless:true});
 for(const [name,viewport] of [['desktop',{width:1280,height:900}],['mobile-web',{width:390,height:844}]]){
  const context=await browser.newContext({viewport,locale:'zh-CN',timezoneId:'Asia/Shanghai',serviceWorkers:'block'}),page=await context.newPage(),errors=[];let paidPosts=0;
  page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.method()==='POST'&&/\/ai\/sessions\/[^/]+\/jobs$/.test(new URL(r.url()).pathname))paidPosts++;});
  await page.goto(origin+'/reset-password');await expect(page.getByRole('heading',{name:'找回密码暂未开放'})).toBeVisible();
  const login=await context.request.post(origin+'/api/auth/login',{data:{email,password:account.password}});expect(login.status()).toBe(200);const token=(await login.json()).accessToken;
  const quota=await(await context.request.get(origin+'/api/ai/quota',{headers:{Authorization:'Bearer '+token}})).json();
  for(const path of ['/api/admin/mail','/api/admin/ai/usage'])expect((await context.request.get(origin+path,{headers:{Authorization:'Bearer '+token}})).status()).toBe(403);
  const caps=await context.request.get(origin+'/api/ai/capabilities',{headers:{Authorization:'Bearer '+token}});expect(caps.headers()['cache-control']).toContain('no-store');expect((await caps.json()).available).toBe(true);
  await page.goto(origin+'/ai?mode=TODO');await expect(page.getByRole('heading',{name:'AI 规划助手',exact:true})).toBeVisible();await expect(page.getByLabel('AI 额度说明')).toBeVisible();
  await page.getByText('不确定下一步怎么做？',{exact:true}).click();const lesson=page.getByRole('link',{name:'进入 AI 四步教学'});await expect(lesson).toBeVisible();
  const input=page.getByLabel('你的目标或修改要求');await input.fill('合成测试：尚未发送，不调用 AI');let confirmed=false;page.once('dialog',async d=>{confirmed=true;await d.dismiss();});await lesson.click();await expect(input).toHaveValue('合成测试：尚未发送，不调用 AI');expect(confirmed).toBe(true);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);await page.screenshot({path:join(out,name+'-ai-guidance.png'),fullPage:true});await input.fill('');
  expect(paidPosts).toBe(0);const after=await(await context.request.get(origin+'/api/ai/quota',{headers:{Authorization:'Bearer '+token}})).json();expect(after.used).toBe(quota.used);expect(after.pending).toBe(quota.pending);expect(errors).toEqual([]);
  report.cases.push({name:name+' private admin boundary, inactive SMTP, real HTTPS AI help and unsent guard',status:'PASS'});await context.close();
 }
 report.status='PASS';console.log('PASS production Web: changed UX, ordinary-user boundaries, 0 paid calls; evidence '+out);
}catch(error){report.failure=String(error.stack||error).replaceAll(account.password,'[redacted]');process.exitCode=1;console.error('Production smoke failed; see ignored evidence.');}
finally{await browser?.close();await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));}
