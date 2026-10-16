import {test,expect,type Page} from '@playwright/test';
async function fixture(page:Page){
  const writes:string[]=[];
  await page.route('**/api/**',async r=>{
    const path=new URL(r.request().url()).pathname;
    if(!['GET','HEAD'].includes(r.request().method())&&!path.startsWith('/api/auth/'))writes.push(path);
    if(path==='/api/auth/config')return r.fulfill({json:{apiVersion:1,serverVersion:'0.1.12',registrationEnabled:false,invitationEnabled:true,todoEnabled:true}});
    if(path==='/api/auth/refresh')return r.fulfill({json:{accessToken:'teaching-fixture-only',user:{id:'ai-learning-user',email:'ai-learning@example.test',displayName:'教学合成账号',role:'USER',emailVerified:true},expiresInSeconds:3600}});
    if(path==='/api/ai/capabilities')return r.fulfill({json:{available:false,adjustmentEnabled:true,message:'教学测试不连接上游',maxInputChars:8000}});
    if(path==='/api/ai/quota')return r.fulfill({json:{dailyLimit:20,remaining:20,used:0,pending:0}});
    return r.fulfill({json:[]});
  });
  return writes;
}
test('AI first-contact teaching is skippable and can be reopened',async({page})=>{
  await fixture(page);await page.goto('/ai');
  const prompt=page.getByLabel('首次使用教学');await expect(prompt).toContainText('第一次使用AI 规划');
  await prompt.getByRole('button',{name:'稍后再看'}).click();await expect(prompt).toHaveCount(0);
  await page.reload();await expect(prompt).toHaveCount(0);
  await page.goto('/learn');await expect(page.locator('.lesson-card')).toHaveCount(5);
  await page.locator('.lesson-card').filter({has:page.getByRole('heading',{name:'AI 规划',exact:true})}).getByRole('link',{name:'开始练习'}).click();
  await expect(page).toHaveURL(/\/learn\/ai$/);
});
test('four AI practice steps persist without paid calls or business writes',async({page},info)=>{
  const writes=await fixture(page);await page.goto('/learn/ai');
  const next=page.getByRole('button',{name:'下一步',exact:true});await expect(next).toBeDisabled();
  await page.getByLabel('练习目标',{exact:true}).fill('两周复习英语，每次20分钟');
  await page.getByLabel('已阅读对话处理说明（练习）').check();await page.getByLabel('选择练习模式').selectOption('ROUTE');await expect(next).toBeDisabled();
  await page.getByLabel('选择练习模式').selectOption('PLAN');await next.click();
  await page.getByRole('button',{name:'生成示例草稿（不调用 AI）'}).click();await expect(next).toBeDisabled();
  await page.getByLabel('修改练习任务标题').fill('复习5个英语单词');await page.getByRole('button',{name:'保存练习',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('练习已保存在当前设备');await page.reload();
  await expect(page.getByLabel('修改练习任务标题')).toHaveValue('复习5个英语单词');await next.click();
  const create=page.getByRole('button',{name:'确认生成（练习）',exact:true});await expect(create).toBeDisabled();
  await page.getByLabel('我已核对内容，确认生成（练习）').check();await expect(next).toBeDisabled();await create.click();await next.click();
  const finish=page.getByRole('button',{name:'完成教学',exact:true});await expect(finish).toBeDisabled();
  await page.getByRole('button',{name:'预览放慢后的安排（练习）'}).click();
  await expect(page.getByLabel('练习调整对照')).toContainText('20 分钟 → 10 分钟');await expect(finish).toBeDisabled();
  await page.getByRole('button',{name:'确认应用调整（练习）'}).click();
  await expect(page.getByRole('status').filter({hasText:'仅未开始的事项放慢'})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({path:info.outputPath('ai-learning-adjustment.png'),fullPage:true});
  await finish.click();await expect(page.getByRole('heading',{name:'AI 规划教学已完成'})).toBeVisible();
  await page.reload();await expect(page.getByRole('heading',{name:'AI 规划教学已完成'})).toBeVisible();
  await page.getByRole('button',{name:'重新练习'}).click();await expect(page.getByLabel('练习目标',{exact:true})).toHaveValue('');
  expect(writes).toEqual([]);
});
