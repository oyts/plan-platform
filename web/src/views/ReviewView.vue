<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { api, apiMessage } from '@/api/client';
import type { PageResponse, Plan, Reflection } from '@/types';

const plans = ref<Plan[]>([]);
const planId = ref('');
const reviewDate = ref(new Date().toLocaleDateString('sv-SE'));
const historyFrom = ref('');
const historyTo = ref('');
const history = ref<PageResponse<Reflection> | null>(null);
const current = ref<Reflection | null>(null);
const page = ref(0);
const size = ref(10);
const error = ref('');
const message = ref('');
const busy = ref(false);
const draft = reactive({ completedSummary: '', unfinishedSummary: '', blockers: '', firstTaskTomorrow: '', energy: null as number | null, note: '' });

function resetDraft(reflection: Reflection | null) {
  current.value = reflection;
  Object.assign(draft, reflection ? { completedSummary: reflection.completedSummary, unfinishedSummary: reflection.unfinishedSummary, blockers: reflection.blockers, firstTaskTomorrow: reflection.firstTaskTomorrow, energy: reflection.energy, note: reflection.note } : { completedSummary: '', unfinishedSummary: '', blockers: '', firstTaskTomorrow: '', energy: null, note: '' });
}
function historyStart(plan: Plan) { const end = new Date(`${plan.endDate}T00:00:00`); end.setDate(end.getDate() - 366); const candidate = end.toLocaleDateString('sv-SE'); return candidate > plan.startDate ? candidate : plan.startDate; }
async function loadPlans() {
  plans.value = (await api.get<Plan[]>('/plans')).data;
  if (!planId.value && plans.value[0]) { planId.value = plans.value[0].id; reviewDate.value = plans.value[0].startDate; historyFrom.value = historyStart(plans.value[0]); historyTo.value = plans.value[0].endDate; }
}
async function loadDay() {
  if (!planId.value || !reviewDate.value) return;
  try {
    const response = await api.get<PageResponse<Reflection>>(`/plans/${planId.value}/reflections/page`, { params: { from: reviewDate.value, to: reviewDate.value, page: 0, size: 1 } });
    resetDraft(response.data.content[0] ?? null);
  } catch (exception) { error.value = apiMessage(exception); }
}
async function loadHistory() {
  if (!planId.value || !historyFrom.value || !historyTo.value) return;
  try { history.value = (await api.get<PageResponse<Reflection>>(`/plans/${planId.value}/reflections/page`, { params: { from: historyFrom.value, to: historyTo.value, page: page.value, size: size.value } })).data; }
  catch (exception) { error.value = apiMessage(exception); }
}
async function save() {
  if (!planId.value) return;
  busy.value = true; error.value = ''; message.value = '';
  try {
    const response = await api.put<Reflection>(`/plans/${planId.value}/reflections/${reviewDate.value}`, { ...draft, version: current.value?.version });
    resetDraft(response.data); message.value = '当日复盘已保存。'; await loadHistory();
  } catch (exception) { error.value = apiMessage(exception); }
  finally { busy.value = false; }
}
function selectHistory(item: Reflection) { reviewDate.value = item.date; }
function changePlan() { const plan = plans.value.find(item => item.id === planId.value); if (!plan) return; reviewDate.value = plan.startDate; historyFrom.value = historyStart(plan); historyTo.value = plan.endDate; page.value = 0; }
watch(planId, changePlan);
watch(reviewDate, loadDay);
watch([planId, historyFrom, historyTo, page, size], loadHistory);
onMounted(async () => { try { await loadPlans(); await loadDay(); await loadHistory(); } catch (exception) { error.value = apiMessage(exception); } });
</script>

<template>
  <div class="page review-page">
    <header class="page-head"><div><p class="eyebrow">REVIEW / DAILY EVIDENCE</p><h1>每日复盘</h1><p>把完成结果、未完成原因和明日第一件事留下来，形成可回看的行动证据。</p></div></header>
    <p v-if="error" class="error-text">{{ error }}</p><p v-if="message" class="success-text">{{ message }}</p>
    <p v-if="!plans.length" class="empty">创建计划后，可以开始记录每日复盘。</p>
    <template v-else>
      <section class="review-controls"><label>计划<select v-model="planId"><option v-for="plan in plans" :key="plan.id" :value="plan.id">{{ plan.name }}</option></select></label><label>复盘日期<input v-model="reviewDate" type="date" /></label></section>
      <div class="review-layout">
        <form class="review-form" @submit.prevent="save"><p class="eyebrow">{{ reviewDate }}</p><h2>记录当天</h2><label>完成了什么<textarea v-model="draft.completedSummary" rows="3" placeholder="记录已完成的任务或关键结果。" /></label><label>哪些未完成<textarea v-model="draft.unfinishedSummary" rows="3" placeholder="记录未完成事项及其状态。" /></label><label>阻塞因素<textarea v-model="draft.blockers" rows="3" placeholder="记录需要解决的阻塞或风险。" /></label><label>明日第一件事<input v-model="draft.firstTaskTomorrow" placeholder="填写一个明确、可开始的动作。" /></label><label>精力评分<select v-model="draft.energy"><option :value="null">未评分</option><option :value="1">1 / 5</option><option :value="2">2 / 5</option><option :value="3">3 / 5</option><option :value="4">4 / 5</option><option :value="5">5 / 5</option></select></label><label>补充备注<textarea v-model="draft.note" rows="3" /></label><button class="btn primary" :disabled="busy">{{ busy ? '正在保存…' : '保存复盘' }}</button></form>
        <section class="review-history"><header><p class="eyebrow">HISTORY / PAGINATED</p><h2>复盘记录</h2></header><div class="history-filter"><label>开始<input v-model="historyFrom" type="date" :max="historyTo" /></label><label>结束<input v-model="historyTo" type="date" :min="historyFrom" /></label><label>每页<select v-model.number="size"><option :value="10">10 项</option><option :value="20">20 项</option><option :value="50">50 项</option></select></label></div><button v-for="item in history?.content" :key="item.id" class="review-history-row" type="button" :class="{ active: item.date === reviewDate }" @click="selectHistory(item)"><strong>{{ item.date }}</strong><span>{{ item.completedSummary || '未填写完成记录' }}</span><small>精力 {{ item.energy ?? '—' }}/5</small></button><p v-if="!history?.content.length" class="empty">所选范围没有已保存的复盘。</p><footer class="pagination-bar"><span>第 {{ (history?.page ?? 0) + 1 }} / {{ Math.max(history?.totalPages ?? 0, 1) }} 页</span><div class="button-row"><button class="btn" :disabled="!history || history.page === 0" @click="page--">上一页</button><button class="btn" :disabled="!history || history.page >= history.totalPages - 1" @click="page++">下一页</button></div></footer></section>
      </div>
    </template>
  </div>
</template>
