// Read-only live UI check with an explicitly invalid synthetic invite.
// The mismatched form must never issue a registration request or create an account.
import assert from 'node:assert/strict';
import {mkdirSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';
const origin='https://plan.zijun701.cn',root=fileURLToPath(new URL('../',import.meta.url));
const out=join(root,'acceptance-results/public-registration',new Date().toISOString().replace(/[:.]/g,'-'));
mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true}),cases=[];
try {
  for(const [name,viewport] of [['desktop',{width:1280,height:900}],['mobile-web',{width:390,height:844}]]) {
    const context=await browser.newContext({viewport,locale:'zh-CN',serviceWorkers:'block'});
    try {
      const page=await context.newPage();let posts=0;
      page.on('request',request=>{if(new URL(request.url()).pathname==='/api/auth/register'&&request.method()==='POST')posts++;});
      await page.goto(origin+'/register');
      await expect(page.getByText('当前仅接受邀请注册链接注册。',{exact:true})).toBeVisible();
      await expect(page.getByLabel('密码',{exact:true})).toHaveCount(0);
      await page.goto(origin+'/register?invite=synthetic-invalid-ui-probe&email=ui-only%40example.test');
      await page.getByLabel('显示名称',{exact:true}).fill('只读界面检查');
      await page.getByLabel('密码',{exact:true}).fill('SyntheticProbe123!');
      await page.getByLabel('确认密码',{exact:true}).fill('SyntheticProbe124!');
      await page.getByRole('button',{name:'创建账号',exact:true}).click();
      await expect(page.getByRole('alert')).toContainText('两次输入的密码不一致');
      assert.equal(posts,0,'Password mismatch must not reach production registration');
      assert.equal(await page.getByLabel('确认密码',{exact:true}).getAttribute('type'),'password');
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
      await page.screenshot({path:join(out,name+'.png'),fullPage:true});
      cases.push({name,status:'PASS',registrationRequests:posts});console.log('PASS '+name+' production password confirmation and closed public registration');
    } finally {await context.close();}
  }
  writeFileSync(join(out,'report.json'),JSON.stringify({status:'PASS',scope:'Real HTTPS UI; invalid synthetic invite, zero registration POSTs; no account creation.',cases},null,2));
} finally {await browser.close();}
