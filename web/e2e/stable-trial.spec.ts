import {test,expect,type Page,type APIRequestContext} from '@playwright/test';
import {randomUUID} from 'node:crypto';
const API='http://127.0.0.1:43131/api',password='StableSynthetic123!';
async function login(page:Page,request:APIRequestContext){const email=`stable-${randomUUID()}@example.test`;const r=await request.post(API+'/auth/register',{data:{email,password,displayName:'稳定候选合成验收'}});expect(r.status()).toBe(201);await page.goto('/login');await page.getByLabel('邮箱',{exact:true}).fill(email);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page.locator('.site-header')).toBeVisible();}
test('website explains daily use and only advertises explicitly available templates',async({page},info)=>{
 await page.goto('/welcome');await expect(page.getByTestId('published-templates')).toBeVisible();await expect(page.locator('.learning-courses>li')).toHaveCount(6);
 await expect(page.locator('.product-section>header>span')).toHaveText(['01','02','03','04','05','06','07']);
 await expect(page.locator('#daily-use')).toContainText('默认关闭');await expect(page.locator('#daily-use')).toContainText('不承诺必达');await expect(page.locator('#daily-use')).toContainText('不覆盖在用');
 await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('stable-home.png'),fullPage:true});
});
test('legacy or unavailable health hides unreleased template marketing but keeps manual web entry',async({page})=>{
 await page.route('**/api/health',r=>r.fulfill({json:{service:'plan-platform-api',ok:true,version:'0.1.18',apiVersion:1}}));await page.goto('/welcome');await expect(page.locator('.learning-courses>li')).toHaveCount(5);await expect(page.getByTestId('published-templates')).toHaveCount(0);
 await page.unroute('**/api/health');await page.route('**/api/health',r=>r.fulfill({status:503}));await page.reload();await expect(page.getByTestId('published-templates')).toHaveCount(0);await page.getByRole('button',{name:'开始使用网页版',exact:true}).click();await expect(page).toHaveURL(/\/login$/);
});
for(const mode of ['PLAN','ROUTE'] as const)test(`template ${mode} lesson confirms structure, creates an independent practice copy and persists`,async({page,request},info)=>{
 await login(page,request);const mutations:string[]=[];page.on('request',r=>{if(/\/api\/(structure-templates|plans|tasks|mainlines|todos|ai)(\/|\?|$)/.test(r.url())&&!['GET','HEAD'].includes(r.method()))mutations.push(r.url());});
 await page.goto('/learn/template');const next=page.getByRole('button',{name:'下一步',exact:true});await expect(next).toBeDisabled();await page.getByRole('combobox',{name:'练习结构来源',exact:true}).selectOption(mode);await page.getByLabel('练习模板名称',{exact:true}).fill('合成可复用方法');await next.click();
 await expect(next).toBeDisabled();await page.getByLabel('我已核对练习结构和保存范围',{exact:true}).check();await page.getByRole('button',{name:'保存为练习模板',exact:true}).click();await next.click();
 await page.getByLabel('练习副本名称',{exact:true}).fill('合成新副本');if(mode==='PLAN'){await page.getByLabel('练习新周期开始',{exact:true}).fill('2026-11-02');await page.getByLabel('练习新周期结束',{exact:true}).fill('2026-11-07');await expect(page.getByRole('button',{name:'建立练习副本',exact:true})).toBeDisabled();await expect(page.locator('.template-learning')).toContainText('完整七天');await page.getByLabel('练习新周期结束',{exact:true}).fill('2026-11-08');await page.getByLabel('星期日',{exact:true}).check();}
 await page.getByRole('button',{name:'保存练习',exact:true}).click();await expect(page.getByText('练习已保存在当前设备。',{exact:true})).toBeVisible();await page.reload();await expect(page.getByLabel('练习副本名称',{exact:true})).toHaveValue('合成新副本');
 await page.getByLabel('确认建立练习副本，不修改来源',{exact:true}).check();await page.getByRole('button',{name:'建立练习副本',exact:true}).click();await next.click();await expect(page.getByRole('button',{name:'完成教学',exact:true})).toBeDisabled();await page.getByRole('button',{name:'修改来源标题（练习）',exact:true}).click();await page.getByRole('button',{name:'完成副本第一项（练习）',exact:true}).click();
 if(info.project.name==='mobile-web')await page.setViewportSize({width:320,height:740});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('template-lesson.png'),fullPage:true});
 await page.getByRole('button',{name:'完成教学',exact:true}).click();await expect(page.getByRole('heading',{name:'整套模板教学已完成',exact:true})).toBeVisible();await page.reload();await expect(page.getByRole('heading',{name:'整套模板教学已完成',exact:true})).toBeVisible();expect(mutations).toEqual([]);
});
test('an older server gives a compatibility explanation without attempting template requests',async({page,request})=>{
 await login(page,request);let templateRequests=0;page.on('request',r=>{if(r.url().includes('/api/structure-templates'))templateRequests++;});await page.route('**/api/health',r=>r.fulfill({json:{service:'plan-platform-api',ok:true,version:'0.1.18',apiVersion:1}}));
 await page.goto('/templates');await expect(page.getByRole('alert')).toContainText('当前服务器尚不支持整套模板');await expect(page.getByRole('button',{name:'预览保存结构',exact:true})).toHaveCount(0);expect(templateRequests).toBe(0);
});
