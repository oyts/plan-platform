// Android-only real WebView acceptance. Synthetic fixtures use the real isolated API;
// no page.route(), mocked native bridge, production identity or paid provider.
export async function exerciseAndroidRoadmap({client:c,owner,other,admin,api,expect,route,restart,configure,scenario,section='all'}) {
  if (c.kind !== 'android') throw new Error('This suite only authorizes the independent Android package.');
  if (!['all','help'].includes(section)) throw new Error('Unsupported targeted section.');
  const page = () => c.page;
  const button = name => page().getByRole('button',{name,exact:true});
  const heading = name => page().getByRole('heading',{name,exact:true});
  const label = name => page().getByLabel(name,{exact:true});
  const day = (await api('/dashboard/today',undefined,owner.token)).date;
  const shift = n => {const d=new Date(day+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);};
  const weekDate = new Date(day+'T12:00:00Z');weekDate.setUTCDate(weekDate.getUTCDate()-((weekDate.getUTCDay()+6)%7));
  const week = weekDate.toISOString().slice(0,10);
  const post = (path,data,who=owner) => api(path,data,who.token,'POST',201);
  const consentCopy = '我已核对预览，确认应用以上变更。';
  let plan,line,task,todo,backup;
  const go = async (path,title) => {await route(c,path);if(title)await expect(heading(title)).toBeVisible();};
  const reopen = async (path,title) => {await restart(c);await go(path,title);};
  const fits = async () => expect(await page().evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);

  if (section === 'help') await scenario('Android 帮助定向续验：合成管理员登录',async()=>{await configure(c,admin);});
  if (section === 'all') {
  await scenario('Android 真实桥与候选版本，隔离账号登录',async()=>{
    await configure(c,owner);
    plan=await post('/plans',{name:'原生合成周期',startDate:day,endDate:shift(60),restDays:[7]});
    const tasks=await post(`/plans/${plan.id}/tasks/recurring`,{task:{title:'原生重复练习',date:shift(1),plannedMinutes:20,category:'STUDY',acceptance:['合成验收'],required:true,sortOrder:0},mode:'DAILY',until:shift(3)});
    task=tasks[0];
    line=await post('/mainlines',{name:'原生合成路线',color:'#002FA7',executionMode:'SEQUENTIAL'});
    for(let i=0;i<12;i++)await post(`/mainlines/${line.id}/items`,{title:`原生步骤${i}`,description:'合成内容',plannedMinutes:15,sortOrder:i,acceptance:[]});
    todo=await post('/todos',{title:'共同基础标题',note:'原备注',dueDate:day});
    await go('/help','帮助与版本');await button('检查服务器版本').click();
    await expect(page().getByText(/本次检查/)).toBeVisible();
    await expect(page().getByText('Android · 安装版本：0.1.15',{exact:true})).toBeVisible();await fits();
  });
  await scenario('反馈草稿保存重开、诊断白名单、同意后实际提交',async()=>{
    await go('/feedback','反馈与诊断');
    for(const [name,value] of Object.entries({'问题摘要':'Android 合成反馈','操作步骤':'打开候选测试页','期望的结果':'合成正常结果','实际的结果':'合成待核对结果'}))await label(name).fill(value);
    await button('生成并查看诊断摘要').click();
    const diagnostic=JSON.parse(await page().getByTestId('diagnostic-preview').innerText());
    expect(diagnostic.platform).toBe('ANDROID');
    expect(JSON.stringify(diagnostic)).not.toMatch(/example\.test|127\.0\.0\.1|accessToken|deviceId|共同基础标题/);
    await button('保存本机草稿').click();await expect(page().getByRole('status').filter({hasText:'反馈草稿已保存'})).toBeVisible();
    await reopen('/feedback','反馈与诊断');await expect(label('问题摘要')).toHaveValue('Android 合成反馈');
    await button('预览待发送内容').click();await expect(button('确认发送反馈')).toBeDisabled();
    await label('我已核对内容不含敏感信息，同意将以上内容发送给管理员。').check();await button('确认发送反馈').click();
    await expect(page().getByRole('status').filter({hasText:'反馈已发送'})).toBeVisible();
    const tickets=await api('/feedback',undefined,owner.token);expect(tickets.totalElements).toBe(1);
    const detail=await api('/feedback/'+tickets.content[0].id,undefined,owner.token);
    await api('/admin/feedback/'+detail.ticket.id,{status:'RESOLVED',body:'合成管理员已核对',version:detail.ticket.version},admin.token,'PUT');
    await button('读取最新记录（保留输入）').click();await expect(page().getByText('合成管理员已核对',{exact:true})).toBeVisible();
    await api('/feedback/'+detail.ticket.id,undefined,other.token,'GET',404);await fits();
  });
  await scenario('私有数据只读预检、同意后恢复新副本、重复确认不重复',async()=>{
    await api('/weekly/reviews/'+week,{wins:'合成源周复盘',blockers:'无',nextFocus:'小步推进'},other.token,'PUT');
    await post('/todos',{title:'备份合成待办',dueDate:day},other);
    backup=await api('/data/backup',undefined,other.token);
    await go('/exports','个人数据中心');
    // Supply an in-memory synthetic file through the real file input handler.
    // Android system picker and share chooser remain separate human/system gates.
    await label('选择私有 JSON 备份').evaluate((input,body)=>{const data=new DataTransfer();data.items.add(new File([JSON.stringify(body)],'synthetic-native-backup.json',{type:'application/json'}));input.files=data.files;input.dispatchEvent(new Event('change',{bubbles:true}));},backup);
    await label('我确认这是获准使用的私有备份，同意将文件发送到当前登录服务器进行只读预检。').check();
    await button('只读预检，不恢复数据').click();await expect(heading('预检通过 · 2 条记录')).toBeVisible();
    await expect(button('确认恢复为新副本')).toBeDisabled();
    const consent='我已核对以上数量和限制，确认恢复为新副本。周复盘保存在下方只读副本中，不覆盖当前周。';
    await label(consent).check();await button('确认恢复为新副本').click();await expect(page().getByRole('status').filter({hasText:/恢复批次 .* 已完成/})).toBeVisible();
    await label(consent).check();await button('确认恢复为新副本').click();await expect(page().getByText('共 1 批',{exact:true})).toBeVisible();
    expect((await api(`/todos?from=${day}&to=${day}`,undefined,owner.token)).map(x=>x.title)).toEqual(expect.arrayContaining(['共同基础标题','备份合成待办']));
    await page().getByText(`${week} 这一周 · 只读副本`,{exact:true}).click();await expect(page().getByText('合成源周复盘',{exact:true})).toBeVisible();await fits();
  });
  await scenario('结构复制预览确认、批量409保留草稿，不覆盖原任务',async()=>{
    await go('/organize','编排与历史');await label('来源').selectOption(plan.id);await button('预览结构复制').click();
    await expect(button('确认创建副本')).toBeDisabled();await label(consentCopy).check();await button('确认创建副本').click();
    await expect(page().getByRole('status')).toContainText('结构副本已创建');expect(await api('/plans',undefined,owner.token)).toHaveLength(2);
    await button('未来批量调整').click();await label('选择计划').selectOption(plan.id);await label('重复批次').selectOption(task.recurrenceId);await label('统一平移天数').fill('2');
    await button('预览批量差异').click();await expect(page().locator('.difference')).toHaveCount(3);
    await api(`/tasks/${task.id}/manual-time`,{seconds:5,version:task.version},owner.token,'POST');
    await label(consentCopy).check();await button('确认应用批量调整').click();await expect(page().getByRole('alert')).toContainText('预览已过期');
    await reopen('/organize','编排与历史');await expect(heading('上次提交结果待确认')).toBeVisible();await button('原样重试确认结果').click();await expect(page().getByRole('alert')).toContainText('预览已过期');await fits();
  });
  await scenario('长路线分页与暂停恢复、待办历史服务端查询',async()=>{
    await go('/organize','编排与历史');
    page().once('dialog',dialog=>dialog.accept());await button('已核对结果，重新预览').click();
    await button('长路线查询').click();await button('查看步骤').click();
    const panel=page().getByRole('region',{name:'分页步骤',exact:true});await expect(panel.locator('article')).toHaveCount(10);await panel.getByRole('button',{name:'下一页步骤',exact:true}).click();await expect(panel.locator('article')).toHaveCount(2);
    await expect(panel.getByText('原生步骤11',{exact:true})).toBeVisible();await button('暂停路线').click();await expect(button('恢复路线')).toBeEnabled();
    await reopen('/organize','编排与历史');await button('长路线查询').click();await expect(button('恢复路线')).toBeEnabled();await button('恢复路线').click();await expect(button('暂停路线')).toBeEnabled();
    await button('待办历史').click();await label('历史关键词').fill('备份合成待办');await button('查询待办历史').click();await expect(heading('备份合成待办')).toBeVisible();await fits();
  });
  await scenario('真实断网下周复盘本机草稿重开、云端变更人工核对',async()=>{
    await go('/weekly-review');await label('选择一周中的任意日期').fill(day);await button('查看这一周').click();await expect(label('本周收获')).toBeVisible();
    c.proxy.setOnline(false);await label('本周收获').fill('Android 尚未提交的收获');await button('保存本机草稿').click();await expect(page().getByRole('status')).toContainText('已存本机');
    await reopen('/weekly-review');await expect(label('本周收获')).toHaveValue('Android 尚未提交的收获');await expect(button('保存本周复盘')).toBeDisabled();
    await api('/weekly/reviews/'+week,{wins:'另一会话收获',blockers:'云端阻塞',nextFocus:'云端重点'},owner.token,'PUT');
    c.proxy.setOnline(true);await reopen('/weekly-review');await label('选择一周中的任意日期').fill(day);await button('查看这一周').click();
    await expect(heading('云端版本已变化，当前草稿仍保留')).toBeVisible();await expect(label('本周收获')).toHaveValue('Android 尚未提交的收获');
    page().once('dialog',dialog=>dialog.accept());await button('已比较，按最新版本准备保存').click();await button('保存本周复盘').click();await expect(page().getByRole('status')).toContainText('本周复盘已保存');await fits();
  });
  await scenario('真实离线队列409三方差异、人工重提并保留后续操作',async()=>{
    await go('/todos?date='+day);await expect(heading('共同基础标题')).toBeVisible();
    c.proxy.setOnline(false);await page().locator('.todo-row').filter({has:heading('共同基础标题')}).getByRole('button',{name:'编辑',exact:true}).click();
    await label('待办标题').fill('Android 人工选择标题');await button('保存待办').click();await expect(heading('Android 人工选择标题')).toBeVisible();
    await label('快速记录').fill('后续离线合成事项');await button('记一件事').click();
    await api('/todos/'+todo.id,{title:'云端选择标题',note:'保留云端备注',dueDate:day,done:false,deleted:false,version:todo.version},owner.token,'PUT');
    c.proxy.setOnline(true);await go('/sync','同步中心');await button('同步等待项').click();await expect(page().locator('.sync-row')).toHaveCount(2);await expect(page().locator('.sync-row--conflict')).toHaveCount(1);
    await button('比较并重新提交').click();await expect(heading('共同基础').first()).toBeVisible();await button('全部保留云端').click();
    await page().getByRole('combobox',{name:'标题处理方式',exact:true}).selectOption('LOCAL');
    await label('我已核对每个字段，同意用最新版本生成新的本机操作；原修改保留在追溯记录中。').check();await button('确认生成新的本机操作').click();
    await expect(page().locator('.sync-row')).toHaveCount(2);await button('同步等待项').click();await expect(page().getByText('队列为空',{exact:true})).toBeVisible();
    const rows=await api(`/todos?from=${day}&to=${day}`,undefined,owner.token);expect(rows.find(x=>x.id===todo.id)).toMatchObject({title:'Android 人工选择标题',note:'保留云端备注'});expect(rows.some(x=>x.title==='后续离线合成事项')).toBe(true);await fits();
  });
  await scenario('有限预加载与离线范围清单、断网重开仍可读',async()=>{
    await go('/offline');await label('预加载计划').selectOption(plan.id);await label('日程起始日期').fill(day);await button('预加载这 7 天').click();await expect(page().getByRole('status').filter({hasText:'已核对'})).toBeVisible();
    await page().getByRole('link',{name:'打开此日程窗口',exact:true}).click();await expect(page().locator('.rail-task')).toHaveCount(3);
    c.proxy.setOnline(false);await restart(c);await go('/offline');await expect(button('预加载这 7 天')).toBeDisabled();await expect(page().getByText('日程分页',{exact:true}).first()).toBeVisible();await fits();
    c.proxy.setOnline(true);await restart(c);
  });
  await scenario('管理员未知状态不伪健康、告警去重、只预览不删除',async()=>{
    // Wait for the restarted native identity, then use the real sign-out UI.
    // Saving the same server alone need not remove its existing refresh session.
    await expect(page().locator('.site-header .brand')).toBeVisible();
    await button('更多').click();await button(`${owner.name} · 退出`).click();
    await expect(heading('登录计划平台')).toBeVisible();
    await configure(c,admin);await go('/operations','运行与成员治理');await expect(heading('备份与主机状态')).toBeVisible();
    await expect(page().getByText(/状态未知，不能当作正常/)).toBeVisible();await button('核对并记录站内告警').click();await button('核对并记录站内告警').click();
    expect((await api('/admin/operations/alerts',undefined,admin.token)).length).toBe(1);
    await button('保留预览').click();await expect(heading('只预览，不执行删除')).toBeVisible();await expect(page().getByRole('button',{name:/执行删除|立即清理/})).toHaveCount(0);
    await label('至少保留最近份数').fill('3');await button('只保存预览规则').click();await expect(page().getByRole('status')).toContainText('只更新了预览规则');await fits();
  });
  await scenario('合成成员停用恢复确认、旧令牌拒绝、移动面板布局',async()=>{
    await button('成员状态').click();await label('搜索成员').fill(other.email);await button('查询成员').click();
    await button('停用成员').click();await expect(button('确认变更成员状态')).toBeDisabled();
    await label('变更原因').fill('合成真机状态验收');await label('我已确认目标账号与影响，执行本次状态变更。').check();await button('确认变更成员状态').click();
    await expect(button('恢复成员')).toBeEnabled();await api('/auth/me',undefined,other.token,'GET',401);
    await button('恢复成员').click();await label('变更原因').fill('合成验收恢复');await label('我已确认目标账号与影响，执行本次状态变更。').check();await button('确认变更成员状态').click();
    await expect(button('停用成员')).toBeEnabled();await api('/auth/me',undefined,other.token,'GET',401);await button('成员审计').click();await expect(page().getByText('合成验收恢复',{exact:true})).toBeVisible();await fits();
  });
  }
  await scenario('移动更多菜单与本地三模式演示不产生业务写入',async()=>{
    const before=(await api('/data/backup',undefined,admin.token)).records;
    await go('/help','帮助与版本');await button('更多').click();const menu=page().getByRole('dialog',{name:'更多功能',exact:true});await expect(menu).toBeVisible();
    for(const name of ['数据中心','编排与历史','离线范围','反馈与诊断','运行与治理'])await expect(menu.getByRole('link',{name,exact:true})).toBeVisible();
    await menu.getByRole('link',{name:'帮助与版本',exact:true}).click();await expect(menu).not.toBeVisible();
    await page().getByRole('link',{name:'开始 3—5 分钟演示',exact:true}).click();await expect(heading('本地演示')).toBeVisible();
    await page().getByLabel('阅读第一章并写三条笔记').check();await button('目标路线').click();await expect(page().getByText('认识琴弦与调音',{exact:true})).toBeVisible();await button('今日待办').click();
    await button('查看本地示例建议').click();await button('仅应用到演示').click();await expect(page().getByRole('status')).toContainText('没有修改真实计划');await button('重置本页演示').click();await expect(button('仅应用到演示')).toHaveCount(0);
    expect((await api('/data/backup',undefined,admin.token)).records).toEqual(before);await fits();
  });
}
