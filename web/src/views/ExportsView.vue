<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api, apiMessage } from '@/api/client';
import type { Plan } from '@/types';
import { downloadFile, downloadErrorMessage } from '@/download';
import PersonalDataPanel from '@/components/PersonalDataPanel.vue';

function iso(days = 0) { const value = new Date(); value.setDate(value.getDate() + days); return value.toLocaleDateString('sv-SE'); }
const plans = ref<Plan[]>([]); const planId = ref(''); const from = ref(iso(-29)); const to = ref(iso()); const error = ref(''); const busy = ref('');
async function load() { plans.value = (await api.get<Plan[]>('/plans')).data; planId.value ||= plans.value[0]?.id ?? ''; }
async function download(kind: 'tasks' | 'stats' | 'reflectionsCsv' | 'reflectionsMd') {
  if (!planId.value) return; busy.value = kind; error.value = '';
  try {
    const endpoint = kind === 'tasks' ? `/plans/${planId.value}/tasks/export` : kind === 'stats' ? `/plans/${planId.value}/stats/export` : `/plans/${planId.value}/reflections/export`;
    const params = kind.startsWith('reflections') ? { from: from.value, to: to.value, format: kind === 'reflectionsMd' ? 'MARKDOWN' : 'CSV' } : { from: from.value, to: to.value };
    const response = await api.get(endpoint, { params, responseType: 'blob' });
    await downloadFile(response.data as Blob, kind === 'reflectionsMd' ? `plan-review-${from.value}-${to.value}.md` : `plan-${kind}-${from.value}-${to.value}.csv`);
  } catch (exception) { error.value = await downloadErrorMessage(exception) ?? apiMessage(exception); } finally { busy.value = ''; }
}
onMounted(() => load().catch(exception => error.value = apiMessage(exception)));
</script>

<template>
  <div class="page">
    <header class="page-head"><div><h1>个人数据中心</h1><p>把三种模式的数据带走，或从私有备份恢复为新副本。当前账号的原有内容不会被覆盖。</p></div></header>
    <PersonalDataPanel @restored="load().catch(exception=>error=apiMessage(exception))"/>
    <p v-if="error" class="error-text">{{ error }}</p>
    <section class="studio-section export-controls"><header class="section-title"><div><p class="eyebrow">SCOPE</p><h2>导出范围</h2></div></header><div class="form-grid"><label>计划<select v-model="planId"><option value="">请选择计划</option><option v-for="plan in plans" :key="plan.id" :value="plan.id">{{ plan.name }}</option></select></label><label>开始日期<input v-model="from" type="date" :max="to" /></label><label>结束日期<input v-model="to" type="date" :min="from" /></label></div></section>
    <section class="studio-section"><header class="section-title"><div><p class="eyebrow">AVAILABLE EXPORTS</p><h2>可用文件</h2></div></header><div class="export-grid"><article><p class="eyebrow">TASK LEDGER</p><h3>计划任务 CSV</h3><p>日期、时段、标签、状态、计划与实际用时，以及重复批次编号。</p><button class="btn primary" :disabled="!planId || !!busy" @click="download('tasks')">{{ busy === 'tasks' ? '正在生成…' : '下载 CSV' }}</button></article><article><p class="eyebrow">STATISTICS</p><h3>统计汇总 CSV</h3><p>范围总览及标签维度的任务数、完成数与用时。</p><button class="btn primary" :disabled="!planId || !!busy" @click="download('stats')">{{ busy === 'stats' ? '正在生成…' : '下载 CSV' }}</button></article><article><p class="eyebrow">REVIEW LEDGER</p><h3>复盘 CSV</h3><p>适合二次分析与表格整理的每日复盘记录。</p><button class="btn" :disabled="!planId || !!busy" @click="download('reflectionsCsv')">{{ busy === 'reflectionsCsv' ? '正在生成…' : '下载 CSV' }}</button></article><article><p class="eyebrow">REVIEW NOTES</p><h3>复盘 Markdown</h3><p>适合保存到仓库、笔记系统或学习档案的可读版本。</p><button class="btn" :disabled="!planId || !!busy" @click="download('reflectionsMd')">{{ busy === 'reflectionsMd' ? '正在生成…' : '下载 Markdown' }}</button></article></div></section>
  </div>
</template>
