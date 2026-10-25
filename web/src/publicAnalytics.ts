// Optional public-site analytics is deliberately separate from the auth client.
const CHOICE='plan-cabin.analytics.choice.v1',VISITOR='plan-cabin.analytics.daily.v1';
export function analyticsChoice(storage:Pick<Storage,'getItem'>|null){try{return storage?.getItem(CHOICE)==='yes';}catch{return false;}}
export function saveAnalyticsChoice(enabled:boolean,storage:Pick<Storage,'setItem'|'removeItem'>|null){try{storage?.setItem(CHOICE,enabled?'yes':'no');if(!enabled)storage?.removeItem(VISITOR);}catch{/* no persistent consent, no automatic opt-in next time */}}
export function dailyVisitor(storage:Pick<Storage,'getItem'|'setItem'>|null,now=new Date(),uuid:()=>string=()=>crypto.randomUUID()):string|null{
 const day=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(now);
 try{if(!storage)return null;let value:{day:string;id:string}|null=null;try{value=JSON.parse(storage.getItem(VISITOR)||'null');}catch{/* rotate malformed own key */}if(value?.day===day&&/^[a-f0-9-]{36}$/.test(value.id))return value.id;const id=uuid();storage.setItem(VISITOR,JSON.stringify({day,id}));return id;}catch{return null;}
}
export async function recordPublicView(storage:Storage|null,signal:AbortSignal,transport:typeof fetch=fetch){
 if(!analyticsChoice(storage))return 'declined';
 const options:RequestInit={method:'POST',credentials:'omit',cache:'no-store',redirect:'error',referrerPolicy:'origin',headers:{'Content-Type':'application/json'},signal};
 const config=await transport('/api/public/analytics/config',{credentials:'omit',cache:'no-store',redirect:'error',referrerPolicy:'origin',signal});if(!config.ok||!(await config.json()).enabled)return 'disabled';
 const response=await transport('/api/public/analytics/ticket',{...options,body:JSON.stringify({page:'WELCOME'})});if(!response.ok)return 'unavailable';const {ticket}=await response.json();if(signal.aborted||!analyticsChoice(storage))return 'declined';
 const result=await transport('/api/public/analytics/event',{...options,body:JSON.stringify({page:'WELCOME',ticket,visitorId:dailyVisitor(storage)})});return result.ok?'recorded':'unavailable';
}
