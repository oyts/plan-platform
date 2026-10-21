export const capabilityGuide=[
  {name:'三种规划模式',scope:'Web · Windows · Android',detail:'周期计划按日期推进；目标路线不强加截止日期；今日待办记录临时事项。',path:'/learn'},
  {name:'AI 规划与调整',scope:'联网使用',detail:'先选择发送内容并同意，再讨论、预览、手改和确认。服务不可用时仍可手工使用或导入外部 AI JSON。',path:'/ai'},
  {name:'个人数据中心',scope:'联网导出与恢复',detail:'只导出已同步的本人内容。私有 JSON 先预检，确认后恢复为新记录，不覆盖原内容。文件含隐私，请妥善保存。',path:'/exports'},
  {name:'整套复制与批量调整',scope:'联网预览后确认',detail:'复制结构不复制执行历史；未来实例调整保护已开始和已完成内容，版本冲突整批拒绝。',path:'/organize'},
  {name:'离线与本机草稿',scope:'有缓存才可读取',detail:'有限预加载；支持的普通编辑和待办勾选可入队。AI、计时及计划／路线执行状态需联网。本机草稿不等于云端同步，离线身份最多24小时。',path:'/offline'},
  {name:'冲突处理',scope:'人工选择，不静默合并',detail:'有可靠基础版本才展示三方差异；确认后生成新修改，保留原操作记录，再由本人同步。',path:'/sync'},
  {name:'系统提醒',scope:'每台设备主动开启',detail:'Windows和Android支持提前、到点和待办收尾提醒；默认关闭，受系统权限和节电策略影响，不能保证强停或关机后送达。网页保留站内提醒。',path:'/reminders'},
  {name:'日周复盘',scope:'事实与自述分开',detail:'汇总三模式已同步事实，再记录收获和精力；AI分析先预览同意。周节奏建议须另行核对确认，不自动重排。',path:'/reflection'},
  {name:'个人整套模板',scope:'需要服务器支持',detail:'保存本人计划、路线或完整新建AI草稿的结构。预览后建立独立副本，不复制执行历史，不改已有副本。',path:'/templates'},
  {name:'反馈与账号安全',scope:'最少必要信息',detail:'反馈诊断默认不附带，提交前预览；不提交密码、邀请链接或 AI 原文。邮件恢复需要服务器启用。',path:'/feedback'},
] as const;
export function healthSummary(value:unknown):{version:string;apiVersion:number}{
  if(!value||typeof value!=='object')throw new Error('健康接口格式不兼容。');
  const h=value as Record<string,unknown>;
  if(h.service!=='plan-platform-api'||h.ok!==true||typeof h.version!=='string'||!/^\d+\.\d+\.\d+$/.test(h.version)||!Number.isInteger(h.apiVersion)||h.apiVersion!==1)throw new Error('服务器版本或 API 不兼容，请检查连接与部署。');
  return {version:h.version,apiVersion:h.apiVersion as number};
}
export function supportsPersonalTemplates(value:unknown):boolean{
  healthSummary(value);
  const capabilities=(value as Record<string,unknown>).capabilities;
  return !!capabilities&&typeof capabilities==='object'&&!Array.isArray(capabilities)&&(capabilities as Record<string,unknown>).structureTemplates===true;
}
export const demoCases={
  plan:{name:'演示：两周阅读',description:'固定日期与星期日休息，适合有规律的安排。',items:['阅读第一章并写三条笔记','整理章节提纲']},
  route:{name:'演示：从零认识吉他',description:'按阶段推进，不要求每天完成，也不设置结束日期。',items:['认识琴弦与调音','练习单音','尝试开放和弦']},
  todo:{name:'演示：今天的小事',description:'随手记录临时事项，不必先创建长期计划。',items:['整理一份资料','寄出资料']},
} as const;
