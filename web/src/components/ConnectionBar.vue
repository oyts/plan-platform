<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { connectionState, requiresConnection } from '@/connectionState';
import { currentQueueOwner } from '@/api/client';
import { onQueueChange, queueCount } from '@/offlineQueue';
defineProps<{ busy: boolean }>();
defineEmits<{ reconnect: [] }>();
const queued = ref(0);
const offline = computed(requiresConnection);
let stop: (() => void) | undefined;
async function refresh() { const owner = currentQueueOwner(); queued.value = owner ? await queueCount(owner) : 0; }
onMounted(() => { stop = onQueueChange(() => { void refresh(); }); void refresh(); });
onUnmounted(() => stop?.());
</script>
<template>
  <aside v-if="offline || queued || !connectionState.cacheAvailable" class="connection-bar" aria-label="连接状态">
    <div><strong>{{ offline ? '离线工作区' : '已连接服务器' }}</strong><p v-if="offline">正在使用本机缓存；仅已加载的内容可用。计时等操作需联网，待办勾选可离线保存。</p><p v-else>本地修改仍需在同步中心提交，不会自动覆盖服务器。</p><small v-if="connectionState.cacheReadAt">最近读取的缓存保存于 {{ new Date(connectionState.cacheReadAt).toLocaleString('zh-CN') }} · 统计与进度为历史快照</small><small v-if="!connectionState.cacheAvailable">本地缓存不可用，请检查浏览器存储空间；不要关闭尚未保存的编辑。</small></div>
    <div class="button-row"><router-link v-if="queued" class="btn" to="/sync">待同步 {{ queued }}</router-link><router-link class="btn" to="/feedback">反馈与诊断</router-link><button v-if="offline" class="btn primary" :disabled="busy" @click="$emit('reconnect')">{{ busy ? '检查连接中…' : '恢复连接' }}</button></div>
  </aside>
</template>
<style scoped>
.connection-bar{display:flex;justify-content:space-between;align-items:center;gap:20px;margin:0 auto;padding:18px 4%;border-bottom:1px solid #002fa7;background:#f7f7f8;color:#002fa7}.connection-bar strong{font-size:18px}.connection-bar p{margin:6px 0;color:#343840}.connection-bar small{display:block;color:#5c6470;line-height:1.6}@media(max-width:700px){.connection-bar{align-items:stretch;flex-direction:column;gap:12px}.button-row{justify-content:flex-start}}
</style>
