<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api, apiMessage } from '@/api/client';
import type { Progress, Task, Today } from '@/types';

const today = ref<Today | null>(null);
const error = ref('');
const busyId = ref('');
const completing = ref<Task | null>(null);
const completionChecks = ref<boolean[]>([]);
const completionNote = ref('');

function duration(seconds: number) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor(seconds % 3600 / 60);
  const s = Math.floor(seconds % 60);
  return [h, m, s].map(value => String(value).padStart(2, '0')).join(':');
}

async function load() {
  try { today.value = (await api.get<Today>('/dashboard/today')).data; }
  catch (e) { error.value = apiMessage(e); }
}

async function timer(taskId: string, type: 'start' | 'pause', progress: Progress) {
  busyId.value = taskId;
  error.value = '';
  try {
    await api.post(`/tasks/${taskId}/timer/${type}`, null, { params: { version: progress.version } });
    await load();
  } catch (e) { error.value = apiMessage(e); }
  finally { busyId.value = ''; }
}

function openCompletion(task: Task) {
  completing.value = task;
  completionChecks.value = task.acceptance.map((_, index) => task.progress.acceptanceChecks[index] ?? false);
  completionNote.value = task.progress.note ?? '';
}

function closeCompletion() {
  completing.value = null;
  completionChecks.value = [];
  completionNote.value = '';
}

const canComplete = computed(() => completionChecks.value.every(Boolean));

async function completeTask() {
  if (!completing.value) return;
  busyId.value = completing.value.id;
  error.value = '';
  try {
    await api.post(`/tasks/${completing.value.id}/complete`, {
      note: completionNote.value,
      acceptanceChecks: completionChecks.value,
      version: completing.value.progress.version,
    });
    closeCompletion();
    await load();
  } catch (e) { error.value = apiMessage(e); }
  finally { busyId.value = ''; }
}

onMounted(load);
</script>

<template>
  <div v-if="today" class="page">
    <section class="hero">
      <div class="date-block"><strong>{{ today.date.slice(5) }}</strong><span>{{ new Date(today.date + 'T00:00:00').toLocaleDateString('zh-CN', { weekday: 'long' }) }}</span></div>
      <div><p class="eyebrow">TODAY / {{ today.date }}</p><h1>今日控制台</h1><p>计划由服务器持久化，多端使用同一份进度。</p></div>
      <div class="hero-metrics"><div><span>完成</span><strong>{{ today.completed }}/{{ today.total }}</strong></div><div><span>计划</span><strong>{{ duration(today.plannedMinutes * 60) }}</strong></div><div><span>实际</span><strong>{{ duration(today.actualSeconds) }}</strong></div></div>
    </section>
    <p v-if="error" class="error-text">{{ error }}</p>
    <section class="section">
      <header class="section-title"><div><p class="eyebrow">TASKS / TODAY</p><h2>今天的任务</h2></div><span>{{ today.tasks.length }} 项</span></header>
      <p v-if="!today.tasks.length" class="empty">今天还没有任务，可以前往“计划”添加。</p>
      <article v-for="task in today.tasks" :key="task.id" class="task-row">
        <div class="time-cell"><strong>{{ task.startTime?.slice(0, 5) ?? '--:--' }}</strong><span>{{ task.endTime?.slice(0, 5) ?? '' }}</span></div>
        <div class="task-copy"><span class="tag" :style="{ color: task.tagColor ?? '#002FA7', borderColor: task.tagColor ?? '#002FA7' }">{{ task.tagName ?? task.category }}</span><h3>{{ task.title }}</h3><p>{{ task.description }}</p></div>
        <div class="task-state"><strong>{{ task.progress.status }}</strong><span>{{ duration(task.progress.accumulatedSeconds) }}</span></div>
        <div class="button-row">
          <button v-if="task.progress.status !== 'RUNNING' && task.progress.status !== 'COMPLETED'" class="btn" :disabled="busyId === task.id" @click="timer(task.id, 'start', task.progress)">开始</button>
          <button v-if="task.progress.status === 'RUNNING'" class="btn" @click="timer(task.id, 'pause', task.progress)">暂停</button>
          <button v-if="task.progress.status !== 'COMPLETED'" class="btn primary" @click="openCompletion(task)">完成</button>
        </div>
      </article>
    </section>

    <div v-if="completing" class="dialog-backdrop" @click.self="closeCompletion">
      <form class="completion-dialog" role="dialog" aria-modal="true" aria-labelledby="completion-title" @submit.prevent="completeTask">
        <p class="eyebrow">TASK / REVIEW</p>
        <h2 id="completion-title">完成「{{ completing.title }}」</h2>
        <p>逐项确认验收标准，并留下可供以后复盘的结果备注。</p>
        <label v-for="(item, index) in completing.acceptance" :key="index" class="check acceptance-check"><input v-model="completionChecks[index]" type="checkbox" />{{ item }}</label>
        <label>结果备注<textarea v-model="completionNote" rows="4" placeholder="记录完成结果、问题或下一步。" /></label>
        <div class="button-row dialog-actions"><button type="button" class="btn" @click="closeCompletion">取消</button><button class="btn primary" :disabled="busyId === completing.id || !canComplete">确认完成</button></div>
      </form>
    </div>
  </div>
  <div v-else class="loading">正在读取今天的计划…</div>
</template>
