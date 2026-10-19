import { test,expect } from '@playwright/test';
test('shared colors work for plan creation, tag edit and route creation/edit with compatible HEX storage',async({page,isMobile},info)=>{
  const id='20000000-0000-4000-8000-000000000001',tid='20000000-0000-4000-8000-000000000002';let routeColor='#002FA7',tagColor='#002FA7';let lineCreated=false;
  const plan={id,name:'合成配色计划',description:'',startDate:'2026-10-19',endDate:'2026-10-31',restDays:[],version:0};
  const line=()=>({id,name:'合成路线',description:'',color:routeColor,version:0,completed:1,total:2,executionMode:'FLEXIBLE',stages:[{id:'base',name:'基础',description:'',sortOrder:0,version:0}],items:[{id:'one',title:'已完成练习',description:'',plannedMinutes:10,status:'COMPLETED',sortOrder:0,stageId:'base',acceptance:[],unlocked:true,version:0,accumulatedSeconds:0},{id:'two',title:'下一步练习',description:'',plannedMinutes:25,status:'PENDING',sortOrder:1,stageId:'base',acceptance:[],unlocked:true,version:0,accumulatedSeconds:0}]});
  await page.route('**/api/**',async r=>{const p=new URL(r.request().url()).pathname,m=r.request().method(),b=['POST','PUT'].includes(m)?r.request().postDataJSON():{};
    if(p==='/api/auth/config')return r.fulfill({json:{apiVersion:1,serverVersion:'0.1.16',registrationEnabled:false,invitationEnabled:true,planRestDaysEnabled:true,todoEnabled:true}});
    if(p==='/api/auth/refresh')return r.fulfill({json:{accessToken:'color-fixture-only',user:{id:'colors-user',email:'colors@example.test',role:'USER',displayName:'配色合成测试',emailVerified:true}}});
    if(p==='/api/plans')return r.fulfill({json:[plan]});
    if(p===`/api/plans/${id}`)return r.fulfill({json:plan});
    if(p.endsWith('/tags')&&m==='GET')return r.fulfill({json:[{id:tid,name:'总标签',color:tagColor,overall:true,version:0}]});
    if(p===`/api/tags/${tid}`&&m==='PUT'){tagColor=b.color;return r.fulfill({json:{id:tid,name:b.name,color:tagColor,overall:true,version:1}});}
    if(p==='/api/mainlines'&&m==='POST'){routeColor=b.color;lineCreated=true;return r.fulfill({json:line()});}
    if(p==='/api/mainlines')return r.fulfill({json:lineCreated?[line()]:[]});
    if(p===`/api/mainlines/${id}`&&m==='PUT'){routeColor=b.color;return r.fulfill({json:line()});}
    return r.fulfill({json:[]});
  });
  await page.goto('/plans');const picker=page.getByRole('group',{name:'标签颜色',exact:true});await picker.getByLabel('HEX',{exact:true}).fill('#1a2b3c');await picker.getByLabel('HEX',{exact:true}).blur();await expect(picker.getByLabel('标签颜色 R',{exact:true})).toHaveValue('26');await picker.getByLabel('标签颜色 G',{exact:true}).fill('255');await expect(picker.getByLabel('HEX',{exact:true})).toHaveValue('#1AFF3C');await picker.getByLabel('标签颜色 B',{exact:true}).fill('256');await expect(picker.getByRole('alert')).toContainText('0–255');
  await page.goto(`/plans/${id}`);await page.locator('.tag-tool').getByRole('button',{name:'编辑',exact:true}).click();await page.getByRole('group',{name:'标签颜色',exact:true}).getByLabel('HEX',{exact:true}).fill('#abcdef');await page.getByRole('group',{name:'标签颜色',exact:true}).getByLabel('HEX',{exact:true}).blur();await page.getByRole('button',{name:'保存标签',exact:true}).click();await expect.poll(()=>tagColor).toBe('#ABCDEF');
  await page.goto('/mainlines');await page.getByLabel('路线名称',{exact:true}).fill('合成路线');await page.getByRole('group',{name:'路线颜色',exact:true}).getByLabel('HEX',{exact:true}).fill('#0080ff');await page.getByRole('group',{name:'路线颜色',exact:true}).getByLabel('HEX',{exact:true}).blur();await page.getByRole('button',{name:'新建路线',exact:true}).click();await expect(page.getByLabel('整条路线参考投入')).toContainText('35 分钟');await page.getByLabel('搜索步骤',{exact:true}).fill('下一步');await page.getByLabel('只看未完成',{exact:true}).check();await expect(page.getByLabel('整条路线参考投入')).toContainText('未完成步骤参考 25 分钟');expect(routeColor).toBe('#0080FF');
  await page.getByRole('button',{name:'路线设置',exact:true}).click();const dialog=page.getByRole('dialog',{name:'路线设置',exact:true});await dialog.getByLabel('路线颜色 R',{exact:true}).fill('255');await dialog.getByRole('button',{name:'保存路线设置',exact:true}).click();await expect.poll(()=>routeColor).toBe('#FF80FF');await expect(dialog).not.toBeVisible();
  if(isMobile)for(const width of [320,390,430]){await page.setViewportSize({width,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);}
  await page.screenshot({path:info.outputPath('route-effort-and-color.png'),fullPage:true});
});
