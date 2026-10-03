<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api, apiMessage } from '@/api/client';
import { nextRouteItem, routeDuration, routeStatus } from '@/routeProgress';
import type { Mainline } from '@/types';
const lines = ref<Mainline[]>([]); const error = ref(''); const loading = ref(true);
onMounted(async () => { try { lines.value = (await api.get<Mainline[]>('/mainlines')).data; } catch (e) { error.value = apiMessage(e); } finally { loading.value = false; } });
</script>
<template><div class="page route-home-page">
  <header class="page-head"><div><h1>继续路线</h1><p>有空就推进一步，进度会保留。没有每日配额，也不按日期计算欠账。</p></div><router-link class="btn primary" to="/mainlines">管理目标路线</router-link></header>
  <p v-if="error" class="error-text" role="alert">{{ error }}</p><p v-if="loading" class="loading">正在读取路线…</p>
  <section v-else class="section"><header class="section-title"><h2>我的目标路线</h2><span>{{ lines.length }}</span></header>
    <article v-for="line in lines" :key="line.id" class="route-card">
      <div><span class="tag" :style="{ color: line.color, borderColor: line.color }">{{ line.completed }} / {{ line.total }} 步骤</span><h2>{{ line.name }}</h2><p v-if="line.description">{{ line.description }}</p>
        <template v-if="nextRouteItem(line)"><p class="route-next-label">{{ nextRouteItem(line)!.status === 'RUNNING' ? '正在进行' : '下一步' }} · {{ routeStatus(nextRouteItem(line)!.status) }}</p><h3>{{ nextRouteItem(line)!.title }}</h3><p>已记录 {{ routeDuration(nextRouteItem(line)!.accumulatedSeconds) }}</p></template>
        <p v-else>{{ line.total ? '所有步骤已完成，可以继续添加新的步骤。' : '还没有步骤，先把目标拆成可以执行的行动。' }}</p>
      </div><router-link class="btn primary" :to="{ path: '/mainlines', query: { line: line.id } }">{{ nextRouteItem(line) ? '继续这条路线' : '查看路线' }}</router-link>
    </article><p v-if="!lines.length && !error" class="empty">还没有目标路线。可以先创建一条，再逐步添加学习或项目步骤。</p>
  </section>
</div></template>
