// Public read-only homepage checks. No accounts, invitation links or paid calls.
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const origin='https://plan.zijun701.cn';
const output=join(root,'acceptance-results/product-home-refresh',new Date().toISOString().replace(/[:.]/g,'-'));
await mkdir(output,{recursive:true});
const report={status:'FAIL',scope:'Public homepage, unchanged authentication/download gates and desktop/mobile presentation only',paidCalls:0,cases:[]};
const browser=await chromium.launch();
try {
  const response=await fetch(origin+'/api/health');assert.equal(response.status,200);const health=await response.json();assert.equal(health.service,'plan-platform-api');assert.equal(health.version,'0.1.13');
  const config=await(await fetch(origin+'/api/auth/config')).json();assert.equal(config.registrationEnabled,false);assert.equal(config.invitationEnabled,true);assert.equal(config.passwordResetEnabled,true);
  const manifest=await(await fetch(origin+'/downloads/manifest.json')).json();assert.equal(manifest.android,null);assert.equal(manifest.windows.version,'0.1.13');
  for(const path of ['/client-updates/android/latest.json','/downloads/0.1.13/PlanCabin-0.1.13-release.apk'])assert.equal((await fetch(origin+path)).status,404);
  assert.equal((await fetch(origin+'/api/client-updates/android/latest')).status,401);
  report.cases.push('API health, SMTP recovery and invitation-only/public-download boundaries unchanged');
  for(const [name,viewport] of [['desktop',{width:1440,height:1000}],['mobile',{width:390,height:844}],['narrow-mobile',{width:320,height:740}]]) {
    const context=await browser.newContext({viewport,serviceWorkers:'block',locale:'zh-CN',timezoneId:'Asia/Shanghai'});
    const page=await context.newPage(),errors=[],writes=[];
    page.on('pageerror',e=>errors.push(e.message));
    page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/')&&!['GET','HEAD'].includes(r.method())&&new URL(r.url()).pathname!=='/api/auth/refresh')writes.push(r.url());});
    await page.goto(origin+'/welcome');
    await expect(page.getByRole('heading',{level:1})).toContainText('让目标走得下去');
    await expect(page.locator('meta[name="product-page-revision"]')).toHaveAttribute('content','ai-overview-20261017');
    await expect(page.locator('.ai-workflow>li')).toHaveCount(4);
    await expect(page.locator('.learning-courses>li')).toHaveCount(5);
    await expect(page.getByRole('link',{name:'下载 Windows 安装包'})).toHaveAttribute('href',new URL(manifest.windows.url,origin).href);
    await page.screenshot({path:join(output,name+'-top.png')});
    await page.getByRole('tab',{name:'目标路线',exact:true}).click();await expect(page.getByRole('tabpanel')).toContainText('没有截止日期');
    await page.getByRole('tab',{name:'今日待办',exact:true}).click();await expect(page.getByRole('tabpanel')).toContainText('准备开始练吉他');
    await page.getByRole('navigation',{name:'官网导航'}).getByRole('link',{name:'AI 规划',exact:true}).click();await expect(page.locator('#ai')).toBeInViewport();
    await page.screenshot({path:join(output,name+'-ai.png')});
    await page.getByRole('button',{name:'查看 Android 发布状态'}).click();await expect(page.getByRole('dialog')).toContainText('仅供受邀测试');await expect(page.getByRole('dialog').getByRole('img')).toHaveCount(0);
    await page.getByRole('button',{name:'关闭面板'}).click();
    await expect(page.getByRole('link',{name:'鄂ICP备2026050813号-1'})).toHaveAttribute('href','https://beian.miit.gov.cn/');
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:join(output,name+'-full.png'),fullPage:true});
    await page.getByRole('button',{name:'开始使用网页版'}).click();await expect(page).toHaveURL(/\/login$/);await expect(page.getByRole('heading',{name:'登录计划平台'})).toBeVisible();
    assert.deepEqual(errors,[]);assert.deepEqual(writes,[]);
    report.cases.push(name+': AI preview, teaching, download gates, filing link, login and no overflow');
    await context.close();
  }
  report.status='PASS';
} catch(error) {report.error=String(error.message);process.exitCode=1;}
finally {await browser.close();await writeFile(join(output,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));console.log('Evidence: '+output);}
