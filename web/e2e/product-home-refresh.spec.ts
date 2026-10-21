import {test,expect} from '@playwright/test';

test('homepage explains real AI workflow and safe fallback without submitting AI',async({page},info)=>{
  const writes:string[]=[];
  page.on('request',request=>{if(new URL(request.url()).pathname.startsWith('/api/')&&!['GET','HEAD'].includes(request.method())&&new URL(request.url()).pathname!=='/api/auth/refresh')writes.push(request.url());});
  await page.goto('/welcome');
  await expect(page.getByRole('heading',{level:1})).toContainText('把今天安排好');
  await expect(page.locator('.ai-workflow>li')).toHaveCount(4);
  await expect(page.locator('#ai')).toContainText('由你确认');
  await expect(page.locator('#ai')).toContainText('不会把原计划删掉重建');
  await expect(page.locator('#ai')).toContainText('DeepSeek 官方 API');
  await expect(page.locator('#ai')).toContainText('手动规划');
  await expect(page.locator('.learning-courses>li')).toHaveCount(6);
  await expect(page.locator('.learning-copy')).toContainText('不消耗对话次数');
  await page.getByRole('tab',{name:'目标路线',exact:true}).click();
  await expect(page.getByRole('tabpanel')).toContainText('没有截止日期');
  await expect(page.getByRole('tabpanel')).toContainText('随时暂停');
  await page.getByRole('tab',{name:'今日待办',exact:true}).click();
  await expect(page.getByRole('tabpanel')).toContainText('准备开始练吉他');
  await expect(page.locator('.preview-caption')).toContainText('不会调用 AI 或创建计划');
  expect(writes).toEqual([]);
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
  await page.screenshot({path:info.outputPath('refreshed-home.png'),fullPage:true});
});

test('preview tabs support keyboard selection without a provider request',async({page})=>{
  await page.goto('/welcome');
  const first=page.getByRole('tab',{name:'周期计划',exact:true});await first.focus();
  await page.keyboard.press('ArrowRight');
  const route=page.getByRole('tab',{name:'目标路线',exact:true});await expect(route).toBeFocused();await expect(route).toHaveAttribute('aria-selected','true');
  await page.keyboard.press('End');await expect(page.getByRole('tab',{name:'今日待办',exact:true})).toBeFocused();
  await page.keyboard.press('Home');await expect(first).toBeFocused();await expect(first).toHaveAttribute('aria-selected','true');
});

test('official links, invited Android state and AI anchor are preserved',async({page})=>{
  await page.route('**/downloads/manifest.json',route=>route.fulfill({json:{schemaVersion:1,windows:{version:'0.1.13',sha256:'a'.repeat(64),bytes:3488144,url:'/downloads/0.1.13/plan.exe'},android:null}}));
  await page.goto('/welcome');
  await page.getByRole('navigation',{name:'官网导航'}).getByRole('link',{name:'AI 规划',exact:true}).click();
  await expect(page).toHaveURL(/#ai$/);await expect(page.locator('#ai')).toBeInViewport();
  await expect(page.getByRole('link',{name:'下载 Windows 安装包'})).toHaveAttribute('href',/\/downloads\/0\.1\.13\/plan\.exe$/);
  await page.getByRole('button',{name:'查看 Android 发布状态'}).click();
  const dialog=page.getByRole('dialog');await expect(dialog).toContainText('暂不提供公开下载及二维码');
  await expect(dialog.getByRole('img')).toHaveCount(0);await expect(dialog.getByRole('link',{name:'直接下载 APK'})).toHaveCount(0);
  await page.getByRole('button',{name:'关闭面板'}).click();
  await expect(page.getByRole('link',{name:'鄂ICP备2026050813号-1'})).toHaveAttribute('href','https://beian.miit.gov.cn/');
  await expect(page.getByRole('link',{name:'GitHub 源码'})).toHaveAttribute('href','https://github.com/oyts/plan-platform');
  await page.getByText('现在可以直接注册吗？',{exact:true}).click();await expect(page.locator('.faq-list')).toContainText('没有公众注册入口');
  await page.getByRole('button',{name:'开始使用网页版'}).click();await expect(page).toHaveURL(/\/login$/);
});

test('download failure leaves product content and web entry usable',async({page})=>{
  await page.route('**/downloads/manifest.json',route=>route.fulfill({status:503}));
  await page.goto('/welcome');
  await expect(page.getByRole('status')).toContainText('暂时无法获取下载清单');
  await expect(page.locator('#ai')).toContainText('确认以后，再生成');
  await expect(page.getByRole('button',{name:'正式安装包尚未发布'})).toBeDisabled();
  await page.getByRole('button',{name:'开始使用网页版'}).click();await expect(page).toHaveURL(/\/login$/);
});
