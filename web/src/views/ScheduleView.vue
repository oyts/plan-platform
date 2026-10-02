<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import LocalBadge from '@/components/LocalBadge.vue';
import { requiresConnection } from '@/connectionState';
import { isPlanRestDay, restDaySummary } from '@/restDays';
import type { Milestone, Plan, Progress, Task, PageResponse } from '@/types';

const route = useRoute();
const router = useRouter();
const plans = ref<Plan[]>([]);
const planId = ref(typeof route.query.plan === 'string' ? route.query.plan : '');
const anchor = ref(new Date().toLocaleDateString('sv-SE'));
const tasks = ref<Task[]>([]);
const milestones = ref<Milestone[]>([]);
const error = ref('');
const loading = ref(false);
const busy = ref(false);
const selected = ref<Task | null>(null);
const draft = reactive({ status: 'PENDING', note: '', deferTo: '', manualMinutes: 0, acceptanceChecks: [] as boolean[] });

function dateFrom(value: string) { return new Date(`${value}T00:00:00`); }
function iso(date: Date) { return date.toLocaleDateString('sv-SE'); }
function shiftDate(value: string, days: number) { const date = dateFrom(value); date.setDate(date.getDate() + days); return iso(date); }
function duration(seconds: number) { const h = Math.floor(seconds / 3600); const m = Math.floor(seconds % 3600 / 60); return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`; }
function dayLabel(value: string) { return dateFrom(value).toLocaleDateString('zh-CN', { weekday: 'short' }); }
function isRestDay(value: string) { return isPlanRestDay(value, activePlan.value?.restDays); }
const dates = computed(() => Array.from({ length: 7 }, (_, index) => shiftDate(anchor.value, index)));
const activePlan = computed(() => plans.value.find(plan => plan.id === planId.value) ?? null);
const milestonesByDate = computed(() => {
  const groups = new Map<string, Milestone[]>();
  for (const milestone of milestones.value) {
    const rows = groups.get(milestone.date) ?? [];
    rows.push(milestone); groups.set(milestone.date, rows);
  }
  return groups;
});
const tasksByDate = computed(() => {
  const groups = new Map(dates.value.map(date => [date, [] as Task[]]));
  for (const task of tasks.value) {
    const scheduled = task.progress.deferTo || task.date;
    groups.get(scheduled)?.push(task);
  }
  for (const rows of groups.values()) rows.sort((a, b) => (a.startTime ?? '99:99').localeCompare(b.startTime ?? '99:99') || a.sortOrder - b.sortOrder);
  return groups;
});
const selectedDay = computed(() => selected.value ? (selected.value.progress.deferTo || selected.value.date) : '');
const canComplete = computed(() => draft.acceptanceChecks.every(Boolean));

async function loadPlans() {
  plans.value = (await api.get<Plan[]>('/plans')).data;
  if (!plans.value.some(plan => plan.id === planId.value)) planId.value = plans.value[0]?.id ?? '';
}
async function loadSchedule() {
  if (!planId.value) { tasks.value = []; return; }
  loading.value = true; error.value = '';
  try {
    const [planned, carryover, milestoneRows] = await Promise.all([
      api.get<PageResponse<Task>>(`/plans/${planId.value}/tasks/page`, { params: { from: dates.value[0], to: dates.value[dates.value.length - 1], page: 0, size: 100 } }),
      api.get<Task[]>('/tasks/carryover', { params: { from: dates.value[0], to: dates.value[dates.value.length - 1] } }),
      api.get<Milestone[]>(`/plans/${planId.value}/milestones`),
    ]);
    tasks.value = Array.from(new Map([...planned.data.content, ...carryover.data].map(task => [task.id, task])).values());
    milestones.value = milestoneRows.data;
    if (selected.value) selected.value = tasks.value.find(task => task.id === selected.value?.id) ?? null;
  } catch (exception) { error.value = apiMessage(exception); }
  finally { loading.value = false; }
}
function select(task: Task) {
  selected.value = task;
  draft.status = task.progress.status;
  draft.note = task.progress.note ?? '';
  draft.deferTo = task.progress.deferTo ?? '';
  draft.manualMinutes = 0;
  draft.acceptanceChecks = task.acceptance.map((_, index) => task.progress.acceptanceChecks[index] ?? false);
}
async function saveState() {
  if (!selected.value) return;
  busy.value = true; error.value = '';
  try {
    if (draft.status === 'COMPLETED') {
      if (!canComplete.value) throw new Error('请先确认全部验收标准，再完成任务。');
      await api.post(`/tasks/${selected.value.id}/complete`, { note: draft.note, acceptanceChecks: draft.acceptanceChecks, version: selected.value.progress.version });
    } else {
      await api.put(`/tasks/${selected.value.id}/progress`, { status: draft.status, note: draft.note, acceptanceChecks: draft.acceptanceChecks, deferTo: null, skipReason: '', version: selected.value.progress.version });
    }
    await loadSchedule();
  } catch (exception) { error.value = apiMessage(exception); }
  finally { busy.value = false; }
}
async function deferTask() {
  if (!selected.value || !draft.deferTo) return;
  busy.value = true; error.value = '';
  try {
    await api.post(`/tasks/${selected.value.id}/defer`, { deferTo: draft.deferTo, note: draft.note, version: selected.value.progress.version });
    await loadSchedule();
  } catch (exception) { error.value = apiMessage(exception); }
  finally { busy.value = false; }
}
async function addManualTime() {
  if (!selected.value || !draft.manualMinutes) return;
  busy.value = true; error.value = '';
  try {
    await api.post(`/tasks/${selected.value.id}/manual-time`, { seconds: draft.manualMinutes * 60, version: selected.value.progress.version });
    draft.manualMinutes = 0;
    await loadSchedule();
  } catch (exception) { error.value = apiMessage(exception); }
  finally { busy.value = false; }
}
async function timer(type: 'start' | 'pause', progress: Progress) {
  if (!selected.value) return;
  busy.value = true; error.value = '';
  try { await api.post(`/tasks/${selected.value.id}/timer/${type}`, null, { params: { version: progress.version } }); await loadSchedule(); }
  catch (exception) { error.value = apiMessage(exception); }
  finally { busy.value = false; }
}

watch(planId, async value => { if (value) await router.replace({ query: { plan: value } }); await loadSchedule(); });
watch(anchor, loadSchedule);
onMounted(async () => { try { await loadPlans(); await loadSchedule(); } catch (exception) { error.value = apiMessage(exception); } });
</script>

<template>
  <div class="page schedule-page">
    <header class="page-head schedule-head">
      <div><p class="eyebrow">SCHEDULE / DATE RAIL</p><h1>执行日程</h1><p>原计划、补做任务和实际用时在同一条日期轨道上查看。</p><p v-if="activePlan" class="helper-text">{{ restDaySummary(activePlan.restDays) }} · <router-link :to="`/plans/${planId}`">编辑计划设置</router-link></p></div>
      <label class="plan-picker">当前计划<select v-model="planId"><option v-for="plan in plans" :key="plan.id" :value="plan.id">{{ plan.name }}</option></select></label>
    </header>
    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-if="!plans.length && !loading && !error" class="empty">先创建一个计划，才能开始安排日程。</p>
    <template v-else>
      <section class="schedule-controls" aria-label="日期范围控制">
        <button class="btn" type="button" @click="anchor = shiftDate(anchor, -7)">上一周</button>
        <button class="btn primary" type="button" @click="anchor = new Date().toLocaleDateString('sv-SE')">回到今天</button>
        <button class="btn" type="button" @click="anchor = shiftDate(anchor, 7)">下一周</button>
        <strong>{{ dates[0] }} — {{ dates[6] }}</strong>
        <span>{{ activePlan?.name }}</span>
      </section>
      <div class="schedule-layout">
        <section class="date-rail" aria-label="七日日程">
          <article v-for="date in dates" :key="date" class="rail-day" :class="{ 'rail-day--today': date === new Date().toLocaleDateString('sv-SE'), 'rail-day--rest': isRestDay(date) }">
            <header><span>{{ dayLabel(date) }}</span><strong>{{ date.slice(5).replace('-', '.') }}</strong><small>{{ isRestDay(date) ? '休息日 / 可补做' : '计划日' }}</small><em v-for="milestone in milestonesByDate.get(date)" :key="milestone.id" class="rail-milestone">{{ milestone.name }}</em></header>
            <div v-if="tasksByDate.get(date)?.length" class="rail-tasks">
              <button v-for="task in tasksByDate.get(date)" :key="task.id" class="rail-task" :class="{ active: selected?.id === task.id, deferred: Boolean(task.progress.deferTo) }" type="button" @click="select(task)">
                <span>{{ task.startTime?.slice(0, 5) ?? '弹性' }}</span><b :style="{ background: task.tagColor ?? '#002FA7' }" /><strong>{{ task.title }}</strong><small>{{ task.progress.status }} · {{ duration(task.progress.accumulatedSeconds) }}</small>
                <em v-if="task.localState">{{ task.localState === 'PENDING' ? '本地修改 · 待同步' : '本地操作 · 待处理' }}</em><em v-if="task.progress.deferTo">补做自 {{ task.date.slice(5) }}</em>
              </button>
            </div>
            <p v-else class="rail-empty">{{ isRestDay(date) ? '休息日可在右侧安排补做' : '没有安排任务' }}</p>
          </article>
        </section>
        <aside class="schedule-inspector" aria-live="polite">
          <template v-if="selected">
            <p class="eyebrow">TASK / INSPECTOR</p><h2>{{ selected.title }}</h2><LocalBadge :state="selected.localState"/>
            <p class="inspector-meta">{{ selectedDay }} · {{ selected.tagName ?? selected.category }} · 计划 {{ selected.plannedMinutes }} 分钟</p>
            <a v-if="selected.resourceUrl" :href="selected.resourceUrl" target="_blank" rel="noreferrer">打开任务资料</a>
            <label v-if="selected.acceptance.length" class="inspector-label">验收标准
              <span v-for="(item, index) in selected.acceptance" :key="item" class="check acceptance-check"><input v-model="draft.acceptanceChecks[index]" type="checkbox" />{{ item }}</span>
            </label>
            <label>执行状态<select v-model="draft.status"><option value="PENDING">待开始</option><option value="RUNNING">进行中</option><option value="PAUSED">已暂停</option><option value="PARTIAL">部分完成</option><option value="SKIPPED">已跳过</option><option value="COMPLETED">已完成</option></select></label>
            <label>执行备注<textarea v-model="draft.note" rows="3" placeholder="记录结果、阻塞或下次调整。" /></label>
            <div class="button-row"><button v-if="selected.progress.status !== 'RUNNING'" class="btn" :disabled="busy || requiresConnection()" @click="timer('start', selected.progress)">开始计时</button><button v-else class="btn" :disabled="busy || requiresConnection()" @click="timer('pause', selected.progress)">暂停计时</button><button class="btn primary" :disabled="busy || requiresConnection()" @click="saveState">保存状态</button></div>
            <div class="inspector-rule" />
            <label>补做日期<input v-model="draft.deferTo" type="date" :min="selected.date" /></label>
            <button class="btn" :disabled="busy || !draft.deferTo || requiresConnection()" @click="deferTask">安排补做</button>
            <p class="helper-text">补做可以安排在休息日；原计划会标记为已延期，不会重复计入当天任务。</p>
            <label>补录实际分钟<input v-model.number="draft.manualMinutes" type="number" min="1" max="1440" /></label>
            <button class="btn" :disabled="busy || !draft.manualMinutes || requiresConnection()" @click="addManualTime">记入实际用时</button>
          </template>
          <div v-else class="inspector-empty"><p class="eyebrow">TASK / INSPECTOR</p><h2>选择一个任务</h2><p>在左侧日程中点选任务，右侧会固定显示其状态、补做和计时操作。</p></div>
        </aside>
      </div>
    </template>
  </div>
</template>
