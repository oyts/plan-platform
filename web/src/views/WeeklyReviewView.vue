<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
import { api, apiMessage, currentQueueOwner, serverScope } from '@/api/client';
import { cacheResponse } from '@/offlineCache';
import { requiresConnection } from '@/connectionState';
import { localDate, shiftDate } from '@/todos';
import { mondayOf, recordedTime, type WeeklyReview, type WeekSummary } from '@/dailyExperience';
import type { PageResponse } from '@/types';
import {readPrivateDraft,writePrivateDraft,deletePrivateDraft} from '@/privateDrafts';
import {parseWeeklyDraft,weeklyReviewSelection,type WeeklyLocalDraft} from '@/weeklyDraft';
const owner=currentQueueOwner()??'',scope=serverScope();let active=true;const inContext=()=>active&&owner===currentQueueOwner()&&scope===serverScope();
const route=useRoute(),router=useRouter();
const selected=ref(weeklyReviewSelection(route.query.date,localDate())), start=ref(mondayOf(selected.value));
const summary=ref<WeekSummary|null>(null), current=ref<WeeklyReview|null>(null), history=ref<PageResponse<WeeklyReview>|null>(null), historyPage=ref(0);
const loading=ref(false), saving=ref(false), error=ref(''), notice=ref('');
const draft=reactive({wins:'',blockers:'',nextFocus:''}); const baseline=ref(JSON.stringify(draft));
const localDraft=ref<WeeklyLocalDraft|null>(null),localSaving=ref(false),baseVersion=ref<number|null>(null),storageError=ref('');
const localClean=ref('');const needsReview=computed(()=>!!current.value&&baseVersion.value!==current.value.version);
const dirty=computed(()=>JSON.stringify(draft)!==baseline.value);
let requestNumber=0;
function clean() { baseline.value=JSON.stringify(draft); }
async function load() {
  const request=++requestNumber;loading.value=true;error.value='';notice.value='';
  try {
    const week=start.value;let saved:WeeklyLocalDraft|null=null;storageError.value='';
    try{saved=parseWeeklyDraft(await readPrivateDraft(scope,owner,'weekly:'+week),week);}catch(e){storageError.value=apiMessage(e);}
    const [metrics,review]=await Promise.allSettled([api.get<WeekSummary>('/weekly/summary',{params:{start:week}}),api.get<WeeklyReview>(`/weekly/reviews/${week}`)]);
    if(request!==requestNumber||!inContext())return;
    if(metrics.status==='fulfilled')summary.value=metrics.value.data;
    if(review.status==='fulfilled'){current.value=review.value.data;Object.assign(draft,{wins:current.value.wins,blockers:current.value.blockers,nextFocus:current.value.nextFocus});clean();baseVersion.value=current.value.version;}
    // A failed refresh must not erase input or retarget its original version.
    localDraft.value=saved;localClean.value=saved?JSON.stringify(saved.content):'';
    if(saved){baseVersion.value=saved.baseVersion;Object.assign(draft,saved.content);notice.value='本机草稿已恢复，尚未提交到云端。';}
    if(review.status==='rejected')error.value=apiMessage(review.reason);else if(metrics.status==='rejected')error.value=apiMessage(metrics.reason);
  }catch(e){if(request===requestNumber)error.value=apiMessage(e);}finally{if(request===requestNumber)loading.value=false;}
}
async function loadHistory(){try{history.value=(await api.get<PageResponse<WeeklyReview>>('/weekly/reviews',{params:{page:historyPage.value,size:10}})).data;}catch(e){error.value=apiMessage(e);}}
function mayLeave(){return !currentQueueOwner() || !saving.value&&!localSaving.value && (!dirty.value||JSON.stringify(draft)===localClean.value || confirm('周复盘还有未保存到本机或云端的修改。确定离开吗？'));}
async function choose(value=selected.value){try{const week=mondayOf(value);if(route.query.date!==week){await router.replace({path:'/weekly-review',query:{...route.query,date:week}});if(route.query.date!==week)selected.value=start.value;return;}if(!mayLeave()){selected.value=start.value;return;}start.value=week;selected.value=week;current.value=null;summary.value=null;baseVersion.value=null;localDraft.value=null;localClean.value='';Object.assign(draft,{wins:'',blockers:'',nextFocus:''});clean();await load();}catch(e){error.value=apiMessage(e);}}
async function moveWeek(delta:number){await choose(shiftDate(start.value,delta*7));}
async function save(){
  if(!current.value||saving.value||needsReview.value||storageError.value)return;
  if(!inContext())return;const path=`/weekly/reviews/${start.value}`;
  saving.value=true;error.value='';notice.value='';
  try{
    const value=(await api.put<WeeklyReview>(path,{...draft,version:baseVersion.value})).data;
    if(!inContext())return;
    if(owner)await cacheResponse(scope,owner,path,undefined,value);
    current.value=value;
    baseVersion.value=value.version;
    Object.assign(draft,{wins:current.value.wins,blockers:current.value.blockers,nextFocus:current.value.nextFocus});clean();
    notice.value='本周复盘已保存，其他设备联网后可读取。';try{await deletePrivateDraft(scope,owner,'weekly:'+start.value);localDraft.value=null;localClean.value='';}catch{notice.value+='本机旧草稿暂未移除，请勿将其误认为新的云端版本。';}await loadHistory();
  }catch(e){error.value=apiMessage(e);}finally{saving.value=false;}
}
async function reread(){if(mayLeave())await load();}
async function saveLocal(){if(localSaving.value||saving.value||storageError.value||!inContext())return;localSaving.value=true;error.value='';try{const value:WeeklyLocalDraft={schemaVersion:1,weekStart:start.value,savedAt:new Date().toISOString(),baseVersion:baseVersion.value,content:{...draft}};await writePrivateDraft(scope,owner,'weekly:'+start.value,value);if(inContext()){localDraft.value=value;localClean.value=JSON.stringify(draft);notice.value='已存本机，关闭重开可恢复；尚未提交云端。';}}catch(e){error.value=apiMessage(e);}finally{localSaving.value=false;}}
function adoptLatest(){if(!current.value||!confirm('已比较下方云端内容与当前草稿？确认仅使用最新版本号，草稿文字不会自动合并，保存仍需你再次点击。'))return;baseVersion.value=current.value.version;notice.value='已选择按最新版本保存当前草稿；请再次核对后点击保存。';}
async function historyMove(delta:number){historyPage.value+=delta;await loadHistory();}
function beforeUnload(event:BeforeUnloadEvent){if((dirty.value&&JSON.stringify(draft)!==localClean.value)||saving.value||localSaving.value){event.preventDefault();event.returnValue='';}}
onBeforeRouteLeave(mayLeave);
onBeforeRouteUpdate(mayLeave);
onMounted(()=>{void load();void loadHistory();window.addEventListener('beforeunload',beforeUnload);});
onUnmounted(()=>{active=false;requestNumber++;window.removeEventListener('beforeunload',beforeUnload);});
</script>
<template><div class="page weekly-review">
  <header class="page-head"><div><h1>周复盘</h1><p>回顾三种模式的推进，把下周重点写清楚。不会自动改期、生成任务或调用 AI。</p></div><router-link class="btn" to="/overview">今日与提醒</router-link></header>
  <section class="weekly-controls" aria-label="选择复盘周"><label>选择一周中的任意日期<input v-model="selected" type="date" required/></label><button class="btn" :disabled="loading||saving" @click="choose()">查看这一周</button><button class="btn" :disabled="loading||saving" @click="moveWeek(-1)">前一周</button><button class="btn" :disabled="loading||saving" @click="moveWeek(1)">后一周</button></section>
  <p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="notice" class="success-text" role="status">{{notice}}</p>
  <p v-if="storageError" class="error-text" role="alert">{{storageError}}</p>
  <p v-if="localDraft" class="helper-text">本机草稿保存于 {{new Date(localDraft.savedAt).toLocaleString('zh-CN')}}，仅当前设备、服务器和账号可读取；不等于已跨端同步。</p>
  <p v-if="requiresConnection()" class="helper-text">正在查看已加载的离线快照；周复盘保存需要联网，当前输入不会加入任务同步队列。</p>
  <p v-if="loading" role="status">正在读取周复盘…</p>
  <template v-else-if="summary || current || localDraft">
    <section v-if="summary" class="weekly-summary" aria-label="本周概况"><h2>{{summary.start}} 至 {{summary.end}}</h2><p>以北京时间周一至周日为一周。统计按当前云端记录计算，不是冻结的历史快照。</p><div class="weekly-metrics"><article><span>周期计划本周完成</span><strong>{{summary.planCompleted}}</strong><small>本周安排 {{summary.planScheduled}} 项</small></article><article><span>今日待办本周完成</span><strong>{{summary.todoCompleted}}</strong><small>本周安排 {{summary.todoScheduled}} 项</small></article><article><span>目标路线本周完成</span><strong>{{summary.routeCompleted}}</strong><small>按实际完成时间统计步骤</small></article><article><span>周期计划记录投入</span><strong class="weekly-time">{{recordedTime(summary.recordedPlanSeconds)}}</strong><small>计时及手工补录的净记录</small></article></div>
      <p class="helper-text">计时流水按开始时间归属，手工补录或扣减按记录时间归属；包含已归档或已删除任务的保留流水，不估算路线和待办用时。正在运行而未结算的时间不计入。</p>
      <p class="helper-text">当前遗留：截至本周日已安排、仍未完成的周期任务 {{summary.openPlansThroughEnd}} 项，待办 {{summary.openTodosThroughEnd}} 项。包含更早遗留，不代表当时的历史状态。</p>
      <p v-if="summary.legacyCompletedTodosUntimed" class="helper-text">另有 {{summary.legacyCompletedTodosUntimed}} 项旧待办没有完成时间，无法判断完成周次，未计入本周完成数量。</p>
      <p class="helper-text">汇总于 {{new Date(summary.calculatedAt).toLocaleString('zh-CN')}}，仅含已同步数据。</p>
    </section>
    <div class="weekly-layout"><form class="weekly-editor" @submit.prevent="save">
      <h2>写下这一周</h2>
      <label>本周收获<textarea v-model="draft.wins" :disabled="saving" rows="5" maxlength="4000" placeholder="完成了什么，哪些做法值得继续？"/></label>
      <label>遗留与阻塞<textarea v-model="draft.blockers" :disabled="saving" rows="5" maxlength="4000" placeholder="哪些事项需要改期、缩小范围或寻求帮助？"/></label>
      <label>下周重点<textarea v-model="draft.nextFocus" :disabled="saving" rows="5" maxlength="4000" placeholder="写下少量具体、可开始的重点；不会自动生成任务。"/></label>
      <p v-if="dirty" class="helper-text">有尚未保存的修改。</p>
      <p v-if="current?.updatedAt" class="helper-text">上次保存：{{new Date(current.updatedAt).toLocaleString('zh-CN')}}</p>
      <section v-if="needsReview && current" class="weekly-conflict" aria-label="周复盘版本比较"><h3>云端版本已变化，当前草稿仍保留</h3><p>下方是最新云端文字；上面的编辑框是你的本机草稿。不会自动合并。</p><dl><dt>云端本周收获</dt><dd>{{current.wins||'未填写'}}</dd><dt>云端遗留与阻塞</dt><dd>{{current.blockers||'未填写'}}</dd><dt>云端下周重点</dt><dd>{{current.nextFocus||'未填写'}}</dd></dl><button class="btn" type="button" :disabled="requiresConnection()||saving" @click="adoptLatest">已比较，按最新版本准备保存</button></section>
      <div class="button-row"><button class="btn" type="button" :disabled="saving||localSaving||!!storageError" @click="saveLocal">{{localSaving?'正在存本机…':'保存本机草稿'}}</button><button class="btn primary" :disabled="saving||localSaving||requiresConnection()||!current||needsReview||!!storageError">{{saving?'正在保存…':'保存本周复盘'}}</button><button class="btn" type="button" :disabled="saving||localSaving||requiresConnection()" @click="reread">读取云端版本</button></div>
      <p class="helper-text">若另一台设备已修改，保存会被拦截且保留当前输入。请先复制需要保留的文字，再读取云端版本比较，不会自动覆盖。</p>
    </form>
    <section class="weekly-history"><h2>已保存的周复盘</h2><button v-for="item in history?.content" :key="item.weekStart" type="button" class="weekly-history-item" :aria-current="item.weekStart===start ? 'true' : undefined" :disabled="loading||saving" @click="choose(item.weekStart)"><strong>{{item.weekStart}} 这一周</strong><span>{{item.nextFocus||item.wins||'已保存，尚未填写重点'}}</span></button><p v-if="history && !history.content.length" class="empty">保存第一篇周复盘后，会在这里列出。</p><footer v-if="history" class="pagination-bar"><span>共 {{history.totalElements}} 周</span><div class="button-row"><button class="btn" :disabled="historyPage===0" @click="historyMove(-1)">上一页</button><button class="btn" :disabled="historyPage>=history.totalPages-1" @click="historyMove(1)">下一页</button></div></footer><router-link class="btn" to="/reviews">周期计划每日复盘</router-link></section></div>
  </template>
  <button v-if="!loading && (!summary||!current)" class="btn" @click="load">重新读取</button>
