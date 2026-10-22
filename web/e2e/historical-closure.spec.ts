import {test,expect,type Page} from '@playwright/test';
const batch='00000000-0000-4000-8000-000000000777',requestId='00000000-0000-4000-8000-000000000888';
async function fixture(page:Page,{stale=false,mailFailure=false,mailEnabled=true,guest=false}={}) {
  const user={id:'00000000-0000-4000-8000-000000000123',email:'old@example.test',displayName:'合成账号安全验收',role:'USER',emailVerified:true};
  let authenticated=!guest,supplements=0,starts=0,verifies=0,completes=0,oldVerified=false,newVerified=false,state='NONE',newEmail='';
  const requests:unknown[]=[];
  await page.route('**/api/**',async route=>{
    const req=route.request(),pathname=new URL(req.url()).pathname;
    if(!pathname.startsWith('/api/')){await route.continue();return;}
    const path=pathname.slice(4);let value:unknown={};
    if(path==='/auth/config')value={registrationEnabled:false,invitationEnabled:true,passwordResetEnabled:true,emailVerificationEnabled:true,planRestDaysEnabled:true,serverVersion:'0.1.19',apiVersion:1};
    else if(path==='/auth/refresh'||path==='/auth/login'){if(!authenticated){await route.fulfill({status:401,json:{message:'合成未登录'}});return;}value={accessToken:'synthetic-historical-fixture-only',expiresInSeconds:900,user};}
    else if(path==='/auth/logout'){authenticated=false;await route.fulfill({status:204});return;}
    else if(path==='/auth/me')value=user;
    else if(path==='/auth/email-verification/status')value={enabled:true,configured:true,verified:true,retryAfterSeconds:0};
    else if(path==='/auth/email-change/status')value={enabled:mailEnabled,configured:true,requestId:state==='NONE'?null:requestId,newEmail,state,oldVerified,newVerified,expiresAt:'2026-10-23T10:30:00Z',retryAfterSeconds:0};
    else if(path==='/auth/email-change/request'){
      starts++;if(mailFailure){await route.fulfill({status:503,json:{message:'邮件未完全接受，原请求未替换。'}});return;}
      state='PENDING';newEmail=req.postDataJSON().newEmail;value={enabled:true,configured:true,requestId,newEmail,state,oldVerified,newVerified,expiresAt:'2026-10-23T10:30:00Z',retryAfterSeconds:60};
    }else if(path==='/auth/email-change/verify'){verifies++;await route.fulfill({status:204});return;}
    else if(path==='/auth/email-change/complete'){completes++;await route.fulfill({status:204});return;}
    else if(path===`/auth/email-change/${requestId}/cancel`){state='CANCELLED';await route.fulfill({status:204});return;}
    else if(path==='/migrations/legacy')value=[{importId:batch,sourceVersion:'1.10.1（合成）',status:'COMPLETED',createdAt:'2026-10-22T10:00:00Z'}];
    else if(path===`/migrations/legacy/${batch}/recycling-preview`)value={importId:batch,checksum:'a'.repeat(64),fingerprint:'b'.repeat(64),alreadyIncluded:false,writeSupported:true,retainedPermanentMarkers:1,notice:'确认后仅补入回收站，不覆盖当前内容。',entries:[{type:'mainline-item',legacyId:'synthetic-deleted-step',legacyParentId:'synthetic-route',deletedAt:'2026-10-20T03:00:00Z',action:'ATTACH_DELETED_CHILD',targetParentId:'synthetic-parent',parentVersion:3}]};
    else if(path===`/migrations/legacy/${batch}/recycling-supplement`){supplements++;requests.push(req.postDataJSON());if(stale){await route.fulfill({status:409,json:{message:'预检后数据已改变，请重新预检；没有写入。'}});return;}value={status:'APPLIED'};}
    else if(['/auth/sessions','/plans','/mainlines'].includes(path))value=[];
    else if(path==='/daily/reminders')value={enabled:false,includePlans:true,includeTodos:true,showOverdue:false,upcomingDays:3};
    await route.fulfill({json:value});
  });
  return {counts:()=>({supplements,starts,verifies,completes}),requests,verifyBoth:()=>{oldVerified=true;newVerified=true;}};
}
test('recycling preview never writes before explicit consent and has a clear scope',async({page},info)=>{
  const f=await fixture(page);await page.goto('/settings');const panel=page.getByRole('region',{name:'补迁旧版回收站'});
  await panel.getByLabel('已完成批次').selectOption(batch);await panel.getByRole('button',{name:'只读预检'}).click();
  await expect(panel.getByText('待补快照 1 项')).toBeVisible();expect(f.counts().supplements).toBe(0);
  const apply=panel.getByRole('button',{name:'确认补迁回收站'});await expect(apply).toBeDisabled();await panel.getByRole('checkbox').check();await apply.click();
  await expect(panel.getByRole('status')).toContainText('原有任务与历史未被覆盖');expect(f.counts().supplements).toBe(1);expect(f.requests[0]).toMatchObject({confirmed:true,fingerprint:'b'.repeat(64)});
  if(info.project.name==='mobile-web')await page.setViewportSize({width:320,height:740});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await panel.screenshot({path:info.outputPath('recycling-panel.png')});
});
test('stale recycling preview keeps the request and does not claim success',async({page})=>{
  const f=await fixture(page,{stale:true});await page.goto('/settings');const panel=page.getByRole('region',{name:'补迁旧版回收站'});
  await panel.getByLabel('已完成批次').selectOption(batch);await panel.getByRole('button',{name:'只读预检'}).click();await panel.getByRole('checkbox').check();await panel.getByRole('button',{name:'确认补迁回收站'}).click();
  await expect(panel.getByRole('alert')).toContainText('重新预检');await expect(panel.getByRole('status')).toHaveCount(0);await expect(panel.getByText('待补快照 1 项')).toBeVisible();expect(f.counts().supplements).toBe(1);
});
test('email change checks repeated email and requires two proofs plus final consent',async({page},info)=>{
  const f=await fixture(page);await page.goto('/settings');const panel=page.getByRole('region',{name:'更改登录邮箱'});
  await panel.getByLabel('新邮箱',{exact:true}).fill('new@example.test');await panel.getByLabel('再次输入新邮箱').fill('different@example.test');await panel.getByLabel('当前密码',{exact:true}).fill('SyntheticOnly123');await panel.getByRole('button',{name:'发送两封验证邮件'}).click();
  await expect(panel.getByRole('alert')).toContainText('两次输入的新邮箱不一致');expect(f.counts().starts).toBe(0);
  await panel.getByLabel('再次输入新邮箱').fill('new@example.test');await panel.getByRole('button',{name:'发送两封验证邮件'}).click();await expect(panel).toContainText('尚未更改登录邮箱');
  const finish=panel.getByRole('button',{name:'确认变更并退出全部设备'});await expect(finish).toBeDisabled();expect(f.counts().completes).toBe(0);
  f.verifyBoth();await panel.getByRole('button',{name:'重新读取变更状态'}).click();await expect(finish).toBeDisabled();await panel.getByLabel('再次输入当前密码').fill('SyntheticOnly123');await panel.getByRole('checkbox').check();await expect(finish).toBeEnabled();
  if(info.project.name==='mobile-web')await page.setViewportSize({width:320,height:740});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await panel.screenshot({path:info.outputPath('email-change-panel.png')});
  await finish.click();await expect(page).toHaveURL(/\/login\?emailChanged=1/);await expect(page.getByRole('status')).toContainText('使用新邮箱');expect(f.counts().completes).toBe(1);
});
test('email verification page strips URL capability and never auto-consumes on open',async({page})=>{
  const f=await fixture(page,{guest:true});await page.goto(`/change-email#request=${requestId}&side=old&token=${'x'.repeat(73)}`);
  await expect(page).toHaveURL(/\/change-email$/);expect(f.counts().verifies).toBe(0);await page.getByRole('button',{name:'确认本邮箱验证'}).click();await expect(page.getByRole('status')).toContainText('登录邮箱尚未更改');expect(f.counts().verifies).toBe(1);
});
test('partial mail failure is visible and never becomes a pending success',async({page})=>{
  const f=await fixture(page,{mailFailure:true});await page.goto('/settings');const panel=page.getByRole('region',{name:'更改登录邮箱'});
  await panel.getByLabel('新邮箱',{exact:true}).fill('new@example.test');await panel.getByLabel('再次输入新邮箱').fill('new@example.test');await panel.getByLabel('当前密码',{exact:true}).fill('SyntheticOnly123');await panel.getByRole('button',{name:'发送两封验证邮件'}).click();
  await expect(panel.getByRole('alert')).toContainText('原请求未替换');await expect(panel.getByRole('button',{name:'确认变更并退出全部设备'})).toHaveCount(0);expect(f.counts().starts).toBe(1);
});
test('disabled email-change switch does not block existing account settings',async({page})=>{
  await fixture(page,{mailEnabled:false});await page.goto('/settings');const panel=page.getByRole('region',{name:'更改登录邮箱'});await expect(panel).toContainText('暂未启用邮箱变更');await expect(panel.getByRole('button',{name:'发送两封验证邮件'})).toHaveCount(0);await expect(page.getByRole('button',{name:'保存资料'})).toBeEnabled();
});
