// Shared hands-on learning regression for isolated native clients and synthetic Web accounts.
// The caller supplies navigation/restart; this helper never reads credentials or writes business data.
export async function exerciseLearning(client, expect, navigate, reopen) {
  let page = client.page;
  async function start(id, firstHeading) {
    await navigate(client, '/learn/' + id);
    await expect(page.getByRole('heading', { name: '使用教学', exact: true })).toBeVisible();
    await expect(page.locator('.lesson-tabs a[aria-current="page"]')).toHaveText({plan:'周期计划',route:'目标路线',todo:'今日待办',sync:'离线与同步'}[id]);
    const restart = page.getByRole('button', { name: '重新练习', exact: true });
    await expect(page.locator('.lesson-finished, .lesson-practice')).toBeVisible();
    if (await restart.isVisible()) { await expect(restart).toBeEnabled(); await restart.click(); }
    await expect(page.getByRole('button', { name: '保存练习', exact: true })).toBeEnabled();
    // A prior interrupted test may have a saved exercise. Return using its own UI only.
    for (let i = 0; i < 3 && await page.getByRole('button', { name: '上一步', exact: true }).isEnabled(); i++) {
      await page.getByRole('button', { name: '上一步', exact: true }).click();
      await expect(page.getByRole('button', { name: '保存练习', exact: true })).toBeEnabled();
    }
    await expect(page.getByRole('heading', { name: firstHeading, exact: true })).toBeVisible();
  }
  async function next() { await page.getByRole('button', { name: '下一步', exact: true }).click(); await expect(page.getByRole('button', { name: '保存练习', exact: true })).toBeEnabled(); }
  async function finish(name) { await page.getByRole('button', { name: '完成教学', exact: true }).click(); await expect(page.getByRole('heading', { name: name + '教学已完成', exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: '重新练习', exact: true })).toBeEnabled(); }
  await start('plan', '设置周期');
  await page.getByLabel('练习计划名称', { exact: true }).fill('七天阅读练习');
  await page.getByLabel('练习开始日期').fill('2026-10-12'); await page.getByLabel('练习结束日期').fill('2026-10-18'); await next();
  const sunday = page.getByLabel('星期日', { exact: true }); if (!await sunday.isChecked()) await sunday.check(); await next();
  await page.getByLabel('练习任务标题').fill('阅读 20 分钟'); await page.getByLabel('练习任务日期').fill('2026-10-17'); await next();
  await page.getByLabel('完成练习任务', { exact: true }).check(); await finish('周期计划');
  await start('route', '写下目标'); await page.getByLabel('练习目标名称').fill('学会弹奏一首歌'); await next();
  await page.getByLabel('练习阶段名称').fill('认识吉他'); await next(); await page.getByLabel('步骤 2 需要先完成步骤 1').check(); await next();
  await page.getByLabel('完成步骤 1：认识六根琴弦').check(); await expect(page.getByText('步骤 2：练习单音 · 现在可以开始', { exact: true })).toBeVisible(); await finish('目标路线');
  await start('todo', '记录一件事'); await page.getByLabel('练习待办标题').fill('寄出资料'); await next(); await page.getByLabel('练习待办备注').fill('确认收件地址');
  await page.getByRole('button', { name: '保存练习', exact: true }).click(); await expect(page.getByRole('status').filter({ hasText: '练习已保存在当前设备。' })).toBeVisible();
  await reopen(client, '/learn/todo'); page = client.page; await expect(page.getByLabel('练习待办备注')).toHaveValue('确认收件地址'); await next();
  await page.getByLabel('完成练习待办').check(); await next(); await page.getByRole('button', { name: '取消完成并改到明天（练习）' }).click(); await finish('今日待办');
  await start('sync', '准备离线'); await page.getByLabel('已在练习中联网打开这条任务').check(); await next();
  await page.getByRole('button', { name: '模拟离线保存', exact: true }).click(); await next(); await page.getByRole('button', { name: '查看两份内容', exact: true }).click(); await next();
  await page.getByLabel('保留云端版本（练习）').check(); await finish('离线与同步');
  await reopen(client, '/learn'); page = client.page;
  // This legacy scope exercises four courses only; a separately completed AI
  // lesson must not turn a successful result into a spurious count failure.
  for (const title of ['周期计划','目标路线','今日待办','离线与同步']) await expect(page.locator('.lesson-card').filter({has:page.getByRole('heading',{name:title,exact:true})}).getByText('已完成，可随时重学',{exact:true})).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
}
