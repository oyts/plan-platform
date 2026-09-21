<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import axios from 'axios';
import { apiMessage, currentQueueOwner, replayOfflineOperation } from '@/api/client';
import { deleteOperation, listOperations, onQueueChange, updateOperation, type OfflineOperation } from '@/offlineQueue';

const rows = ref<OfflineOperation[]>([]);
const busy = ref(new Set<string>());
const online = ref(navigator.onLine);
const notice = ref('');
const error = ref('');
const pendingCount = computed(() => rows.value.filter((row) => row.status === 'PENDING').length);
const conflictCount = computed(() => rows.value.filter((row) => row.status === 'CONFLICT').length);
let stopQueueListener: (() => void) | undefined;

async function load(): Promise<void> {
  const ownerId = currentQueueOwner();
  rows.value = ownerId ? await listOperations(ownerId) : [];
}

async function retry(row: OfflineOperation): Promise<boolean> {
  if (!online.value || busy.value.has(row.id)) return false;
  busy.value = new Set(busy.value).add(row.id);
  error.value = ''; notice.value = '';
  try {
    await updateOperation(row.id, { attempts: row.attempts + 1, status: 'PENDING', error: undefined });
    await replayOfflineOperation(row);
    await deleteOperation(row.id);
    notice.value = `“${operationName(row)}”已同步。`;
    return true;
  } catch (failure) {
    const conflict = axios.isAxiosError(failure) && failure.response?.status === 409;
    await updateOperation(row.id, { status: conflict ? 'CONFLICT' : 'FAILED', error: apiMessage(failure) });
    if (!conflict) error.value = apiMessage(failure);
    return false;
  } finally {
    const next = new Set(busy.value); next.delete(row.id); busy.value = next;
    await load();
  }
}

async function retryAll(): Promise<void> {
  for (const row of [...rows.value]) {
    if (row.status === 'CONFLICT') continue;
    if (!await retry(row) && !online.value) break;
  }
}

async function discard(row: OfflineOperation): Promise<void> {
  if (!window.confirm(`放弃本地操作“${operationName(row)}”？此操作无法恢复。`)) return;
  await deleteOperation(row.id);
  notice.value = '本地操作已放弃，服务器数据未被改动。';
  await load();
}

function operationName(row: OfflineOperation): string {
  const names: Record<string, string> = { POST: '新增', PUT: '更新', PATCH: '调整', DELETE: '删除' };
  return `${names[row.method] ?? row.method} ${row.url}`;
}

function statusName(status: OfflineOperation['status']): string {
  return { PENDING: '等待同步', CONFLICT: '需要处理', FAILED: '同步失败' }[status];
}

function destination(row: OfflineOperation): string {
  if (row.url.startsWith('/mainlines')) return '/mainlines';
  const match = row.url.match(/^\/plans\/([^/]+)/);
  if (match) return `/plans/${match[1]}/studio`;
  if (row.url.startsWith('/tasks')) return '/schedule';
  return '/plans';
}

function payload(row: OfflineOperation): string {
  return JSON.stringify({ params: row.params, data: row.data }, null, 2);
}

function updateOnline(): void { online.value = navigator.onLine; }

onMounted(async () => {
  stopQueueListener = onQueueChange(() => { void load(); });
  window.addEventListener('online', updateOnline); window.addEventListener('offline', updateOnline);
  await load();
});
onUnmounted(() => {
  stopQueueListener?.(); window.removeEventListener('online', updateOnline); window.removeEventListener('offline', updateOnline);
});
</script>

<template>
  <div class="page sync-page">
    <header class="page-head sync-head">
      <div><p class="eyebrow">OFFLINE QUEUE / CONFLICT DESK</p><h1>同步中心</h1></div>
      <div class="sync-summary"><strong>{{ rows.length }}</strong><span>本地操作</span><b>{{ conflictCount }}</b><span>待处理冲突</span></div>
    </header>

    <section class="sync-explainer">
      <div><span>01</span><strong>离线暂存</strong><p>计划、任务、标签与主线的普通新增和编辑会保存在当前设备，不保存登录令牌。</p></div>
      <div><span>02</span><strong>幂等重放</strong><p>每项操作都有唯一键；网络中断后的重复提交不会生成两份数据。</p></div>
      <div><span>03</span><strong>冲突停靠</strong><p>服务器已被其他设备修改时停止覆盖，由你查看最新数据并决定是否重新编辑。</p></div>
    </section>

    <p v-if="!online" class="sync-offline">当前离线。你仍可继续支持离线的编辑，联网后回到这里同步。</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-if="notice" class="success-text">{{ notice }}</p>

    <section class="sync-ledger">
      <header class="section-title">
        <div><p class="eyebrow">LOCAL OPERATION LEDGER</p><h2>待同步操作</h2></div>
        <div class="button-row"><span>{{ pendingCount }} 项等待</span><button class="btn primary" :disabled="!online || !rows.some((row) => row.status !== 'CONFLICT')" @click="retryAll">同步等待项</button></div>
      </header>

      <article v-for="row in rows" :key="row.id" class="sync-row" :class="`sync-row--${row.status.toLowerCase()}`">
        <div class="sync-method"><strong>{{ row.method }}</strong><span>{{ row.attempts }} 次尝试</span></div>
        <div class="sync-copy">
          <div><span class="sync-status">{{ statusName(row.status) }}</span><time>{{ new Date(row.createdAt).toLocaleString('zh-CN') }}</time></div>
          <h3>{{ operationName(row) }}</h3>
          <p v-if="row.error">{{ row.error }}</p>
          <p v-else>操作键 {{ row.id }}</p>
          <details><summary>查看本地请求内容</summary><pre>{{ payload(row) }}</pre></details>
        </div>
        <div class="sync-actions">
          <router-link v-if="row.status === 'CONFLICT'" class="btn" :to="destination(row)">查看最新数据</router-link>
          <button class="btn primary" :disabled="!online || busy.has(row.id)" @click="retry(row)">{{ busy.has(row.id) ? '同步中…' : '重试' }}</button>
          <button class="btn danger" :disabled="busy.has(row.id)" @click="discard(row)">放弃本地操作</button>
        </div>
      </article>
      <div v-if="!rows.length" class="sync-empty"><strong>队列为空</strong><p>当前设备的操作已经全部送达服务器。</p></div>
    </section>
  </div>
</template>
