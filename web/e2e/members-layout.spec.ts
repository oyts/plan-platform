import {test,expect,type Page} from '@playwright/test';
async function fixture(page:Page) {
  // Explicit UI fixtures only; no production ADMIN account or invitation is used.
  const user={id:'layout-admin-fixture',email:'layout-admin@example.test',displayName:'布局测试管理员',role:'ADMIN',emailVerified:true};
  const rows=[
    {id:'active',email:'very-long-address-for-mobile-layout-acceptance@example.test',expiresAt:'2030-01-01T00:00:00Z',acceptedAt:null,revokedAt:null,createdBy:user.id,registrationUrl:null},
    {id:'accepted',email:'accepted-layout@example.test',expiresAt:'2030-01-01T00:00:00Z',acceptedAt:'2026-01-01T00:00:00Z',revokedAt:null,createdBy:user.id,registrationUrl:null},
    {id:'expired',email:'expired-layout@example.test',expiresAt:'2020-01-01T00:00:00Z',acceptedAt:null,revokedAt:null,createdBy:user.id,registrationUrl:null},
  ];
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname,method=route.request().method();
    if(!path.startsWith('/api/')) return route.continue();
    if(path==='/api/auth/config') return route.fulfill({json:{apiVersion:1,serverVersion:'0.1.4',registrationEnabled:false,invitationEnabled:true,planRestDaysEnabled:true,todoEnabled:true}});
    if(path==='/api/auth/refresh') return route.fulfill({json:{accessToken:'layout-fixture-only',expiresInSeconds:3600,user}});
    if(path==='/api/admin/invitations'&&method==='GET') return route.fulfill({json:rows});
    if(path==='/api/admin/invitations'&&method==='POST') {
      const draft=route.request().postDataJSON();
      const added={id:'created',email:draft.email,expiresAt:'2030-01-01T00:00:00Z',acceptedAt:null,revokedAt:null,createdBy:user.id,registrationUrl:'https://example.invalid/register?invitation=layout-fixture-only'};
      rows.unshift({...added,registrationUrl:null});return route.fulfill({status:201,json:added});
    }
    if(path.startsWith('/api/admin/invitations/')&&method==='DELETE') {
      const row=rows.find(value=>value.id===path.split('/').at(-1));if(row)row.revokedAt='2026-10-12T00:00:00Z' as any;
      return route.fulfill({status:204});
    }
    return route.fulfill({json:[]});
  });
  await page.goto('/members');await expect(page.getByRole('heading',{level:1,name:'成员邀请'})).toBeVisible();
}
test('members mobile cards and invitation flow stay readable at narrow and landscape widths',async({page,isMobile},info)=>{
  await fixture(page);
  if(isMobile) {
    for(const width of [320,390,430,844]) {
      await page.setViewportSize({width,height:width===844?390:844});
      await expect(page.locator('.invitation-cards')).toBeVisible();await expect(page.locator('.invitation-table')).not.toBeVisible();
      expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
      const button=page.getByRole('button',{name:'创建邀请',exact:true});expect((await button.boundingBox())!.height).toBeGreaterThanOrEqual(44);
      await page.screenshot({path:info.outputPath(`members-${width}.png`),fullPage:width!==844});
    }
    await page.setViewportSize({width:390,height:844});
  } else {await expect(page.locator('.invitation-table')).toBeVisible();await expect(page.locator('.invitation-cards')).not.toBeVisible();await page.screenshot({path:info.outputPath('members-desktop.png'),fullPage:true});}
  await page.getByRole('button',{name:'创建邀请',exact:true}).click();const dialog=page.getByRole('dialog',{name:'创建邀请'});await expect(dialog).toBeVisible();
  await dialog.getByLabel('受邀邮箱').fill('new-layout@example.test');await dialog.getByLabel('有效小时数').fill('24');
  await dialog.getByRole('button',{name:'创建邀请',exact:true}).click();await expect(dialog.getByLabel('一次性注册链接')).toHaveValue(/layout-fixture-only/);
  await page.screenshot({path:info.outputPath('members-invite-link.png')});
  await dialog.getByRole('button',{name:'关闭面板'}).click();await expect(dialog).not.toBeVisible();expect(await page.evaluate(()=>document.body.style.overflow)).not.toBe('hidden');
  const visibleList=page.locator(isMobile?'.invitation-cards':'.invitation-table');
  page.once('dialog',dialog=>dialog.accept());await visibleList.locator(isMobile?'article':'tr').filter({hasText:'new-layout@example.test'}).getByRole('button',{name:'撤销'}).click();
  await expect(visibleList.locator(isMobile?'article':'tr').filter({hasText:'new-layout@example.test'})).toContainText('已撤销');
});
