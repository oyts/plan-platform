<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue';
import {api,apiMessage,currentQueueOwner,serverScope} from '@/api/client';
import {cacheInventory,readResponse,type CacheEntry} from '@/offlineCache';
import {schedulePages,SCHEDULE_PAGE_SIZE} from '@/scheduleWindow';
import {requiresConnection} from '@/connectionState';
import {localDate,shiftDate} from '@/todos';
import type {PageResponse,Plan,Task} from '@/types';
const scope=serverScope(),owner=currentQueueOwner()??'',plans=ref<Plan[]>([]),entries=ref<CacheEntry[]>([]),plan=ref(''),start=ref(localDate()),busy=ref(false),error=ref(''),notice=ref('');let active=true;onUnmounted(()=>{active=false;});const context=()=>active&&owner===currentQueueOwner()&&scope===serverScope();
const labels=(path:string)=>path==='/plans'?'计划列表':path==='/mainlines'?'已加载路线':path==='/todos'?'已加载待办':path.startsWith('/weekly/')?'周复盘／统计':path.includes('/tasks/page')?'日程分页':path==='/tasks/carryover'?'延期任务':path.endsWith('/milestones')?'里程碑':path.includes('/tags')?'计划标签':path.startsWith('/daily/')?'今日与提醒':'其他已加载业务快照';
async function inspect(){try{const data=await cacheInventory(scope,owner);if(context())entries.value=data;}catch(e){error.value=apiMessage(e);}}
async function preload(){if(busy.value||requiresConnection()||!plan.value)return;busy.value=true;error.value='';notice.value='';const id=plan.value,from=start.value,to=shiftDate(from,6);const startedAt=Date.now();let reads=0;const required:{url:string;params?:unknown}[]=[{url:'/plans'},{url:`/plans/${id}/tags`},{url:`/plans/${id}/milestones`},{url:'/tasks/carryover',params:{from,to}}];
  try{
    for(const item of required){const response=await api.get(item.url,{params:item.params});if(response.headers['x-plan-local-cache'])throw new Error('连接中断，本次只读到旧快照，未确认完成预加载。');reads++;if(!context())return;}
    await schedulePages<Task>(async page=>{if(!context())throw new Error('账号或服务器已变化，预加载已停止。');const params={from,to,page,size:SCHEDULE_PAGE_SIZE},url=`/plans/${id}/tasks/page`;const response=await api.get<PageResponse<Task>>(url,{params});if(response.headers['x-plan-local-cache'])throw new Error('连接中断，未确认完成预加载。');const result=response.data;reads++;required.push({url,params});return result;});
    for(const item of required){const saved=await readResponse(scope,owner,item.url,item.params);if(!saved||saved.savedAt<startedAt)throw new Error('部分响应过大、缓存不可用或已被回收，本次未确认完整缓存。请联网打开日程核对。');}
    if(context())notice.value=`已核对 ${from} 至 ${to} 的日程窗口（${reads} 次读取）。仅此起始日期对应的 7 天窗口，不包含全账号、AI、计时结算或其他未访问页面。请用下方链接打开一次。`;
  }catch(e){if(context())error.value=apiMessage(e);}finally{if(context()){busy.value=false;await inspect();}}
}
onMounted(async()=>{await inspect();try{const response=(await api.get<Plan[]>('/plans')).data;if(context()){plans.value=response;plan.value=response[0]?.id??'';}}catch(e){error.value=apiMessage(e);}});
</script>
<template><div class="page offline-page"><header class="page-head"><div><h1>离线范围与预加载</h1><p>清楚看到当前设备已保存哪些页面。缓存不是完整备份，不会自动上传草稿或提交队列。</p></div><router-link class="btn" to="/sync">同步中心</router-link></header><p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
  <section class="section"><h2>准备一个日程窗口</h2><p>每次选择一个计划和起始日期，最多读取 7 天、500 项计划任务。休息日同样加载，不假定没有内容。预加载需要联网，AI、计时与账号安全不进行离线自动重放。</p><form @submit.prevent="preload"><label>预加载计划<select v-model="plan" aria-label="预加载计划" :disabled="busy"><option v-for="item in plans" :key="item.id" :value="item.id">{{item.name}}</option></select></label><label>日程起始日期<input v-model="start" type="date" required :disabled="busy"/></label><button class="btn primary" :disabled="busy||requiresConnection()||!plan">{{busy?'正在核对并缓存…':'预加载这 7 天'}}</button></form><router-link v-if="plan" class="btn" :to="{path:'/schedule',query:{plan,date:start}}">打开此日程窗口</router-link></section>
  <section class="section"><h2>当前设备缓存</h2><p>只列当前服务器和账号。最多保留 100 份查询、总计约 10 MiB；单份超过 1 MiB 不保存。快照最多保留 7 天，但离线登录身份仍只允许上次成功验证后的 24 小时，读取不会续期。</p><p>切换日期或筛选条件可能需要另一份缓存；本列表不承诺已缓存整套计划或完整路线。退出登录清除业务快照，不清除独立离线队列。</p><button class="btn" :disabled="busy" @click="inspect">刷新缓存清单</button><p>{{entries.length}} 份查询 · {{(entries.reduce((sum,item)=>sum+item.bytes,0)/1024).toFixed(1)}} KiB</p><article v-for="entry in entries" :key="entry.key"><h3>{{labels(entry.url)}}</h3><p v-if="entry.params?.from">日期范围：{{entry.params.from}} 至 {{entry.params.to}}<span v-if="entry.params.page!==undefined"> · 第 {{Number(entry.params.page)+1}} 页</span></p><p v-if="entry.params?.start">复盘周：{{entry.params.start}}</p><small>保存于 {{new Date(entry.savedAt).toLocaleString('zh-CN')}} · 快照到期 {{new Date(entry.expiresAt).toLocaleString('zh-CN')}}</small></article><p v-if="!entries.length">当前还没有可用快照。联网打开需要的页面，或选择上方日程预加载。</p></section>
</div></template>
<style scoped>
.offline-page .section{padding:24px;margin:24px 0}.offline-page form{display:grid;grid-template-columns:1fr 1fr auto;gap:16px;align-items:end;margin:24px 0}.offline-page label{display:grid;gap:8px}.offline-page article{border-top:1px solid #d8d8df;padding:16px 0}.offline-page article p{overflow-wrap:anywhere}.offline-page small{color:#646b77}@media(max-width:700px){.offline-page form{grid-template-columns:minmax(0,1fr)}.offline-page .section{padding:16px}.offline-page input,.offline-page select{min-width:0;max-width:100%}}
</style>
