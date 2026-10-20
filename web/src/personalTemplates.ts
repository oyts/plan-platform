export type TemplateKind='PLAN'|'ROUTE';
export type StructureRow=Record<string,string|number|boolean|null>;
export interface Blueprint{schemaVersion:1;kind:TemplateKind;parent:StructureRow;rows:Record<string,StructureRow[]>}
export interface TemplateCounts{items:number;stages:number;tags:number;milestones:number}
export interface PersonalTemplate{id:string;kind:TemplateKind;name:string;description:string;category:string;favorite:boolean;version:number;updatedAt:string;counts:TemplateCounts;blueprint:Blueprint|null}
export interface TemplatePreview{checksum:string;kind:TemplateKind;counts:TemplateCounts;blueprint:Blueprint;warnings:string[]}
export interface TemplateResult{id:string;kind:TemplateKind;path:string;count:number}
export interface TemplatePending{schemaVersion:1;url:string;body:Record<string,unknown>}
export function templatePending(value:unknown):TemplatePending|null{
 if(value==null)return null;const p=value as TemplatePending;
 if(p.schemaVersion!==1||typeof p.url!=='string'||!/^\/structure-templates\/(capture|[a-f0-9-]{36}\/apply)$/.test(p.url)||!p.body||typeof p.body!=='object'||p.body.confirmed!==true||typeof p.body.operationKey!=='string'||!/^[a-f0-9-]{36}$/.test(p.body.operationKey)||typeof p.body.checksum!=='string'||!/^[a-f0-9]{64}$/.test(p.body.checksum)||!p.body.request||typeof p.body.request!=='object')throw Error('本机模板提交记录无效，请保留数据并反馈；不会自动重试。');
 return p;
}
export function blueprintItems(b:Blueprint){return b.rows[b.kind==='PLAN'?'plan_tasks':'mainline_items']??[];}
export function templateDependencies(row:StructureRow,b:Blueprint){
 if(b.kind!=='ROUTE')return '';
 if(row.dependency_mode==='INHERIT')return b.parent.execution_mode==='SEQUENTIAL'?'依赖前序步骤':'自由推进';
 if(row.dependency_mode!=='EXPLICIT')return '无前置要求';
 try{const ids=JSON.parse(String(row.prerequisite_ids_json)) as string[];const titles=new Map(blueprintItems(b).map(x=>[x.id,x.title]));return ids.map(id=>titles.get(id)??'无法识别的前置步骤').join('、');}catch{return '前置关系无法读取，请重新加载';}
}
