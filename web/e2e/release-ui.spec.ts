import {test,expect,type Page} from '@playwright/test';
import {readFileSync} from 'node:fs';
const {version}=JSON.parse(readFileSync(new URL('../package.json',import.meta.url),'utf8'));
async function registration(page:Page,enabled=false) {
  const requests:any[]=[];
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(!path.startsWith('/api/'))return route.continue();
    if(path==='/api/auth/config')return route.fulfill({json:{apiVersion:1,serverVersion:version,registrationEnabled:enabled,invitationEnabled:true,todoEnabled:true,planRestDaysEnabled:true}});
    if(path==='/api/auth/refresh')return route.fulfill({status:401,json:{message:'Synthetic signed-out fixture'}});
    if(path==='/api/auth/register'){requests.push(route.request().postDataJSON());return route.fulfill({status:400,json:{message:'布局夹具不创建真实账号。'}});}
    return route.fulfill({json:[]});
  });
  await page.goto('/register?email=registration-layout@example.test&invite=synthetic-invite-not-a-real-link');
  await expect(page.getByRole('heading',{name:'创建账号',exact:true})).toBeVisible();
  return requests;
}
test('registration baseline and final layout are readable',async({page,isMobile},info)=>{
  await registration(page);
  for(const width of isMobile?[320,390,430]:[1280]) {
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({path:info.outputPath(`registration-${width}.png`),fullPage:true});
  }
});
test('invite registration rejects mismatch and sends only one password after confirmation',async({page})=>{
  const requests=await registration(page);
  await page.getByLabel('显示名称',{exact:true}).fill('注册确认测试');
  await page.getByLabel('密码',{exact:true}).fill('SyntheticPassword123!');
  await page.getByLabel('确认密码',{exact:true}).fill('SyntheticPassword124!');
  await page.getByRole('button',{name:'创建账号',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('两次输入的密码不一致');
  expect(requests).toHaveLength(0);
  await page.getByLabel('确认密码',{exact:true}).fill('SyntheticPassword123!');
  await page.getByRole('button',{name:'创建账号',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('布局夹具不创建真实账号');
  expect(requests).toHaveLength(1);
  expect(Object.keys(requests[0]).sort()).toEqual(['displayName','email','invitationToken','password']);
  expect(requests[0].password).toBe('SyntheticPassword123!');
  await page.getByLabel('密码',{exact:true}).fill('ChangedPassword123!');
  await page.getByRole('button',{name:'创建账号',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('两次输入的密码不一致');
  expect(requests).toHaveLength(1);
});
test('closed public registration stays closed',async({page})=>{
  await registration(page);
  await page.goto('/register');
  await expect(page.getByText('当前仅接受邀请注册链接注册。',{exact:true})).toBeVisible();
  await expect(page.getByLabel('密码',{exact:true})).toHaveCount(0);
});
test('todo dates stay full-width and readable on phone',async({page,isMobile},info)=>{
  const user={id:'todo-layout-user',email:'todo-layout@example.test',displayName:'日期布局测试',role:'USER',emailVerified:false};
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(!path.startsWith('/api/'))return route.continue();
    if(path==='/api/auth/config')return route.fulfill({json:{apiVersion:1,serverVersion:version,registrationEnabled:false,invitationEnabled:true,todoEnabled:true,planRestDaysEnabled:true}});
    if(path==='/api/auth/refresh')return route.fulfill({json:{accessToken:'layout-fixture-only',expiresInSeconds:3600,user}});
    return route.fulfill({json:[]});
  });
  await page.goto('/todos');await expect(page.getByRole('heading',{name:'今日待办',exact:true})).toBeVisible();
  for(const width of isMobile?[320,360,390,430,844]:[1280]) {
    await page.setViewportSize({width,height:width===844?390:844});
    const field=page.getByLabel('查看日期',{exact:true});await field.fill('2026-12-31');await expect(field).toHaveValue('2026-12-31');
    expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
    if(width<=430)expect((await field.boundingBox())!.width).toBeGreaterThanOrEqual(240);
    await page.screenshot({path:info.outputPath(`todo-date-${width}.png`),fullPage:true});
  }
});
