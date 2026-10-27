import {expect,test,type Page} from '@playwright/test';

// Synthetic UI/bridge contracts only. These are NOT native installation/signature evidence.
async function fixture(page:Page,options:{private?:boolean;notes?:string|null;mandatory?:boolean;current?:string;code?:number;latest?:boolean;known?:boolean}={}){
 await page.addInitScript(o=>{
  const w=window as any;w.isTauri=true;w.updateFixtureCalls=[];w.updateFixtureTarget=o.latest?null:{version:'0.2.0',notes:o.notes===undefined?'改进提醒。\n保留已有数据。\n完善版本说明。':o.notes,mandatory:o.mandatory??false,...(o.private?{versionCode:31}: {})};
  if(o.known)w.updateFixtureTarget={version:'0.1.25',versionCode:30,notes:'随包说明优先于远端长日志。',mandatory:false};
  const user={id:'e477d705-5346-41d5-b68e-f64598e58ce8',email:'updates-synthetic@example.test',displayName:'更新合成验收',role:'USER',emailVerified:true};
  w.__TAURI_INTERNALS__={invoke:async(command:string,args:any)=>{
   w.updateFixtureCalls.push(command);
   if(command==='desktop_info')return{version:o.current??'0.1.24',...(o.code?{versionCode:o.code}:{}),serverUrl:'http://127.0.0.1:43146',updatesEnabled:true,updateRequiresLogin:!!o.private,updateChannel:'https://plan.example.cn/client-updates/'+(o.private?'android':'windows')+'/latest.json'};
   if(command==='desktop_check_update')return w.updateFixtureTarget;
   if(command==='reminder_status')return{enabled:false};
   if(command==='desktop_request'){
    const path=args.request?.path?.split('?')[0];let body:any={},status=200;
    if(path==='/api/auth/config')body={registrationEnabled:false,invitationEnabled:true};
    else if(path==='/api/auth/refresh')body={accessToken:'synthetic-update-ui-not-a-credential',expiresInSeconds:3600,user};
    else if(path==='/api/auth/me')body=user;
    else if(path==='/api/daily/reminders')body={enabled:false};
    else if(path==='/api/reflection/guides/claim')body=[];
    else {status=404;body={message:'synthetic fixture has no business data'};}
    return{status,headers:{},body:JSON.stringify(body)};
   }
   return true;
  }};
 },options);
 await page.goto('/#/desktop');await expect(page.getByRole('heading',{name:'客户端更新',exact:true})).toBeVisible();
 await expect(page.getByRole('combobox',{name:'工作模式',exact:true})).toBeVisible();
}
const notice=(p:Page)=>p.getByRole('status',{name:'客户端版本提示',exact:true});
test('ignore persists across reload, restore is explicit, a new version prompts again',async({page})=>{
 await fixture(page);await expect(notice(page)).toBeVisible();await notice(page).getByRole('button',{name:'忽略此版本',exact:true}).click();await expect(notice(page)).toHaveCount(0);
 await page.reload();await expect(page.getByRole('button',{name:'检查新版本',exact:true})).toBeVisible();await page.getByRole('button',{name:'检查新版本',exact:true}).click();await expect(page.getByText('已忽略此版本的顶部提醒',{exact:false})).toBeVisible();await expect(notice(page)).toHaveCount(0);
 await page.getByRole('button',{name:'恢复此版本提醒',exact:true}).click();await expect(notice(page)).toBeVisible();
 await notice(page).getByRole('button',{name:'忽略此版本',exact:true}).click();await page.evaluate(()=>{(window as any).updateFixtureTarget.version='0.2.1';});await page.getByRole('button',{name:'检查新版本',exact:true}).click();await expect(notice(page)).toContainText('0.2.1');
 await expect(page.getByRole('checkbox',{name:'自动下载更新（安装前仍需确认）'})).not.toBeChecked();
 await page.screenshot({path:test.info().outputPath('update-and-highlights.png'),fullPage:true});
});
test('snooze survives reload, lifecycle events do not flood the bridge',async({page})=>{
 await fixture(page);await notice(page).getByRole('button',{name:'稍后处理',exact:true}).click();
 await page.evaluate(()=>{for(let i=0;i<8;i++){window.dispatchEvent(new Event('focus'));window.dispatchEvent(new Event('online'));document.dispatchEvent(new Event('visibilitychange'));}});
 expect(await page.evaluate(()=>(window as any).updateFixtureCalls.filter((v:string)=>v==='desktop_check_update').length)).toBe(1);
 await page.reload();await expect(page.getByRole('button',{name:'检查新版本',exact:true})).toBeVisible();await expect(notice(page)).toHaveCount(0);
 await page.getByRole('button',{name:'检查新版本',exact:true}).click();await expect(notice(page)).toHaveCount(0);await expect(page.getByRole('button',{name:'恢复此版本提醒',exact:true})).toBeVisible();
});
test('installed highlights appear once; history follows installed version, not available version',async({page})=>{
 await fixture(page);const highlights=page.getByRole('status',{name:'本版更新亮点',exact:true});await expect(highlights).toContainText('0.1.24');await highlights.getByRole('button',{name:'知道了',exact:true}).click();
 await page.reload();await expect(page.getByRole('heading',{name:'客户端更新',exact:true})).toBeVisible();await expect(highlights).toHaveCount(0);
 await page.getByRole('link',{name:'查看本机版本说明与历史',exact:true}).click();const history=page.getByRole('region',{name:'版本说明与历史',exact:true});await expect(history).toContainText('0.1.24');await expect(history).not.toContainText('0.1.25');await expect(history).not.toContainText('0.2.0');
 await page.screenshot({path:test.info().outputPath('installed-version-history.png'),fullPage:true});
});
test('Android-code mismatch does not display false installed highlights',async({page})=>{
 await fixture(page,{private:true,code:28});await expect(page.getByRole('status',{name:'本版更新亮点'})).toHaveCount(0);await page.getByRole('link',{name:'查看本机版本说明与历史',exact:true}).click();const history=page.getByRole('region',{name:'版本说明与历史'});await expect(history).not.toContainText('0.1.24 ·');await expect(history).toContainText('0.1.23');
});
test('untrusted markup is text only; missing notes keep safe download available',async({page})=>{
 await fixture(page,{notes:'<img src=x onerror="window.updateXss=1">'});await expect(notice(page)).toContainText('<img src=x');expect(await page.evaluate(()=>(window as any).updateXss)).toBeUndefined();expect(await notice(page).locator('img').count()).toBe(0);
 await page.evaluate(()=>{(window as any).updateFixtureTarget.notes=null;});await page.getByRole('button',{name:'检查新版本',exact:true}).click();await expect(page.getByText('此版本暂未提供说明，不影响签名校验和安全更新。',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'下载更新',exact:true})).toBeEnabled();
});
test('mandatory policy overrides a previous ignore and preserves safety exits',async({page})=>{
 await fixture(page);await notice(page).getByRole('button',{name:'忽略此版本',exact:true}).click();await page.evaluate(()=>{(window as any).updateFixtureTarget.mandatory=true;});await page.getByRole('button',{name:'检查新版本',exact:true}).click();await expect(page.getByRole('alert',{name:'客户端版本提示'})).toBeVisible();await expect(page.getByRole('button',{name:'忽略此版本',exact:true})).toHaveCount(0);
 await page.evaluate(()=>{location.hash='/plans';});await expect(page).toHaveURL(/#\/desktop$/);await expect(page.getByRole('heading',{name:'必须更新至 0.2.0',exact:true})).toBeVisible();
});
test('web history has no native installation claim or native bridge',async({page})=>{
 await page.route('http://127.0.0.1:43146/api/**',r=>r.request().url().includes('/auth/config')?r.fulfill({json:{registrationEnabled:false}}):r.fulfill({status:401,json:{message:'synthetic signed out'}}));
 await page.goto('/help');await expect(page.getByText('浏览器页面，没有原生安装版本',{exact:false})).toBeVisible();await expect(page.getByRole('region',{name:'版本说明与历史'})).toBeVisible();await expect(page.getByRole('status',{name:'本版更新亮点'})).toHaveCount(0);
});
test('known release shows date and cautions with usable controls at 320px',async({page})=>{
 await page.setViewportSize({width:320,height:780});await fixture(page,{known:true});
 await expect(page.getByText('发布说明日期：2026-10-27',{exact:true})).toBeVisible();
 await expect(page.getByText('Android需联网登录受邀账号后检查更新。',{exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await notice(page).getByRole('button',{name:'忽略此版本',exact:true}).click();await expect(page.getByRole('button',{name:'恢复此版本提醒',exact:true})).toBeVisible();
});
