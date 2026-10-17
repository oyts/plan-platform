<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { api, apiMessage, currentQueueOwner, serverScope } from '@/api/client';
import { cacheResponse } from '@/offlineCache';
import { requiresConnection } from '@/connectionState';
import { localDate } from '@/todos';
import { dailyItemDestination, reminderDefaults, type DailySummary, type DailyRoute, type ItemPage, type Reminders } from '@/dailyExperience';
import type { PageResponse } from '@/types';
const date=ref(localDate()), days=ref(3), bucket=ref('TODAY'), mode=ref('ALL'), page=ref(0);
const summary=ref<DailySummary|null>(null), items=ref<ItemPage|null>(null), routes=ref<PageResponse<DailyRoute>|null>(null), routePage=ref(0);
const loading=ref(false), error=ref(''), saving=ref(false), settingsReady=ref(false), settingsError=ref(''), message=ref('');
const settings=reactive(reminderDefaults()); const baseline=ref(''); let generation=0;
const dirty=computed(()=>settingsReady.value && JSON.stringify(settings)!==baseline.value);
const total=computed(()=> mode.value==='PLAN' ? summary.value?.plan : mode.value==='TODO' ? summary.value?.todo : summary.value ? {today:summary.value.plan.today+summary.value.todo.today,overdue:summary.value.plan.overdue+summary.value.todo.overdue,upcoming:summary.value.plan.upcoming+summary.value.todo.upcoming} : null);
async function load() {
  const request=++generation; loading.value=true; error.value=''; items.value=null;
  try {
    const params={date:date.value,days:days.value};
    const [counts,list]=(await Promise.all([api.get<DailySummary>('/daily/summary',{params}),api.get<ItemPage>('/daily/items',{params:{...params,bucket:bucket.value,mode:mode.value,page:page.value,size:20}})]));
    if(request===generation){summary.value=counts.data;items.value=list.data;}
  } catch(e){if(request===generation){summary.value=null;error.value=apiMessage(e);}} finally{if(request===generation)loading.value=false;}
}
async function loadRoutes(){try{routes.value=(await api.get<PageResponse<DailyRoute>>('/daily/routes',{params:{page:routePage.value,size:10}})).data;}catch(e){error.value=apiMessage(e);}}
async function loadSettings(){try{const value=(await api.get<Reminders>('/daily/reminders')).data;Object.assign(settings,value);baseline.value=JSON.stringify(settings);settingsReady.value=true;settingsError.value='';}catch(e){settingsError.value=apiMessage(e);}}
async function saveSettings(){
  if(saving.value)return;
  const owner=currentQueueOwner(),scope=serverScope();
  saving.value=true;settingsError.value='';message.value='';
  try{
    const value=(await api.put<Reminders>('/daily/reminders',{...settings})).data;
    if(owner)await cacheResponse(scope,owner,'/daily/reminders',undefined,value);
    Object.assign(settings,value);baseline.value=JSON.stringify(settings);message.value='站内提醒设置已保存，其他设备联网读取后生效。';
    window.dispatchEvent(new CustomEvent('plan-platform:reminders-changed'));
  }catch(e){settingsError.value=apiMessage(e);}finally{saving.value=false;}
}
function reloadSettings(){if(!dirty.value || confirm('读取云端设置将替换本页尚未保存的提醒设置，是否继续？'))void loadSettings();}
watch([date,days,bucket,mode],()=>{if(page.value===0)void load();else page.value=0;});watch(page,load);watch(routePage,loadRoutes);
function beforeUnload(event:BeforeUnloadEvent){if(dirty.value||saving.value){event.preventDefault();event.returnValue='';}}
onMounted(()=>{void load();void loadRoutes();void loadSettings();window.addEventListener('beforeunload',beforeUnload);});
onUnmounted(()=>{generation++;window.removeEventListener('beforeunload',beforeUnload);});
onBeforeRouteLeave(()=>!currentQueueOwner() || !saving.value && (!dirty.value || confirm('提醒设置尚未保存，确定离开吗？')));
</script>
<template><div class="page daily-overview">
  <header class="page-head"><div><h1>今日与提醒</h1><p>先看今天，再决定哪些事项需要改期。三种模式仍独立管理，不自动移动或修改任务。</p></div><router-link class="btn" to="/weekly-review">本周复盘</router-link></header>
  <p class="helper-text">汇总使用云端已同步数据；本机离线队列尚未提交的修改不会计入。离线时仅可查看已加载的快照。</p>
  <p v-if="requiresConnection()" role="status" class="helper-text">当前为离线快照，数量可能已变化；保存提醒设置需要联网。</p>
  <p v-if="error" role="alert" class="error-text">{{error}}</p>
  <section class="overview-controls" aria-label="事项筛选"><label>查看日期<input v-model="date" type="date" required/></label><label>模式<select v-model="mode"><option value="ALL">周期计划与今日待办</option><option value="PLAN">周期计划</option><option value="TODO">今日待办</option></select></label><label>提前查看<select v-model.number="days"><option :value="1">未来 1 天</option><option :value="3">未来 3 天</option><option :value="7">未来 7 天</option><option :value="14">未来 14 天</option></select></label><button class="btn" type="button" :disabled="loading" @click="load">刷新事项</button></section>
  <nav class="overview-counts" aria-label="事项日期分类"><button v-for="item in [{id:'TODAY',label:'今日待完成',value:total?.today},{id:'OVERDUE',label:'已逾期',value:total?.overdue},{id:'UPCOMING',label:'即将到期',value:total?.upcoming}]" :key="item.id" type="button" :aria-pressed="bucket===item.id" @click="bucket=item.id"><span>{{item.label}}</span><strong>{{item.value ?? '—'}}</strong></button></nav>
  <section aria-label="汇总事项列表" :aria-busy="loading"><p v-if="loading" role="status">正在读取事项…</p><p v-else-if="items && !items.content.length" class="empty">这个分类没有未完成事项。</p>
    <article v-for="item in items?.content" :key="`${item.kind}:${item.id}`" class="overview-row"><div><span class="tag">{{item.kind==='PLAN'?'周期计划':'今日待办'}}</span><h2>{{item.title}}</h2><p>{{item.dueDate}}<span v-if="item.parentName"> · {{item.parentName}}</span></p></div><router-link class="btn" :to="dailyItemDestination(item)">查看并处理</router-link></article>
    <footer v-if="items" class="pagination-bar"><span>共 {{items.totalElements}} 项 · 第 {{items.page+1}} / {{Math.max(1,items.totalPages)}} 页</span><div class="button-row"><button class="btn" :disabled="loading||page===0" @click="page--">上一页</button><button class="btn" :disabled="loading||page>=items.totalPages-1" @click="page++">下一页</button></div></footer>
  </section>
  <section class="section"><header class="section-title"><div><h2>目标路线</h2><p>没有截止日期，不标记逾期。有空时继续，步骤顺序仍由原路线规则决定。</p></div><router-link class="btn" to="/routes">路线工作区</router-link></header><p v-if="routes && !routes.content.length" class="empty">还没有未归档路线。</p>
    <article v-for="line in routes?.content" :key="line.id" class="overview-row"><div><h3>{{line.name}}</h3><p>已完成 {{line.completed}} / {{line.total}} 步</p></div><router-link class="btn" :to="{path:'/mainlines',query:{line:line.id}}">查看路线</router-link></article>
    <div v-if="routes && routes.totalPages>1" class="pagination-bar"><span>共 {{routes.totalElements}} 条路线</span><div class="button-row"><button class="btn" :disabled="routePage===0" @click="routePage--">上一组路线</button><button class="btn" :disabled="routePage>=routes.totalPages-1" @click="routePage++">下一组路线</button></div></div>
  </section>
  <section id="reminder-settings" class="section"><h2>站内提醒设置</h2><p>默认关闭。开启后，只在打开应用且联网时显示站内提示，不发送邮件、系统通知或后台推送。设置随账号同步；“今天不再提示”只影响当前设备。</p>
    <p v-if="settingsError" role="alert" class="error-text">{{settingsError}}</p><p v-if="message" role="status" class="success-text">{{message}}</p>
    <form v-if="settingsReady" class="reminder-form" @submit.prevent="saveSettings">
      <label class="check"><input v-model="settings.enabled" type="checkbox" :disabled="saving"/>启用站内提醒</label>
      <fieldset :disabled="saving"><legend>提醒哪些事项</legend>
        <label class="check"><input v-model="settings.includePlans" type="checkbox"/>周期计划任务</label>
        <label class="check"><input v-model="settings.includeTodos" type="checkbox"/>今日待办事项</label>
        <label class="check"><input v-model="settings.showOverdue" type="checkbox"/>包含逾期事项</label>
        <label>提前提醒天数<input v-model.number="settings.lookAheadDays" type="number" min="1" max="14" required/></label>
      </fieldset>
      <p v-if="settings.enabled&&!settings.includePlans&&!settings.includeTodos" class="error-text">请至少选择一种事项，或关闭提醒。</p>
      <div class="button-row"><button class="btn primary" :disabled="saving||requiresConnection()||(settings.enabled&&!settings.includePlans&&!settings.includeTodos)">{{saving?'正在保存…':'保存提醒设置'}}</button><button class="btn" type="button" :disabled="saving||requiresConnection()" @click="reloadSettings">读取云端设置</button></div>
    </form>
    <button v-else class="btn" @click="loadSettings">重新读取提醒设置</button>
  </section>
