<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import PwaControl from '@/components/PwaControl.vue';
import ConnectionBar from '@/components/ConnectionBar.vue';
import ClientUpdateNotice from '@/components/ClientUpdateNotice.vue';
import MobileNavigation from '@/components/MobileNavigation.vue';
import WorkspaceSwitcher from '@/components/WorkspaceSwitcher.vue';
import LearningPrompt from '@/components/LearningPrompt.vue';
import { resetLearning } from '@/learning';
import { workspace, bindWorkspaceContext, resetWorkspaceContext } from '@/workspace';
import { planNavigation, routeNavigation, todoNavigation, workspaceHome } from '@/workspaceModel';
import { checkClientUpdate, requiredClientUpdate, updateRouteAllowed } from '@/clientUpdates';
import { isAndroid } from '@/desktop';
const auth = useAuthStore(); const router = useRouter();
const productPage = computed(() => router.currentRoute.value.meta.landing || (router.currentRoute.value.path === '/' && !auth.user));
const navigation = computed(() => [
  ...(workspace.mode === 'plan' ? planNavigation : workspace.mode === 'route' ? routeNavigation : todoNavigation),
  ...(workspace.mode === 'plan' ? [{path:'/stats',label:'统计'},{path:'/reviews',label:'复盘'},{path:'/records',label:'记录'},{path:'/exports',label:'导出'}] : workspace.mode === 'route' ? [{path:'/route-records',label:'记录'}] : []),
  {path:'/sync',label:'同步'}, ...(auth.user?.role === 'ADMIN' ? [{path:'/members',label:'成员'}] : []), {path:'/learn',label:'使用教学'}, {path:'/settings',label:'设置'},
]);
watch(() => auth.user?.id, id => { if (id) bindWorkspaceContext(); else { resetWorkspaceContext(); resetLearning(); } }, { immediate: true });
const queueNotice = ref('');
const reconnecting = ref(false); const pageRevision = ref(0);
async function reconnect() { reconnecting.value = true; try { await auth.bootstrap(); pageRevision.value++; } finally { reconnecting.value = false; } }
let noticeTimer: number | undefined;
let updateTimer: number | undefined;
function checkUpdates() { void checkClientUpdate(); }
async function logout() {
  try { await auth.logout(); }
  catch { window.alert('服务端退出或本地凭据清理未完全成功，请检查网络后重试；当前页面已退出登录。'); }
  finally { await router.replace('/login'); }
}
watch(() => auth.user, (user, previous) => { if (previous && !user && !router.currentRoute.value.meta.public) void router.replace('/login'); });
watch(() => auth.user?.id, id => { if (id && isAndroid()) void checkClientUpdate(); });
function showQueueNotice(): void {
  queueNotice.value = '操作已安全保存到当前设备。联网后可在同步中心继续。';
  window.clearTimeout(noticeTimer);
  noticeTimer = window.setTimeout(() => { queueNotice.value = ''; }, 5000);
}
function invalidated() { auth.user = null; if (!router.currentRoute.value.meta.public) void router.replace('/login'); }
onMounted(() => { window.addEventListener('plan-platform:offline-queued', showQueueNotice); window.addEventListener('plan-platform:session-invalidated', invalidated); window.addEventListener('online', checkUpdates); updateTimer = window.setInterval(checkUpdates, 15 * 60 * 1000); });
onUnmounted(() => { window.removeEventListener('plan-platform:offline-queued', showQueueNotice); window.removeEventListener('plan-platform:session-invalidated', invalidated); window.removeEventListener('online', checkUpdates); window.clearTimeout(noticeTimer); window.clearInterval(updateTimer); });
</script>
<template>
  <div class="app-shell">
    <header v-if="auth.user && !productPage" class="site-header">
      <router-link class="brand" :to="workspaceHome(workspace.mode)"><strong>计划舱</strong><span>{{ workspace.mode === 'plan' ? '按时间安排任务' : workspace.mode === 'route' ? '按路径推进目标' : '随手记，逐项完成' }}</span></router-link>
      <WorkspaceSwitcher/>
      <nav aria-label="主导航"><router-link v-for="item in navigation" :key="item.path" :to="item.path">{{ item.label }}</router-link></nav>
      <div class="account-tools"><PwaControl/><button class="account-button" type="button" @click="logout">{{ auth.user.displayName }} · 退出</button></div>
    </header>
    <div v-if="queueNotice" class="queue-toast" role="status"><strong>已加入离线队列</strong><span>{{ queueNotice }}</span><router-link to="/sync">查看队列</router-link></div>
    <ConnectionBar v-if="auth.user && !productPage" :key="auth.user.id" :busy="reconnecting" @reconnect="reconnect" />
    <ClientUpdateNotice v-if="!productPage" />
    <LearningPrompt v-if="auth.user && !productPage"/>
    <main :inert="requiredClientUpdate() && !updateRouteAllowed(router.currentRoute.value.path)"><router-view :key="`${router.currentRoute.value.fullPath}:${pageRevision}`" /></main>
    <MobileNavigation v-if="auth.user && !productPage" @logout="logout"/>
  </div>
</template>
