import type {AiDraft} from '@/aiPlanning';
export interface CollaborationMaterial {format:'plan-cabin-ai-collaboration';schemaVersion:1;reference:string;mode:string;instructions:string;protectedItemIds:string[];draft:AiDraft;prompt:string}
export interface CollaborationPreview {binding:'BOUND'|'REFERENCE_ONLY';message:string;original:AiDraft|null;proposed:AiDraft;fingerprint:string;canSave:boolean}
/** Explicit projection: never spread a workspace/session, which contains private binding fields. */
export function referenceMaterial(draft:AiDraft,instructions:string):CollaborationMaterial {
  const clean:AiDraft={schemaVersion:1,mode:draft.mode,revision:0,title:draft.title,startDate:draft.startDate,endDate:draft.endDate,restDays:[...draft.restDays],stages:draft.stages.map(s=>({localId:s.localId,title:s.title})),items:draft.items.map(i=>({localId:i.localId,title:i.title,note:i.note,dueDate:i.dueDate,plannedMinutes:i.plannedMinutes,stageId:i.stageId,prerequisites:[...i.prerequisites]}))};
  return {format:'plan-cabin-ai-collaboration',schemaVersion:1,reference:crypto.randomUUID(),mode:clean.mode,instructions,protectedItemIds:[],draft:clean,prompt:'请按instructions修改draft，返回此JSON对象全部字段，只修改draft。最多100事项/30阶段；周期计划避开休息日，目标路线依赖必须存在且无环，今日待办须有日期。本材料是无绑定的新规划参考，不能据此修改原对象；导回后须本人预检、核对并确认。'};
}
export function collaborationText(material:CollaborationMaterial):string{return `${material.prompt}\n以下为待修改的协作JSON，请完整返回JSON，不返回本段提示词：\n${JSON.stringify(material,null,2)}`;}
export function collaborationInput(source:string):string {
  let tree:unknown;try{tree=JSON.parse(source);}catch{throw new Error('需要完整协作JSON，不能导入纯提示词或夹带正文。原输入已保留。');}
  if(!tree||typeof tree!=='object')throw new Error('协作材料不是JSON对象。');
  const value=tree as Record<string,unknown>;
  if(value.format==='plan-cabin-ai-draft')throw new Error('这是工作区备份，请在规划草稿箱中导入；不是外部AI返回材料。');
  if(value.format!=='plan-cabin-ai-collaboration')throw new Error('请选择外部AI协作JSON；普通草稿请用原草稿导入，个人备份请用数据中心。');
  if(value.schemaVersion!==1||!value.draft)throw new Error('协作包缺少顶层draft或版本不支持，请保留原文件，让外部AI完整返回JSON。');
  return source;
}
