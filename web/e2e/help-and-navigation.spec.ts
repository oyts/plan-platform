import {test,expect,type Page} from '@playwright/test';
async function fixture(page:Page,admin=false){
  const user={id:'00000000-0000-4000-8000-000000000001',email:'demo@example.test',displayName:'合成导航验收',role:admin?'ADMIN':'USER',emailVerified:false};
  let failHealth=false;
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname.slice(4);let value:unknown={};
    if(path==='/auth/config')value={registrationEnabled:false,passwordResetEnabled:false,invitationEnabled:true,planRestDaysEnabled:true,operationsAvailable:true};
    else if(path==='/auth/refresh'||path==='/auth/login')value={accessToken:'synthetic-help-token',expiresInSeconds:900,user};
    else if(path==='/auth/me')value=user;
    else if(path==='/plans'||path==='/mainlines')value=[];
    else if(path==='/health'){if(failHealth){await route.fulfill({status:503,json:{message:'合成故障'}});return;}value={service:'plan-platform-api',ok:true,version:'0.1.15',apiVersion:1};}
    else if(path==='/admin/operations/alerts')value=[];
    else if(path==='/daily/reminders')value={enabled:false,includePlans:true,includeTodos:true,showOverdue:true,upcomingDays:3,version:null};
    await route.fulfill({json:value});
  });return{failHealth:()=>{failHealth=true;}};
}
test('help reports only checked health and does not keep an old successful result after a failure',async({page},info)=>{
  const state=await fixture(page);await page.goto('/help');await expect(page.getByRole('heading',{name:'帮助与版本',exact:true})).toBeVisible();await expect(page.getByText('尚未确认',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'检查服务器版本',exact:true}).click();await expect(page.getByText(/本次检查/)).toBeVisible();
  state.failHealth();await page.getByRole('button',{name:'检查服务器版本',exact:true}).click();await expect(page.getByText('尚未确认',{exact:true})).toBeVisible();await expect(page.getByText(/本次检查/)).toHaveCount(0);
  await expect(page.getByRole('heading',{name:'功能与边界',exact:true})).toBeVisible();if(info.project.name==='mobile-web')await page.setViewportSize({width:320,height:740});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('help-and-boundaries.png'),fullPage:true});
});
test('local demo is isolated, resets and never sends business or paid AI writes',async({page},info)=>{
  await fixture(page);const writes:string[]=[];page.on('request',r=>{if(r.url().includes('/api/')&&!['GET','HEAD'].includes(r.method())&&!new URL(r.url()).pathname.startsWith('/api/auth/'))writes.push(r.url());});
  await page.goto('/help/demo');await expect(page.getByRole('heading',{name:'本地演示',exact:true})).toBeVisible();await page.getByLabel('阅读第一章并写三条笔记').check();await expect(page.getByText('演示：已完成',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'目标路线',exact:true}).click();await expect(page.getByText('认识琴弦与调音',{exact:true})).toBeVisible();await page.getByRole('button',{name:'今日待办',exact:true}).click();await expect(page.getByText('寄出资料',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'查看本地示例建议',exact:true}).click();await page.getByRole('button',{name:'仅应用到演示',exact:true}).click();await expect(page.getByRole('status')).toHaveText(/没有修改真实计划/);await page.getByRole('button',{name:'重置本页演示',exact:true}).click();await expect(page.getByRole('button',{name:'仅应用到演示',exact:true})).toHaveCount(0);await page.getByRole('button',{name:'周期计划',exact:true}).click();await expect(page.getByLabel('阅读第一章并写三条笔记')).not.toBeChecked();
  expect(writes).toEqual([]);await page.screenshot({path:info.outputPath('synthetic-local-demo.png'),fullPage:true});
});
test('grouped more menu fits and Escape returns focus without exposing administrator entries',async({page,isMobile},info)=>{
  await fixture(page);await page.goto('/help');if(!isMobile)await page.setViewportSize({width:940,height:900});
  const button=page.getByRole('button',{name:'更多',exact:true});await button.click();const dialog=page.getByRole('dialog',{name:'更多功能',exact:true});await expect(dialog).toBeVisible();
  for(const label of ['数据中心','编排与历史','离线范围','反馈与诊断','帮助与版本','使用教学'])await expect(dialog.getByRole('link',{name:label,exact:true})).toBeVisible();
  await expect(dialog.getByRole('link',{name:'运行与治理',exact:true})).toHaveCount(0);await expect(dialog.getByRole('link',{name:'成员',exact:true})).toHaveCount(0);
  await page.keyboard.press('Escape');await expect(dialog).not.toBeVisible();await expect(button).toBeFocused();await button.click();await dialog.getByRole('link',{name:'使用教学',exact:true}).click();await expect(dialog).not.toBeVisible();await expect(page).toHaveURL(/\/learn$/);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('compact-navigation.png')});
});
test('admin menu remains grouped and keyboard skip link reaches the main content',async({page,isMobile},info)=>{
  await fixture(page,true);await page.goto('/help');await page.getByRole('link',{name:'跳到主要内容',exact:true}).focus();await page.keyboard.press('Enter');await expect(page.locator('main')).toBeFocused();
  await page.getByRole('button',{name:'更多',exact:true}).click();const dialog=page.getByRole('dialog',{name:'更多功能',exact:true});await expect(dialog.getByRole('link',{name:'运行与治理',exact:true})).toBeVisible();await expect(dialog.getByRole('link',{name:'成员',exact:true})).toBeVisible();
  if(isMobile)await page.setViewportSize({width:320,height:740});await page.screenshot({path:info.outputPath('grouped-admin-menu.png'),fullPage:true});
});
test('help and synthetic demo remain accessible without an account',async({page})=>{
  await page.route('**/api/**',route=>route.fulfill({status:401,json:{message:'合成未登录状态'}}));
  await page.goto('/login');await page.getByRole('link',{name:'帮助与本地演示',exact:true}).click();await expect(page).toHaveURL(/\/help$/);await expect(page.getByRole('heading',{name:'帮助与版本',exact:true})).toBeVisible();
  await page.getByRole('link',{name:'开始 3—5 分钟演示',exact:true}).click();await expect(page).toHaveURL(/\/help\/demo$/);await page.getByLabel('阅读第一章并写三条笔记').check();await expect(page.getByText('演示：已完成',{exact:true})).toBeVisible();await expect(page.locator('.site-header')).toHaveCount(0);
});
test('desktop header keeps primary and more controls reachable at the native minimum width',async({page,isMobile},info)=>{
  test.skip(isMobile,'The native desktop minimum width is a desktop-only boundary.');await fixture(page);await page.goto('/help');
  for(const width of [800,940,1280]){await page.setViewportSize({width,height:900});const nav=page.getByRole('navigation',{name:'主导航',exact:true});for(const label of ['今日','日程','计划','AI 规划','同步'])await expect(nav.getByRole('link',{name:label,exact:true})).toBeInViewport();await expect(nav.getByRole('button',{name:'更多',exact:true})).toBeInViewport();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);}
  await page.setViewportSize({width:800,height:900});await page.screenshot({path:info.outputPath('native-minimum-800px.png')});
});
