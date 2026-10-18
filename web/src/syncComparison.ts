export const editableFields:Record<string,string[]>={
  plans:['name','description','startDate','endDate','restDays'],tags:['name','color'],
  tasks:['tagId','date','startTime','endTime','plannedMinutes','category','title','description','acceptance','resourceUrl','required','sortOrder'],
  milestones:['name','date','description','acceptance'],templates:['name','category','title','description','plannedMinutes','acceptance','resourceUrl','required'],
  mainlines:['name','description','color'],'mainlines/items':['title','description','acceptance','plannedMinutes','sortOrder'],todos:['title','note','dueDate','done','deleted'],
};
export const fieldLabels:Record<string,string>={name:'名称',description:'说明',startDate:'开始日期',endDate:'结束日期',restDays:'休息日（1—7）',color:'颜色',tagId:'标签编号',date:'安排日期',startTime:'开始时间',endTime:'结束时间',plannedMinutes:'计划分钟',category:'分类',title:'标题',acceptance:'验收标准',resourceUrl:'资料链接',required:'计入核心完成率',sortOrder:'排序',note:'备注',dueDate:'待办日期',done:'完成状态',deleted:'回收站状态'};
export interface EditableSnapshot{version:number;fields:Record<string,unknown>}
export function recordKind(path:string){const match=path.match(/^\/(plans|tags|tasks|milestones|templates|mainlines(?:\/items)?|todos)\/[a-zA-Z0-9-]+$/);return match?.[1]??null;}
export function editableRecord(kind:string,value:Record<string,unknown>):Record<string,unknown>|null{
  const distinguishing:Record<string,string[]>={plans:['startDate','endDate','taskCount'],tasks:['planId','progress'],tags:['overall'],milestones:['planId','checks'],templates:['name','category','title'],mainlines:['items','completed'],'mainlines/items':['unlocked','status'],todos:['dueDate','done','deleted']};
  if(!(distinguishing[kind]??[]).every(key=>key in value))return null;
  return Object.fromEntries((editableFields[kind]??[]).filter(key=>key in value).map(key=>[key,value[key]]));
}
export function comparisonRows(path:string,local:unknown,cloud:EditableSnapshot,base?:EditableSnapshot){
  const kind=recordKind(path);if(!kind||!local||typeof local!=='object'||!Number.isSafeInteger(cloud.version))throw new Error('此记录暂不支持安全重提。');
  if(Object.keys(local).some(key=>key!=='version'&&!editableFields[kind].includes(key)))throw new Error('此操作涉及拓扑或其他在线功能，请保留内容并到原页面处理。');
  const body=local as Record<string,unknown>;
  return editableFields[kind].filter(key=>key in cloud.fields).map(key=>({key,label:fieldLabels[key]??key,local:key in body?body[key]:cloud.fields[key],cloud:cloud.fields[key],base:base?.fields[key],hasBase:!!base&&key in base.fields,changedLocally:!!base&&key in base.fields&&key in body&&JSON.stringify(body[key])!==JSON.stringify(base.fields[key])}));
}
export function parseEditedValue(text:string,reference:unknown){
  if(Array.isArray(reference)){const value:unknown=JSON.parse(text);if(!Array.isArray(value)||value.length>100||value.some(item=>typeof item!=='string'&&typeof item!=='number'))throw new Error('列表请填写 JSON 数组，例如 ["练习一项"] 或 [7]。');return value;}
  if(typeof reference==='number'){const value=Number(text);if(!Number.isSafeInteger(value))throw new Error('请填写有效整数。');return value;}
  if(typeof reference==='boolean'){if(text!=='true'&&text!=='false')throw new Error('请选择 true 或 false。');return text==='true';}
  return text===''&&reference===null?null:text;
}
export function readableValue(value:unknown){if(value===null)return '未设置';if(value===undefined)return '无共同基础';if(typeof value==='boolean')return value?'是':'否';if(Array.isArray(value))return JSON.stringify(value);return String(value);}