</div></template>
<style scoped>
.overview-controls{display:flex;align-items:end;gap:16px;flex-wrap:wrap;margin:24px 0}.overview-controls label{display:grid;gap:8px}.overview-counts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));border-top:1px solid #ddd;border-left:1px solid #ddd}.overview-counts button{padding:20px;background:#fff;text-align:left;border:0;border-right:1px solid #ddd;border-bottom:1px solid #ddd;display:grid;gap:12px;color:#333;cursor:pointer}.overview-counts button[aria-pressed=true]{color:#002fa7;box-shadow:inset 0 -3px #002fa7}.overview-counts strong{font-size:36px;font-weight:500}.overview-row{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:20px 0;border-bottom:1px solid #ddd}.overview-row h2,.overview-row h3{font-size:18px;margin:8px 0;overflow-wrap:anywhere}.overview-row p{color:#666;font-size:13px;margin:0}.overview-row .btn{flex-shrink:0}.reminder-form{display:grid;gap:20px;max-width:720px}.reminder-form fieldset{display:grid;gap:16px;border:1px solid #ddd;padding:20px}.reminder-form input[type=number]{max-width:160px}.reminder-form .check{display:flex;align-items:center;gap:10px}.daily-overview p{line-height:1.8}
@media(max-width:700px){.overview-controls{display:grid;grid-template-columns:minmax(0,1fr)}.overview-counts button{padding:16px 10px}.overview-counts strong{font-size:30px}.overview-counts span{font-size:13px}.overview-row{align-items:start;flex-direction:column;gap:12px}.overview-row .btn{width:100%}.pagination-bar{align-items:start;gap:12px;flex-direction:column}.reminder-form .button-row{flex-wrap:wrap}.reminder-form .btn{flex:1}.daily-overview .section-title{align-items:start;gap:12px;flex-direction:column}}
</style>
