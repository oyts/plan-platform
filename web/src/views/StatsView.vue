<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { api, apiMessage } from '@/api/client';
import type { DailyStat, PageResponse, Plan, Stats } from '@/types';

const plans = ref<Plan[]>([]);
const planId = ref('');
const from = ref('');
const to = ref('');
const page = ref(0);
const size = ref(14);
const summary = ref<Stats | null>(null);
const ledger = ref<PageResponse<DailyStat> | null>(null);
const error = ref('');
const loading = ref(false);

function duration(seconds: number) { const h = Math.floor(seconds / 3600); const m = Math.floor(seconds % 3600 / 60); return `${h} 小时 ${m} 分`; }
function percent(actual: number, planned: number) { return planned ? Math.round(actual / (planned * 60) * 100) : 0; }
const activePlan = computed(() => plans.value.find(plan => plan.id === planId.value) ?? null);

async function loadPlans() {
  plans.value = (await api.get<Plan[]>('/plans')).data;
  const plan = plans.value[0];
  if (plan && !plans.value.some(item => item.id === planId.value)) {
    planId.value = plan.id;
    from.value = plan.startDate;
    to.value = plan.endDate;
  }
}
async function load() {
  if (!planId.value || !from.value || !to.value) return;
  loading.value = true; error.value = '';
  try {
    const [stats, daily] = await Promise.all([
      api.get<Stats>(`/plans/${planId.value}/stats`, { params: { from: from.value, to: to.value } }),
      api.get<PageResponse<DailyStat>>(`/plans/${planId.value}/stats/daily`, { params: { from: from.value, to: to.value, page: page.value, size: size.value } }),
    ]);
    summary.value = stats.data; ledger.value = daily.data;
  } catch (exception) { error.value = apiMessage(exception); }
  finally { loading.value = false; }
}
function changePlan() {
  const plan = activePlan.value;
  if (!plan) return;
  from.value = plan.startDate; to.value = plan.endDate; page.value = 0;
}
function setPage(next: number) { if (!ledger.value || next < 0 || next >= ledger.value.totalPages) return; page.value = next; }
watch(planId, changePlan);
watch([planId, from, to, page, size], load);
onMounted(async () => { try { await loadPlans(); await load(); } catch (exception) { error.value = apiMessage(exception); } });
</script>

<template>
  <div class="page stats-page">
    <header class="page-head"><div><p class="eyebrow">STATS / EVIDENCE</p><h1>计划与实际</h1><p>以任务状态、计划时长与服务器计时流水核对真实投入。</p></div><span v-if="summary" class="stats-range">{{ summary.from }} — {{ summary.to }}</span></header>
    <p v-if="error" class="error-text">{{ error }}</p>
    <section v-if="plans.length" class="stats-filter-bar">
      <label>计划<select v-model="planId"><option v-for="plan in plans" :key="plan.id" :value="plan.id">{{ plan.name }}</option></select></label>
      <label>开始<input v-model="from" type="date" :min="activePlan?.startDate" :max="to" /></label>
      <label>结束<input v-model="to" type="date" :min="from" :max="activePlan?.endDate" /></label>
      <label>每页<select v-model.number="size"><option :value="7">7 天</option><option :value="14">14 天</option><option :value="28">28 天</option></select></label>
    </section>
    <p v-else-if="!loading" class="empty">创建计划后，统计会按服务器数据自动生成。</p>
    <template v-if="summary">
      <section class="metric-grid stats-metric-grid">
        <article class="metric metric--primary"><span>实际投入</span><strong>{{ duration(summary.actualSeconds) }}</strong><small>{{ summary.taskCount }} 项任务</small></article>
        <article class="metric"><span>核心完成</span><strong>{{ summary.completedCount }}/{{ summary.taskCount }}</strong><small>完成率 {{ summary.taskCount ? Math.round(summary.completedCount / summary.taskCount * 100) : 0 }}%</small></article>
        <article class="metric"><span>计划时长</span><strong>{{ summary.plannedMinutes }} 分</strong><small>实际 / 计划 {{ percent(summary.actualSeconds, summary.plannedMinutes) }}%</small></article>
      </section>
      <section class="stats-panel">
        <header class="section-title"><div><p class="eyebrow">TIME BY TAG</p><h2>标签投入</h2></div><span>{{ summary.tags.length }}</span></header>
        <div v-if="summary.tags.length" class="tag-ledger">
          <article v-for="tag in summary.tags" :key="tag.tagId" class="tag-ledger-row"><i :style="{ background: tag.color ?? '#667085' }" /><strong>{{ tag.tagName }}</strong><span>{{ tag.completedCount }}/{{ tag.taskCount }} 完成</span><span>计划 {{ tag.plannedMinutes }} 分</span><b>{{ duration(tag.actualSeconds) }}</b></article>
        </div>
        <p v-else class="empty">所选范围没有任务数据。</p>
      </section>
      <section class="stats-panel daily-panel">
        <header class="section-title"><div><p class="eyebrow">DAILY LEDGER / PAGINATED</p><h2>逐日计划与实际</h2></div><span>{{ ledger?.totalElements ?? 0 }} 天</span></header>
        <div class="table-scroll"><table><thead><tr><th>日期</th><th>任务</th><th>完成</th><th>计划</th><th>实际</th><th>状态</th></tr></thead><tbody><tr v-for="row in ledger?.content" :key="row.date"><td>{{ row.date }}</td><td>{{ row.taskCount }}</td><td>{{ row.completedCount }}</td><td>{{ row.plannedMinutes }} 分</td><td>{{ duration(row.actualSeconds) }}</td><td>{{ row.partialCount }} 部分 / {{ row.skippedCount }} 跳过 / {{ row.deferredCount }} 延期</td></tr></tbody></table></div>
        <footer class="pagination-bar"><span>第 {{ (ledger?.page ?? 0) + 1 }} / {{ Math.max(ledger?.totalPages ?? 0, 1) }} 页</span><div class="button-row"><button class="btn" :disabled="!ledger || ledger.page === 0" @click="setPage((ledger?.page ?? 0) - 1)">上一页</button><button class="btn" :disabled="!ledger || ledger.page >= ledger.totalPages - 1" @click="setPage((ledger?.page ?? 0) + 1)">下一页</button></div></footer>
      </section>
    </template>
  </div>
</template>
