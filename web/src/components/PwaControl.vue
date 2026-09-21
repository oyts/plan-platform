<script setup lang="ts">
import { usePwaStatus } from '@/pwa';
const { canInstall, standalone, updateReady, online, install, update } = usePwaStatus();
</script>
<template><div class="pwa-control"><button v-if="canInstall" class="install-btn" type="button" @click="install">安装应用</button><button v-if="updateReady" class="install-btn" type="button" @click="update">更新应用</button><span v-if="!online" class="offline-note">离线：仅可浏览已缓存页面</span><span v-else-if="standalone" class="offline-note">已作为应用运行</span></div></template>
