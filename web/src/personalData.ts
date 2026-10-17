export const backupRecordLabels:Record<string,string>={plans:'周期计划',task_templates:'任务模板',plan_tags:'计划标签',plan_tasks:'周期任务',task_progress:'任务进度',time_entries:'计时流水',plan_milestones:'里程碑',daily_reflections:'每日复盘',mainlines:'目标路线',mainline_stages:'路线阶段',mainline_items:'路线步骤',todos:'今日待办',weekly_reviews:'周复盘及副本'};
export interface BackupPreview{checksum:string;counts:Record<string,number>;warnings:string[];totalRecords:number}
export interface RestoreReport{batchId:string;createdAt:string;counts:Record<string,number>;planIds:string[];routeIds:string[]}
export interface WeeklyCopy{id:string;batchId:string;weekStart:string;wins:string;blockers:string;nextFocus:string}
export const MAX_BACKUP_BYTES=5*1024*1024;
export function readPrivateBackup(text:string,bytes:number):unknown{
  if(bytes>MAX_BACKUP_BYTES)throw new Error('备份文件不能超过 5 MiB。');
  let value:unknown;try{value=JSON.parse(text.replace(/^\uFEFF/,''));}catch{throw new Error('请选择可读取的 JSON 备份文件。');}
  const data=value as {format?:string;schemaVersion?:number};
  if(!data||data.format!=='plan-cabin-private-backup'||data.schemaVersion!==1)throw new Error('仅支持计划舱私有备份 schemaVersion 1；旧版迁移文件请继续使用原迁移入口。');
  return value;
}
