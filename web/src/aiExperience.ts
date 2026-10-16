import type {AiUsage} from './aiQuota';
export function aiFailureGuidance(state:string,code:string|null):string{
  if(state==='QUEUED')return '请求已排队，无需重复发送。离开后可从“最近对话”继续查看。';
  if(state==='RUNNING')return '正在生成回复，不代表已经创建计划；收到草稿后还需你确认。';
  if(state==='UNKNOWN')return '上游结果无法确认，额度暂时占用。请先刷新核对或联系管理员，不要连续重发；不会自动重复调用或自动退回。';
  if(state!=='FAILED')return '';
  const reason=({INVALID_CONFIG:'服务端调用配置尚未就绪，请联系管理员。',INVALID_OUTPUT:'回复未通过草稿校验，可补充更明确的日期和步骤要求。',BUSY:'服务繁忙，请稍后再试。',DISABLED:'AI 当前未启用，可继续手动规划。',TIMEOUT:'请求超时。',UPSTREAM_UNAVAILABLE:'上游暂时不可用。'} as Record<string,string>)[code??'']??'本次没有获得有效回复。';
  return reason+' 本次额度已退回，已有草稿保留；不会自动重新调用。';
}
export function aiQuotaHint(quota:AiUsage|null):string{
  if(!quota)return '正在读取每日额度。';
  if(quota.paused)return '管理员已暂停你的 AI 新请求。手动规划、已保存草稿与既有计划仍可使用。';
  if(quota.remaining>0)return '发送获得成功回复计 1 次；草稿预览、手动修改与教学不扣次数。';
  const reset=new Date(quota.resetsAt);const when=Number.isNaN(reset.getTime())?'次日零点':reset.toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false});
  return `当前没有可用次数，按北京时间 ${when} 更新每日额度；也可联系管理员。手动规划与已有草稿不受影响。`;
}
export function aiSubmitUncertain(reason:unknown):boolean{
  const r=reason as {response?:{status?:number;data?:{code?:string}}};const status=r?.response?.status;
  if(status&&[400,401,403,404,409,413,422,429].includes(status))return false;
  if(status===503&&['DISABLED','BUSY','INVALID_CONFIG','NOT_IMPLEMENTED'].includes(r.response?.data?.code??''))return false;
  return true;
}
