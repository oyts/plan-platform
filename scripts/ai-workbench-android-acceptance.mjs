import {randomUUID} from 'node:crypto';

// Android WebView only: never mock API routes or the Java transport.
export async function exerciseAndroidAiWorkbench({client:c,owner,api,expect,route,restart,configure,scenario,adb}){
  if(c.kind!=='android')throw Error('Independent Android client required.');
  const p=()=>c.page,button=name=>p().getByRole('button',{name,exact:true});
  const draftTab=()=>p().getByRole('navigation',{name:'AI 工作区',exact:true}).getByRole('button',{name:/^草稿/});
  let savedId;
  const title='Android合成吉他路线';
  await scenario('真实Java桥登录与独立云端草稿读取',async()=>{
    await configure(c,owner);
    const session=await api('/ai/sessions',{mode:'ROUTE',consent:true},owner.token,'POST');
    const draft={schemaVersion:1,mode:'ROUTE',revision:0,title,startDate:null,endDate:null,restDays:[],stages:[{localId:'base',title:'合成基础阶段'}],items:Array.from({length:21},(_,i)=>({localId:`s${i}`,title:`合成步骤${i+1}`,note:'仅用于测试',dueDate:null,plannedMinutes:15,stageId:'base',prerequisites:[]}))};
    const ready=await api(`/ai/sessions/${session.id}/draft`,{version:session.version,draftJson:JSON.stringify(draft)},owner.token,'PUT');
    savedId=randomUUID();
    await api(`/ai/saved-drafts/${savedId}`,{name:'Android工作台合成草稿',version:-1,contentJson:JSON.stringify({schemaVersion:1,mode:'ROUTE',message:'合成要求，未发送',importJson:'',draft:ready.draft,sourceSessionId:ready.id,sourceVersion:ready.version})},owner.token,'PUT');
    await route(c,'/ai');await expect(p().getByRole('heading',{name:'AI 规划助手',exact:true})).toBeVisible();
    await button('规划草稿箱 · 本机 0 / 云端 1').click();await button('查看／继续').click();
    await draftTab().click();await expect(p().getByLabel('规划名称',{exact:true})).toHaveValue(title);
    await expect(button('确认生成')).toBeDisabled();
  });
  await scenario('阶段分页使用全部315分钟且手机无横向溢出',async()=>{
    await expect(p().getByLabel('草稿路线参考投入')).toContainText('315 分钟');
    await expect(p().locator('.ai-draft-item')).toHaveCount(10);
    await button('下一组').click();await expect(p().getByLabel('任务标题',{exact:true}).first()).toHaveValue('合成步骤11');
    await expect(p().getByLabel('草稿路线参考投入')).toContainText('315 分钟');
    expect(await p().evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  });
  await scenario('真实软键盘输入、切换面板与本机自动保存',async()=>{
    const field=p().getByLabel('规划名称',{exact:true});await field.scrollIntoViewIfNeeded();await field.click();
    // ADB inspects only the boolean IME state; no keyboard text is recorded.
    await expect.poll(async()=>/mInputShown=true|isInputViewShown=true/.test(await adb('shell','dumpsys','input_method'))).toBe(true);
    await field.fill(title+'－本机修改');await button('对话').click();
    await expect(button('发送给 AI')).toBeDisabled();await draftTab().click();
    await expect(field).toHaveValue(title+'－本机修改');
    await expect(p().getByRole('status').filter({hasText:'已存本机'})).toBeVisible();
    await adb('shell','input','keyevent','4');
  });
  await scenario('进程重开恢复草稿且不恢复生成确认',async()=>{
    await restart(c);await route(c,'/ai');await button('恢复本机草稿').click();await draftTab().click();
    await expect(p().getByLabel('规划名称',{exact:true})).toHaveValue(title+'－本机修改');
    await expect(button('确认生成')).toBeDisabled();await button('保存到云端草稿箱').click();
    await expect(p().getByRole('status').filter({hasText:'已保存云端'})).toBeVisible();
    const result=await api(`/ai/saved-drafts/${savedId}`,undefined,owner.token);expect(result.content.draft.title).toBe(title+'－本机修改');
    expect(await api('/mainlines',undefined,owner.token)).toEqual([]);
  });
  await scenario('Android原生WebView精确配色与三位数字',async()=>{
    await route(c,'/plans');const picker=p().getByRole('group',{name:'标签颜色',exact:true});
    await picker.getByLabel('HEX',{exact:true}).fill('#00ff80');await picker.getByLabel('HEX',{exact:true}).blur();
    await expect(picker.getByLabel('标签颜色 G',{exact:true})).toHaveValue('255');
    await expect(picker.getByLabel('标签颜色 B',{exact:true})).toHaveValue('128');
    expect(await p().evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await picker.scrollIntoViewIfNeeded();
    expect(await picker.getByLabel('HEX',{exact:true}).evaluate(e=>e.clientWidth)).toBeGreaterThanOrEqual(120);
  });
  await scenario('真实Android草稿分享面板可唤起并取消，无外发',async()=>{
    await route(c,'/ai');await button('恢复本机草稿').click();
    const shelf=button('规划草稿箱 · 本机 1 / 云端 1');
    if(await shelf.getAttribute('aria-expanded')==='false')await shelf.click();
    await button('导出当前草稿 JSON').click();
    await expect.poll(async()=>/ChooserActivity|ResolverActivity/.test(await adb('shell','dumpsys','activity','activities'))).toBe(true);
    // Cancel the system chooser; no recipient/app is selected and no file is sent.
    await adb('shell','input','keyevent','4');
    // Autosave may replace the transient status. Validate the actual exported
    // synthetic file rather than treating a particular toast lifetime as proof.
    const exported=JSON.parse(await adb('shell','run-as','cn.zijun701.plan.mobile.regression','cat','cache/exports/plan-cabin-ai-draft.json'));
    expect(exported.format).toBe('plan-cabin-ai-draft');
    expect(exported.content.draft.title).toBe(title+'－本机修改');
    expect(exported.content.draft.items).toHaveLength(21);
    expect(Object.keys(exported.content).sort()).toEqual(['draft','importJson','message','mode','schemaVersion','sourceSessionId','sourceVersion']);
    await expect(p().getByRole('heading',{name:'AI 规划助手',exact:true})).toBeVisible();
  });
}
