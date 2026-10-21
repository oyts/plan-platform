import {writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
// Android-only targeted cases. Never replaces Java transport or mocks API calls.
export async function exerciseStableAndroid({client:c,owner,api,expect,route,restart,configure,scenario,adb,runDir,filesOnly=false,pickerOnly=false}){
 if(c.kind!=='android')throw Error('Independent Android client required');
 const p=()=>c.page,button=name=>p().getByRole('button',{name,exact:true});
 const consent='我已核对完整结构、保存范围及新对象设置，确认继续。';
 const day='2026-11-01';let plan,templateId,exportedJson;
 if(filesOnly){
  await configure(c,owner);
  plan=await api('/plans',{name:'安卓文件补验合成来源',startDate:day,endDate:'2026-11-07',restDays:[7]},owner.token,'POST',201);
  await api(`/plans/${plan.id}/tasks`,{title:'安卓文件补验合成任务',date:'2026-11-02',startTime:'09:30',endTime:'10:00',plannedMinutes:30,category:'STUDY',required:true,sortOrder:0,acceptance:[]},owner.token,'POST',201);
  const request={source:'PLAN',sourceId:plan.id,name:'文件补验合成模板',description:'仅本机测试',category:'',favorite:false,targetId:null,version:null};
  const preview=await api('/structure-templates/capture/preview',request,owner.token,'POST');
  await api('/structure-templates/capture',{request,checksum:preview.checksum,operationKey:randomUUID(),confirmed:true},owner.token,'POST');
  templateId=(await api('/structure-templates',undefined,owner.token)).content[0].id;
 }
 if(!filesOnly){
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
  const tasks=await api(`/plans/${copy.id}/tasks`,undefined,owner.token);expect(tasks).toHaveLength(1);expect(tasks[0].startTime).toMatch(/^09:30(?::00)?$/);expect(tasks[0].progress).toMatchObject({status:'PENDING',accumulatedSeconds:0,startedAt:null,completedAt:null});
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
 }
 if(pickerOnly){
  exportedJson=JSON.stringify(await api('/data/backup',undefined,owner.token));
  await route(c,'/exports');
 }
 if(!pickerOnly)await scenario('真实Android私有备份分享面板取消，导出含新模板',async()=>{
  await route(c,'/exports');await button('下载私有 JSON 备份').click();
  await expect.poll(async()=>(await adb('shell','dumpsys','activity','activities')).split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&/ChooserActivity|ResolverActivity/.test(line))).toBe(true);
  if(filesOnly){
   console.log('ANDROID_SHARE_HANDOFF: 请本人在系统分享面板取消并返回独立测试包；不需要向其他应用发送文件。');
   await expect.poll(async()=>(await adb('shell','dumpsys','activity','activities')).split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&line.includes('cn.zijun701.plan.mobile.regression/')),{timeout:180000,intervals:[1000]}).toBe(true);
   await writeFile(join(runDir,'share-handoff.json'),JSON.stringify({systemSheetObserved:true,cancelledByUser:true,testAppResumed:true,externalRecipientSelectedByAutomation:false},null,2));
  }else{
  // Wait for the actual system sheet, not merely an ActivityRecord during its
  // opening animation. Only a visible Cancel control may be tapped.
  const shareXmlPath='/data/local/tmp/plan-share-'+randomUUID()+'.xml';let shareXml;
  try{await adb('shell','uiautomator','dump',shareXmlPath);shareXml=await adb('shell','cat',shareXmlPath);}finally{await adb('shell','rm','-f',shareXmlPath);}
  const shareFocus=await adb('shell','dumpsys','window');
  if(!shareFocus.split(/\r?\n/).some(line=>/mCurrentFocus=/.test(line)&&/ChooserActivity|ResolverActivity/.test(line)))throw Error('System share sheet no longer focused; no native input');
  const cancelNodes=[...shareXml.matchAll(/<node\b[^>]*>/g)].map(m=>Object.fromEntries([...m[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]]))).filter(n=>['取消','Cancel','CANCEL'].includes(n.text));
  if(cancelNodes.length===1){
   const b=cancelNodes[0].bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/);if(!b)throw Error('Visible cancel button bounds missing');
   await adb('shell','input','tap',String(Math.floor((+b[1]+ +b[3])/2)),String(Math.floor((+b[2]+ +b[4])/2)));
  }else await adb('shell','input','keyevent','4');
  }
  await expect.poll(async()=>(await adb('shell','dumpsys','activity','activities')).split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&line.includes('cn.zijun701.plan.mobile.regression/'))).toBe(true);
  const files=(await adb('shell','run-as','cn.zijun701.plan.mobile.regression','ls','cache/exports')).split(/\s+/).filter(x=>/^plan-cabin-private-\d{8}T\d{6}\.json$/.test(x)).sort();
  const file=files.at(-1);expect(file).toBeTruthy();
  exportedJson=await adb('shell','run-as','cn.zijun701.plan.mobile.regression','cat','cache/exports/'+file);const backup=JSON.parse(exportedJson);
  expect(backup.schemaVersion).toBe(4);expect(backup.records.personal_templates).toHaveLength(1);expect(backup.records.personal_templates[0].id).toBe(templateId);
  await expect(p().getByRole('heading',{name:'个人数据中心',exact:true})).toBeVisible();
 });
 await scenario('真实Android系统文件选择器读取合成备份并只读预检',async()=>{
  if(!owner.email.endsWith('@example.test')||!exportedJson)throw Error('Only this run synthetic export may be selected');
  const name='PlanCabin-synthetic-'+randomUUID()+'.json',local=join(runDir,name),remote='/sdcard/Download/'+name;
  await writeFile(local,exportedJson,{flag:'wx'});let pushed=false;
  const pickerForeground=async()=>(await adb('shell','dumpsys','activity','activities')).split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&/com\.(android|google\.android)\.documentsui\//.test(line));
  async function nodes(){
   if(!await pickerForeground())throw Error('Expected only the Android system document picker before native input');
   const path='/data/local/tmp/plan-picker-'+randomUUID()+'.xml';let xml;
   try{
    const deadline=Date.now()+180000;let handedOff=false;
    do{
     await adb('shell','uiautomator','dump',path);xml=await adb('shell','cat',path);
     if(!xml.includes('package="com.vivo.cipherchain"'))break;
     if(!handedOff){console.log('ANDROID_PICKER_HANDOFF: 系统密码保险箱提示需本人处理；自动化不会点击该提示。');handedOff=true;}
     if(Date.now()>=deadline)throw Error('System credential prompt needs user handling; no native input');
     await new Promise(resolve=>setTimeout(resolve,1000));
    }while(true);
   }finally{await adb('shell','rm','-f',path);}
   if(!await pickerForeground()){
    const activity=await adb('shell','dumpsys','activity','activities');
    if(!activity.split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&line.includes('cn.zijun701.plan.mobile.regression/')))throw Error('System picker no longer foreground');
    // The human may have returned to the test app after dismissing an OEM
    // credential prompt. Reopen only our observed file input, never that prompt.
    await p().getByLabel('选择私有 JSON 备份',{exact:true}).click();
    await expect.poll(pickerForeground).toBe(true);
    await adb('shell','uiautomator','dump',path);xml=await adb('shell','cat',path);
    await adb('shell','rm','-f',path);
    if(!await pickerForeground()||xml.includes('package="com.vivo.cipherchain"'))throw Error('Picker still blocked; no native input');
   }
   return [...xml.matchAll(/<node\b[^>]*>/g)].map(m=>Object.fromEntries([...m[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]]))).filter(n=>/com\.(android|google\.android)\.documentsui/.test(n.package||''));
  }
  async function tapObserved(labels){
   const entries=await nodes();const matches=entries.filter(n=>labels.includes(n.text)||labels.includes(n['content-desc']));
   if(matches.length!==1)return false;
   const b=matches[0].bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/);if(!b||+b[3]<=+b[1]||+b[4]<=+b[2])throw Error('Picker item has no visible bounds');
   await adb('shell','input','tap',String(Math.floor((+b[1]+ +b[3])/2)),String(Math.floor((+b[2]+ +b[4])/2)));return true;
  }
  try{
   await adb('push',local,remote);pushed=true;
   await p().getByLabel('选择私有 JSON 备份',{exact:true}).click();await expect.poll(pickerForeground).toBe(true);
   if(pickerOnly){
    console.log('ANDROID_FILE_SELECTION_HANDOFF: 请本人处理系统提示并选择下载目录中唯一文件 '+name+'；不选择其他私人文件。');
    await expect(p().locator('.data-file')).toHaveText(name,{timeout:180000});
    await writeFile(join(runDir,'picker-user-handoff.json'),JSON.stringify({generatedSyntheticFile:name,selectedByUser:true,precheckAutomated:true,fixtureFromApi:true},null,2));
   }else{
   if(!await tapObserved([name])){
    if(!await tapObserved(['显示根目录','Show roots']))throw Error('System document drawer was not uniquely observed; no guessed tap');
    if(!await tapObserved(['下载','下载内容','Downloads']))throw Error('System Downloads location was not uniquely observed');
    if(!await tapObserved([name]))throw Error('Only the generated synthetic file may be selected; it was not visible');
   }
   }
   await expect(p().locator('.data-file')).toHaveText(name);
   await expect(button('只读预检，不恢复数据')).toBeDisabled();
   await p().getByLabel('我确认这是获准使用的私有备份，同意将文件发送到当前登录服务器进行只读预检。',{exact:true}).check();
   await button('只读预检，不恢复数据').click();await expect(p().getByRole('region',{name:'备份预检结果'})).toContainText('预检通过');
   expect(await api('/plans',undefined,owner.token)).toHaveLength(filesOnly?1:2);expect((await api('/structure-templates',undefined,owner.token)).totalElements).toBe(1);
  }finally{
   if(pushed)await adb('shell','rm','-f',remote);
  }
 });
}
