import {test,expect,type Page} from '@playwright/test';
async function fixture(page:Page,{verified=false,wait=0,failure=false,expired=false,authenticated=true}={}){
  const user={id:'00000000-0000-4000-8000-000000000123',email:'verification@example.test',displayName:'合成验证用户',role:'USER',emailVerified:verified};let requests=0;
  await page.route('**/api/**',async route=>{const request=route.request(),path=new URL(request.url()).pathname.slice(4);let value:unknown={};
    if(path==='/auth/config')value={registrationEnabled:false,invitationEnabled:true,passwordResetEnabled:true,emailVerificationEnabled:true,planRestDaysEnabled:true};
    else if(path==='/auth/login'||path==='/auth/refresh'){if(!authenticated){await route.fulfill({status:401,json:{message:'合成未登录状态'}});return;}value={accessToken:'synthetic-verification-only-token',expiresInSeconds:900,user};}
    else if(path==='/auth/me')value=user;
    else if(path==='/auth/email-verification/status')value={enabled:true,configured:true,verified:user.emailVerified,retryAfterSeconds:wait};
    else if(path==='/auth/email-verification/request'){requests++;if(failure){await route.fulfill({status:503,json:{message:'发件服务暂未接受邮件，请稍后重试。'}});return;}wait=60;await route.fulfill({status:204});return;}
    else if(path==='/auth/email-verification/confirm'){if(expired){await route.fulfill({status:401,json:{message:'验证链接无效、已使用或已经过期。'}});return;}user.emailVerified=true;await route.fulfill({status:204});return;}
    else if(['/auth/sessions','/plans','/mainlines','/migrations/legacy'].includes(path))value=[];
    else if(path==='/daily/reminders')value={enabled:false,includePlans:true,includeTodos:true,showOverdue:false,upcomingDays:3};
    await route.fulfill({json:value});
  });return{requests:()=>requests};
}
test('account can send verification without copies, shows server acceptance and disables repeated send',async({page},info)=>{
  const f=await fixture(page);await page.goto('/settings');const section=page.getByRole('region',{name:'邮箱验证',exact:true});
  await expect(section.getByText('尚未验证',{exact:true})).toBeVisible();await section.getByRole('button',{name:'发送验证邮件',exact:true}).click();
  await expect(section.getByRole('status')).toContainText('不等于已投递到收件箱');await expect(section.getByRole('button',{name:/请等待 \d+ 秒/})).toBeDisabled();expect(f.requests()).toBe(1);
  await expect(section).toContainText('不会抄送管理员');if(info.project.name==='mobile-web')await page.setViewportSize({width:320,height:740});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await section.screenshot({path:info.outputPath('email-verification.png')});
});
test('verified user has no send control and existing settings remain usable',async({page})=>{
  const f=await fixture(page,{verified:true});await page.goto('/settings');const section=page.getByRole('region',{name:'邮箱验证',exact:true});await expect(section.getByText('已验证',{exact:true})).toBeVisible();await expect(section.getByRole('button',{name:'发送验证邮件',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'保存资料',exact:true})).toBeEnabled();expect(f.requests()).toBe(0);
});
test('SMTP failure is visible rather than falsely accepted',async({page})=>{
  const f=await fixture(page,{failure:true});await page.goto('/settings');const section=page.getByRole('region',{name:'邮箱验证',exact:true});await section.getByRole('button',{name:'发送验证邮件',exact:true}).click();await expect(section.getByRole('alert')).toContainText('暂未接受邮件');await expect(section.getByRole('status')).toHaveCount(0);expect(f.requests()).toBe(1);
});
test('verification removes consumed token from URL and returns to updated account status',async({page})=>{
  await fixture(page);await page.goto('/verify-email?token=synthetic-consumable-token');await page.getByRole('button',{name:'确认验证',exact:true}).click();await expect(page.getByRole('status')).toContainText('邮箱验证完成');await expect(page).toHaveURL(/\/verify-email$/);await page.getByRole('link',{name:'返回账号设置',exact:true}).click();await expect(page.getByRole('region',{name:'邮箱验证',exact:true}).getByText('已验证',{exact:true})).toBeVisible();
});
test('expired verification preserves logged-in access and offers resend from settings',async({page})=>{
  await fixture(page,{expired:true});await page.goto('/verify-email?token=synthetic-expired-token');await page.getByRole('button',{name:'确认验证',exact:true}).click();await expect(page.getByRole('alert')).toContainText('已经过期');await page.getByRole('link',{name:'前往账号设置',exact:true}).click();await expect(page.getByRole('region',{name:'邮箱验证',exact:true}).getByText('尚未验证',{exact:true})).toBeVisible();
});
test('verification remains available without a signed-in session',async({page})=>{
  await fixture(page,{authenticated:false});await page.goto('/verify-email?token=synthetic-guest-token');await page.getByRole('button',{name:'确认验证',exact:true}).click();await expect(page.getByRole('status')).toContainText('邮箱验证完成');await expect(page.getByRole('link',{name:'返回登录',exact:true})).toBeVisible();
});
