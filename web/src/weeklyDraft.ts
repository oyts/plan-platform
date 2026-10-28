export interface WeeklyLocalDraft{schemaVersion:1;weekStart:string;savedAt:string;baseVersion:number|null;content:{wins:string;blockers:string;nextFocus:string}}
export function weeklyReviewSelection(value:unknown,fallback:string):string{
  if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value))return fallback;
  const parsed=new Date(value+'T12:00:00Z');
  return Number.isFinite(parsed.getTime())&&parsed.toISOString().slice(0,10)===value?value:fallback;
}
// Only retain this explicit, non-sensitive native route across deferred auth restore.
export function weeklyNativeResumePath(hash:string):string|null{
  if(!hash.startsWith('#/weekly-review'))return null;
  try{const url=new URL(hash.slice(1),'https://native.invalid');
    if(url.origin!=='https://native.invalid'||url.pathname!=='/weekly-review'||url.hash)return null;
    if([...url.searchParams.keys()].some(key=>key!=='date')||url.searchParams.getAll('date').length>1)return null;
    const date=url.searchParams.get('date');
    if(date!==null&&(!date||weeklyReviewSelection(date,'')!==date))return null;
    return '/weekly-review'+(date===null?'':'?date='+date);
  }catch{return null;}
}
export function parseWeeklyDraft(value:unknown,weekStart:string):WeeklyLocalDraft|null{
  if(value===undefined||value===null)return null;const v=value as Partial<WeeklyLocalDraft>;
  if(v.schemaVersion!==1||v.weekStart!==weekStart||!v.content||!Number.isFinite(Date.parse(v.savedAt??''))||(v.baseVersion!==null&&(!Number.isSafeInteger(v.baseVersion)||v.baseVersion!<0))||!['wins','blockers','nextFocus'].every(key=>typeof v.content![key as keyof typeof v.content]==='string'&&String(v.content![key as keyof typeof v.content]).length<=4000))throw new Error('本机周复盘草稿格式无效；未覆盖原文件，请反馈此问题。');
  return{schemaVersion:1,weekStart,savedAt:v.savedAt!,baseVersion:v.baseVersion!,content:{wins:v.content.wins,blockers:v.content.blockers,nextFocus:v.content.nextFocus}};
}
