export const adminNavigation=[
  {path:'/admin',label:'管理总览',description:'成员、请求和待处理事项'},
  {path:'/admin/members',label:'成员与邀请',description:'邀请、账号状态与治理审计'},
  {path:'/admin/ai',label:'AI资源与用量',description:'每日次数、运行策略与用量'},
  {path:'/admin/operations',label:'运行与安全',description:'备份、保留预览与邮件状态'},
  {path:'/admin/feedback',label:'反馈处理',description:'回复已主动提交的反馈'},
] as const;
export interface RuntimeLimits{maxConcurrent:number;queueCapacity:number;maxInputChars:number;maxOutputTokens:number;totalOutputTokens:number;timeoutSeconds:number}
export interface RuntimePolicy{limits:RuntimeLimits;version:number;updatedAt:string|null}
export interface RuntimeAudit{id:string;actor:string;before:RuntimePolicy;after:RuntimePolicy;restoredFrom:string|null;createdAt:string}
export const runtimeFields=[
  {key:'maxConcurrent',label:'全局并发上限',min:1,max:2},
  {key:'queueCapacity',label:'等待容量',min:0,max:8},
  {key:'maxInputChars',label:'调用上下文字符上限',min:100,max:32000},
  {key:'maxOutputTokens',label:'首轮输出Token上限',min:128,max:8192},
  {key:'totalOutputTokens',label:'首轮与修复合计输出Token上限',min:128,max:8192},
  {key:'timeoutSeconds',label:'首轮与修复合计超时（秒）',min:1,max:120},
] as const;
export function validRuntime(limits:RuntimeLimits){return runtimeFields.every(f=>Number.isSafeInteger(limits[f.key])&&limits[f.key]>=f.min&&limits[f.key]<=f.max)&&limits.totalOutputTokens>=limits.maxOutputTokens&&limits.totalOutputTokens<=limits.maxOutputTokens*2;}
export function runtimeDiff(before:RuntimeLimits,after:RuntimeLimits){return runtimeFields.filter(f=>before[f.key]!==after[f.key]).map(f=>({label:f.label,before:before[f.key],after:after[f.key]}));}
export function invitationState(row:{revokedAt:string|null;acceptedAt:string|null;expiresAt:string},now=Date.now()){return row.revokedAt?'已撤销':row.acceptedAt?'已接受':Date.parse(row.expiresAt)<=now?'已过期':'有效';}
export function invitationWait(retryAt?:string|null,now=Date.now()){return retryAt?Math.max(0,Math.ceil((Date.parse(retryAt)-now)/1000))||0:0;}
