<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import PwaControl from '@/components/PwaControl.vue';
import ConnectionBar from '@/components/ConnectionBar.vue';
const auth = useAuthStore(); const router = useRouter();
const queueNotice = ref('');
const reconnecting = ref(false); const pageRevision = ref(0);
async function reconnect() { reconnecting.value = true; try { await auth.bootstrap(); pageRevision.value++; } finally { reconnecting.value = false; } }
let noticeTimer: number | undefined;
async function logout() {
  try { await auth.logout(); }
  catch { window.alert('服务端退出或本地凭据清理未完全成功，请检查网络后重试；当前页面已退出登录。'); }
  finally { await router.replace('/login'); }
}
watch(() => auth.user, (user, previous) => { if (previous && !user && !router.currentRoute.value.meta.public) void router.replace('/login'); });
function showQueueNotice(): void {
  queueNotice.value = '操作已安全保存到当前设备。联网后可在同步中心继续。';
  window.clearTimeout(noticeTimer);
  noticeTimer = window.setTimeout(() => { queueNotice.value = ''; }, 5000);
}
function invalidated() { auth.user = null; void router.replace('/login'); }
onMounted(() => { window.addEventListener('plan-platform:offline-queued', showQueueNotice); window.addEventListener('plan-platform:session-invalidated', invalidated); });
onUnmounted(() => { window.removeEventListener('plan-platform:offline-queued', showQueueNotice); window.removeEventListener('plan-platform:session-invalidated', invalidated); window.clearTimeout(noticeTimer); });
</script>
<template>
  <div class="app-shell">
    <header v-if="auth.user" class="site-header">
      <router-link class="brand" to="/"><strong>PLAN / PLATFORM</strong><span>计划与时间管理</span></router-link>
      <nav aria-label="主导航"><router-link to="/">今日</router-link><router-link to="/schedule">日程</router-link><router-link to="/plans">计划</router-link><router-link to="/mainlines">主线</router-link><router-link to="/stats">统计</router-link><router-link to="/reviews">复盘</router-link><router-link to="/records">记录</router-link><router-link to="/exports">导出</router-link><router-link to="/sync">同步</router-link><router-link v-if="auth.user.role==='ADMIN'" to="/members">成员</router-link><router-link to="/settings">设置</router-link></nav>
      <div class="account-tools"><PwaControl/><button class="account-button" type="button" @click="logout">{{ auth.user.displayName }} · 退出</button></div>
    </header>
    <div v-if="queueNotice" class="queue-toast" role="status"><strong>已加入离线队列</strong><span>{{ queueNotice }}</span><router-link to="/sync">查看队列</router-link></div>
    <ConnectionBar v-if="auth.user" :key="auth.user.id" :busy="reconnecting" @reconnect="reconnect" />
    <main><router-view :key="`${router.currentRoute.value.fullPath}:${pageRevision}`" /></main>
  </div>
</template>
