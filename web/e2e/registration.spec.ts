import {test,expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
test('real local registration needs password confirmation and then retains the session',async({page})=>{
  let submitted=0;
  page.on('request',request=>{if(new URL(request.url()).pathname==='/api/auth/register'&&request.method()==='POST')submitted++;});
  await page.goto('/register');
  await page.getByLabel('显示名称',{exact:true}).fill('密码确认验收');
  await page.getByLabel('邮箱',{exact:true}).fill(`registration-${randomUUID()}@example.test`);
  await page.getByLabel('密码',{exact:true}).fill('RegistrationAcceptance123!');
  await page.getByLabel('确认密码',{exact:true}).fill('RegistrationAcceptance124!');
  await page.getByRole('button',{name:'创建账号',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('两次输入的密码不一致');expect(submitted).toBe(0);
  await page.getByLabel('确认密码',{exact:true}).fill('RegistrationAcceptance123!');
  await page.getByRole('button',{name:'创建账号',exact:true}).click();
  await expect(page.locator('.site-header')).toBeVisible();expect(submitted).toBe(1);
  await page.reload();await expect(page.locator('.site-header')).toBeVisible();
});
