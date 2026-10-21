// Android-only targeted cases. Never replaces Java transport or mocks API calls.
export async function exerciseStableAndroid({client:c,owner,api,expect,route,restart,configure,scenario,adb}){
 if(c.kind!=='android')throw Error('Independent Android client required');
 const p=()=>c.page,button=name=>p().getByRole('button',{name,exact:true});
 const consent='我已核对完整结构、保存范围及新对象设置，确认继续。';
 const day='2026-11-01';let plan,templateId;
 await scenario('真实Java桥加载新能力与合成模板来源',async()=>{
  await configure(c,owner);
  plan=await api('/plans',{name:'安卓合成模板来源',startDate:day,endDate:'2026-11-07',restDays:[7]},owner.token,'POST',201);
  await api(`/plans/${plan.id}/tasks`,{title:'安卓合成结构任务',date:'2026-11-02',startTime:'09:30',endTime:'10:00',plannedMinutes:30,category:'STUDY',required:true,sortOrder:0,acceptance:[]},owner.token,'POST',201);
  await route(c,'/templates');await expect(p().getByRole('heading',{name:'个人整套模板库',exact:true})).toBeVisible();
  await expect(button('预览保存结构')).toBeEnabled();
  await p().getByRole('combobox',{name:'来源对象',exact:true}).selectOption(plan.id);
  await p().getByLabel('模板名称',{exact:true}).fill('安卓合成方法');await button('预览保存结构').click();
  const preview=p().getByRole('region',{name:'模板提交预览'});await expect(preview).toContainText('09:30');
  await expect(button('确认保存模板')).toBeDisabled();
 });
 await scenario('显式保存与建立新副本，来源和执行历史不变',async()=>{
  await p().getByRole('region',{name:'模板提交预览'}).getByLabel(consent,{exact:true}).check();await button('确认保存模板').click();
  await expect(p().getByRole('status')).toContainText('已完成');await button('查看保存的模板').click();
  const selected=p().getByRole('region',{name:'所选模板'});await selected.getByLabel('新对象名称',{exact:true}).fill('安卓模板独立新周期');
  await selected.getByLabel('新计划开始日期',{exact:true}).fill('2026-12-01');await selected.getByLabel('新计划结束日期',{exact:true}).fill('2026-12-07');await button('预览建立新副本').click();
  const preview=p().getByRole('region',{name:'模板提交预览'});await expect(preview).toContainText('2026-12-02');await expect(button('确认建立新副本')).toBeDisabled();
  await preview.getByLabel(consent,{exact:true}).check();await button('确认建立新副本').click();await expect(p().getByRole('link',{name:'打开本次结果',exact:true})).toBeVisible();
  const plans=await api('/plans',undefined,owner.token);expect(plans).toHaveLength(2);const copy=plans.find(x=>x.name==='安卓模板独立新周期');expect(copy.restDays).toEqual([7]);
  const tasks=await api(`/plans/${copy.id}/tasks`,undefined,owner.token);expect(tasks).toHaveLength(1);expect(tasks[0].startTime).toBe('09:30');expect(tasks[0].status).toBe('PENDING');
  expect((await api(`/plans/${plan.id}/tasks`,undefined,owner.token))[0].date).toBe('2026-11-02');
  templateId=(await api('/structure-templates',undefined,owner.token)).content[0].id;
  expect(await p().evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 });
 await scenario('第六课路线练习、保存重开和不写真实业务',async()=>{
  await route(c,'/learn/template');await expect(button('下一步')).toBeDisabled();
  await p().getByRole('combobox',{name:'练习结构来源',exact:true}).selectOption('ROUTE');await p().getByLabel('练习模板名称',{exact:true}).fill('安卓路线教学');await button('下一步').click();
  await p().getByLabel('我已核对练习结构和保存范围',{exact:true}).check();await button('保存为练习模板').click();await button('下一步').click();
  await p().getByLabel('练习副本名称',{exact:true}).fill('安卓练习副本');await button('保存练习').click();await expect(p().getByText('练习已保存在当前设备。',{exact:true})).toBeVisible();
  await restart(c);await route(c,'/learn/template');await expect(p().getByLabel('练习副本名称',{exact:true})).toHaveValue('安卓练习副本');
  await expect(p().getByLabel('确认建立练习副本，不修改来源',{exact:true})).not.toBeChecked();
  await p().getByLabel('确认建立练习副本，不修改来源',{exact:true}).check();await button('建立练习副本').click();await button('下一步').click();
  await button('修改来源标题（练习）').click();await button('完成副本第一项（练习）').click();await button('完成教学').click();await expect(p().getByRole('heading',{name:'整套模板教学已完成',exact:true})).toBeVisible();
  expect(await api('/plans',undefined,owner.token)).toHaveLength(2);expect(await api('/mainlines',undefined,owner.token)).toHaveLength(0);expect((await api('/structure-templates',undefined,owner.token)).totalElements).toBe(1);
 });
 await scenario('真实Android私有备份分享面板取消，导出含新模板',async()=>{
  await route(c,'/exports');await button('下载私有 JSON 备份').click();
  await expect.poll(async()=>(await adb('shell','dumpsys','activity','activities')).split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&/ChooserActivity|ResolverActivity/.test(line))).toBe(true);
  await adb('shell','input','keyevent','4');
  const files=(await adb('shell','run-as','cn.zijun701.plan.mobile.regression','ls','cache/exports')).split(/\s+/).filter(x=>/^plan-cabin-private-\d{8}T\d{6}\.json$/.test(x)).sort();
  const file=files.at(-1);expect(file).toBeTruthy();
  const backup=JSON.parse(await adb('shell','run-as','cn.zijun701.plan.mobile.regression','cat','cache/exports/'+file));
  expect(backup.schemaVersion).toBe(4);expect(backup.records.personal_templates).toHaveLength(1);expect(backup.records.personal_templates[0].id).toBe(templateId);
  await expect(p().getByRole('heading',{name:'个人数据中心',exact:true})).toBeVisible();
 });
}
