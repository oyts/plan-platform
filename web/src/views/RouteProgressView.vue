<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api, apiMessage } from '@/api/client';
import { routeDuration, routeSummary } from '@/routeProgress';
import type { Mainline } from '@/types';
const lines = ref<Mainline[]>([]); const error = ref(''); const summary = computed(() => routeSummary(lines.value));
onMounted(async () => { try { lines.value = (await api.get<Mainline[]>('/mainlines')).data; } catch (e) { error.value = apiMessage(e); } });
</script>
<template><div class="page route-progress-page"><header class="page-head"><div><h1>路线进展</h1><p>只统计目标路线，不包含周期计划任务，也不计算每日达标率。</p></div></header>
  <p v-if="error" class="error-text" role="alert">{{ error }}</p>
  <section class="stats-metric-grid"><article class="metric"><span>目标路线</span><strong>{{ summary.routes }}</strong></article><article class="metric"><span>完成步骤</span><strong>{{ summary.completed }} / {{ summary.total }}</strong></article><article class="metric"><span>已记录投入</span><strong>{{ routeDuration(summary.seconds) }}</strong></article></section>
  <section class="section"><header class="section-title"><h2>各路线进度</h2></header><article v-for="line in lines" :key="line.id" class="route-card"><div><h2>{{ line.name }}</h2><p>{{ line.completed }} / {{ line.total }} 步骤 · {{ routeDuration(routeSummary([line]).seconds) }}</p><progress :value="line.completed" :max="Math.max(1, line.total)" :aria-label="`${line.name}完成进度`"/></div><router-link class="btn" :to="{path:'/mainlines',query:{line:line.id}}">查看路线</router-link></article><p v-if="!lines.length && !error" class="empty">创建目标路线并添加步骤后，这里会显示推进情况。</p></section>
  <p class="helper-text">投入时间为服务器已累计记录的时长；正在运行的计时在暂停或完成后计入。不设结束日期，不因间隔较长而扣减进度。</p>
</div></template>
