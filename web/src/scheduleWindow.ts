import type {PageResponse} from './types';
export const SCHEDULE_PAGE_SIZE=100,MAX_SCHEDULE_PAGES=5;
export async function schedulePages<T>(read:(page:number)=>Promise<PageResponse<T>>):Promise<T[]>{
  const first=await read(0);
  if(first.totalElements>MAX_SCHEDULE_PAGES*SCHEDULE_PAGE_SIZE||first.totalPages>MAX_SCHEDULE_PAGES)throw new Error('此 7 天范围超过 500 项任务，未标记为完整缓存。请在计划任务页分段查看，不会静默只显示前 100 项。');
  const result=[...first.content];
  for(let page=1;page<first.totalPages;page++){const next=await read(page);if(next.totalElements!==first.totalElements||next.totalPages!==first.totalPages)throw new Error('加载期间任务数量已变化，请重新读取此日期范围。');result.push(...next.content);}
  if(new Set(result.map(row=>(row as {id?:string}).id)).size!==result.length||result.length!==first.totalElements)throw new Error('分页读取未完整，请联网重新加载；不会把部分内容标记为完整。');
  return result;
}
