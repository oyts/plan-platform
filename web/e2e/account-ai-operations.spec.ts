import{test,expect,type Page}from'@playwright/test';
const id='11111111-1111-4111-8111-111111111111';
const user={id:'operations-fixture',email:'synthetic-admin@example.test',displayName:'合成管理员',role:'ADMIN',emailVerified:true};
async function fixture(page:Page,{loggedIn=true,submission=429}:{loggedIn?:boolean;submission?:number}={}){
 let jobs=0,resets=0;
 const session={id,mode:'TODO',recommendedMode:'TODO',version:0,draft:null,applied:null,adjustment:null,expiresAt:'2026-12-01T00:00:00Z',turns:[]};
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname,method=route.request().method();
  if(path==='/api/auth/config')return route.fulfill({json:{apiVersion:1,serverVersion:'0.1.13',registrationEnabled:false,passwordResetEnabled:true,invitationEnabled:true,emailVerificationEnabled:false,todoEnabled:true}});
  if(path==='/api/auth/refresh')return loggedIn?route.fulfill({json:{accessToken:'fixture-only',user,expiresInSeconds:3600}}):route.fulfill({status:401,json:{message:'fixture signed out'}});
  if(path==='/api/auth/password-reset/request')return route.fulfill({status:204});
  if(path==='/api/auth/password-reset/confirm'){resets++;expect(Object.keys(route.request().postDataJSON()).sort()).toEqual(['email','newPassword','token']);return route.fulfill({status:204});}
  if(path==='/api/admin/mail')return route.fulfill({json:{configured:false,passwordResetEnabled:false,emailVerificationEnabled:false}});
  if(path==='/api/admin/access')return route.fulfill({json:{allowed:true}});
  if(path==='/api/admin/ai/runtime')return route.fulfill({json:{version:0,limits:{maxConcurrent:1,queueCapacity:2,maxInputChars:8000,maxOutputTokens:2048,totalOutputTokens:4096,timeoutSeconds:45},updatedAt:null}});
  if(path==='/api/admin/ai/runtime/audit')return route.fulfill({json:{items:[],page:0,size:10,total:0}});
  if(path==='/api/admin/operations/status')return route.fulfill({json:{enabled:false,mailAlertsEnabled:false,dataAvailable:false,observedAt:null,policy:{keepNewest:7,retentionDays:30,backupMaxHours:26,diskMinPercent:15,version:0},checks:[],backups:[],cleanupPreview:[],oss:{backupName:null,verifiedAt:null,verified:false},restore:{backupName:null,verifiedAt:null,verified:false},diskFreeBytes:-1,diskTotalBytes:-1}});
  if(path==='/api/admin/governance/config')return route.fulfill({json:{enabled:false}});
  if(path==='/api/admin/governance/members'||path==='/api/admin/governance/audit'||path==='/api/admin/operations/policy-audit')return route.fulfill({json:{content:[],page:0,size:10,totalElements:0,totalPages:0}});
  if(path==='/api/admin/ai/usage')return route.fulfill({json:{from:'2026-10-11',to:'2026-10-17',timezone:'Asia/Shanghai',totals:{requests:2,succeeded:1,failed:0,unknown:1,queued:0,running:0,untracked:0,inputTokens:120,outputTokens:45,tokenKnownRequests:1},days:[],runtime:{enabled:true,maxConcurrent:1,queueCapacity:2,queued:0,running:0,circuit:{active:0,queued:0,circuitOpen:false,retryAfterSeconds:0}},recent:{items:[{requestId:id,member:'合成成员',email:'synthetic-member@example.test',quotaDate:'2026-10-17',state:'UNKNOWN',quotaState:'UNKNOWN',errorCode:'TIMEOUT',createdAt:'2026-10-17T00:00:00Z',completedAt:null,durationMs:null,inputTokens:null,outputTokens:null}],page:0,size:10,total:2}}});
  if(path==='/api/admin/ai/settings')return route.fulfill({json:{dailyLimit:20,version:0}});
  if(path==='/api/admin/ai/users'||path==='/api/admin/ai/audit')return route.fulfill({json:{items:[],page:0,size:10,total:0}});
  if(path==='/api/admin/invitations')return route.fulfill({json:[{id,email:'invited-fixture@example.test',expiresAt:'2026-01-01T00:00:00Z',acceptedAt:null,revokedAt:null,createdBy:'合成管理员'}]});
  if(path==='/api/ai/capabilities')return route.fulfill({json:{available:true,adjustmentEnabled:true,maxInputChars:8000}});
  if(path==='/api/ai/quota')return route.fulfill({json:{dailyLimit:20,remaining:20,used:0,pending:0,paused:false,resetsAt:'2026-10-17T16:00:00Z'}});
  if(path==='/api/ai/sessions'&&method==='GET')return route.fulfill({json:[]});
  if(path==='/api/ai/sessions'&&method==='POST')return route.fulfill({json:session});
  if(path===`/api/ai/sessions/${id}`)return route.fulfill({json:session});
  if(path.endsWith('/jobs')){jobs++;return route.fulfill({status:submission,json:{message:submission===429?'今日额度不足':'temporary gateway failure'}});}
  return route.fulfill({json:[]});
 });
 return{jobs:()=>jobs,resets:()=>resets};
}
test('password recovery confirmation prevents typo, never submits confirmation field',async({page},info)=>{
 const calls=await fixture(page,{loggedIn:false});await page.goto('/reset-password?email=synthetic@example.test&token=fixture-token');
 await page.getByLabel('新密码',{exact:true}).fill('NewPassword123!');await page.getByLabel('确认新密码',{exact:true}).fill('WrongPassword123!');await page.getByRole('button',{name:'设置新密码',exact:true}).click();
 await expect(page.getByRole('alert')).toContainText('两次输入的密码不一致');expect(calls.resets()).toBe(0);
 await page.getByLabel('确认新密码',{exact:true}).fill('NewPassword123!');await page.getByRole('button',{name:'设置新密码',exact:true}).click();await expect(page).toHaveURL(/\/login\?passwordReset=success$/);expect(calls.resets()).toBe(1);
});
test('recovery request gives generic feedback and prevents rapid repeat',async({page})=>{
 await fixture(page,{loggedIn:false});await page.goto('/reset-password');await page.getByLabel('邮箱',{exact:true}).fill('unknown-synthetic@example.test');await page.getByRole('button',{name:'发送重置链接'}).click();await expect(page.getByRole('status')).toContainText('如果该邮箱已注册');await expect(page.getByRole('button',{name:/秒后可重发/})).toBeDisabled();
});
test('admin operations panel shows metadata, missing tokens and inactive mail honestly',async({page},info)=>{
 await fixture(page);await page.goto('/admin/ai');await expect(page.getByRole('heading',{name:'AI 用量与运行状态'})).toBeVisible();
 const usage=page.getByRole('region',{name:'AI 用量与运行状态'});await expect(usage).toContainText('结果待核实');await expect(usage).toContainText('未知 / 未知');await expect(usage).toContainText('这不是服务商账单');
 await usage.scrollIntoViewIfNeeded();expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);await page.screenshot({path:info.outputPath('operations-metadata.png'),fullPage:true});
 await page.goto('/admin/operations');const mail=page.getByRole('region',{name:'账号邮件服务'});await expect(mail).toContainText('未配置');await expect(mail.getByRole('button',{name:'发送管理员测试邮件'})).toBeDisabled();
 await page.goto('/members');page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'重新邀请 invited-fixture@example.test',exact:true}).click();await expect(page.getByRole('dialog')).toContainText('旧链接只在新邀请创建成功后失效');await page.getByRole('button',{name:'关闭',exact:true}).click();
});
for(const status of [429,502])test(`AI submission ${status} retains input and only uncertain outcome blocks resend`,async({page},info)=>{
 const calls=await fixture(page,{submission:status});await page.goto('/ai?mode=TODO');
 await page.getByLabel('你的目标或修改要求').fill('这是合成验收，提醒我整理书桌');await page.getByRole('checkbox',{name:/我同意将本次对话/}).check();const send=page.getByRole('button',{name:'发送给 AI',exact:true});await send.click();
 await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByLabel('你的目标或修改要求')).toHaveValue('这是合成验收，提醒我整理书桌');expect(calls.jobs()).toBe(1);
 if(status===502){await expect(send).toBeDisabled();await expect(page.getByText('请先刷新确认本次请求状态，再继续发送。',{exact:true})).toBeVisible();}else await expect(send).toBeEnabled();
 await page.getByText('不确定下一步怎么做？',{exact:true}).click();await expect(page.getByRole('link',{name:'进入 AI 四步教学'})).toBeVisible();expect(calls.jobs()).toBe(1);
 await page.screenshot({path:info.outputPath('ai-submission-'+status+'.png'),fullPage:true});
});