</div></template>
<style scoped>
.weekly-controls{display:flex;align-items:end;gap:12px;flex-wrap:wrap;margin:24px 0}.weekly-controls label,.weekly-editor label{display:grid;gap:10px}.weekly-summary{border-top:1px solid #ddd;border-bottom:1px solid #ddd;padding:20px 0}.weekly-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));border-top:1px solid #ddd;border-left:1px solid #ddd;margin:20px 0}.weekly-metrics article{display:grid;gap:12px;padding:20px;border-right:1px solid #ddd;border-bottom:1px solid #ddd}.weekly-metrics strong{font-size:40px;color:#002fa7;font-weight:500}.weekly-metrics .weekly-time{font-size:24px}.weekly-metrics small{color:#666;line-height:1.7}.weekly-layout{display:grid;grid-template-columns:3fr 2fr;gap:40px;margin:32px 0}.weekly-editor{display:grid;align-content:start;gap:20px;min-width:0}.weekly-editor textarea{width:100%;min-width:0}.weekly-history{min-width:0}.weekly-history-item{display:grid;gap:10px;text-align:left;width:100%;padding:20px 0;background:transparent;border:0;border-bottom:1px solid #ddd;cursor:pointer}.weekly-history-item[aria-current=true]{color:#002fa7}.weekly-history-item span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.weekly-review p{line-height:1.8}.weekly-history>.btn{margin-top:20px}
@media(max-width:800px){.weekly-layout{grid-template-columns:minmax(0,1fr);gap:24px}.weekly-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.weekly-metrics article{padding:16px}.weekly-controls{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.weekly-controls label{grid-column:1/-1}.weekly-metrics .weekly-time{font-size:20px}.weekly-editor .button-row{flex-wrap:wrap}.weekly-editor .btn{flex:1}.weekly-review .pagination-bar{flex-wrap:wrap;gap:12px}}
@media(max-width:480px){.weekly-metrics article:last-child{grid-column:1/-1}}
</style>
