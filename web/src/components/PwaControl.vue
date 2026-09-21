<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { usePwaStatus } from '@/pwa';
import { onQueueChange, queueCount } from '@/offlineQueue';
import { currentQueueOwner } from '@/api/client';
const { canInstall, standalone, updateReady, online, install, update } = usePwaStatus();
const queued = ref(0);
let stopQueueListener: (() => void) | undefined;
async function refreshQueue(): Promise<void> { const ownerId = currentQueueOwner(); queued.value = ownerId ? await queueCount(ownerId) : 0; }
onMounted(async () => { stopQueueListener = onQueueChange(() => { void refreshQueue(); }); await refreshQueue(); });
onUnmounted(() => stopQueueListener?.());
</script>
<template><div class="pwa-control"><router-link v-if="queued" class="install-btn queue-link" to="/sync">待同步 {{ queued }}</router-link><button v-if="canInstall" class="install-btn" type="button" @click="install">安装应用</button><button v-if="updateReady" class="install-btn" type="button" @click="update">更新应用</button><span v-if="!online" class="offline-note">离线：支持的编辑将暂存</span><span v-else-if="standalone" class="offline-note">已作为应用运行</span></div></template>
