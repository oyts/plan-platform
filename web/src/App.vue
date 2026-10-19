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
import InAppReminder from '@/components/InAppReminder.vue';
import OperationsNotice from '@/components/OperationsNotice.vue';
import DesktopNavigation from '@/components/DesktopNavigation.vue';
import { serverScope } from '@/api/client';
import { resetLearning } from '@/learning';
import { workspace, bindWorkspaceContext, resetWorkspaceContext } from '@/workspace';
import { workspaceHome } from '@/workspaceModel';
import { checkClientUpdate, requiredClientUpdate, updateRouteAllowed } from '@/clientUpdates';
import { isAndroid } from '@/desktop';
const auth = useAuthStore(); const router = useRouter();
const productPage = computed(() => router.currentRoute.value.meta.landing || (router.currentRoute.value.path === '/' && !auth.user));
watch(() => auth.user?.id, id => { if (id) bindWorkspaceContext(); else { resetWorkspaceContext(); resetLearning(); } }, { immediate: true });
const queueNotice = ref('');
const reconnecting = ref(false); const pageRevision = ref(0);
async function reconnect() { reconnecting.value = true; try { await auth.bootstrap(); pageRevision.value++; } finally { reconnecting.value = false; } }
let noticeTimer: number | undefined;
let updateTimer: number | undefined;
function checkUpdates() { void checkClientUpdate(); }
function focusMain(){document.getElementById('main-content')?.focus();}
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
    <a class="skip-content" href="#main-content" @click.prevent="focusMain">跳到主要内容</a>
    <header v-if="auth.user && !productPage" class="site-header">
      <router-link class="brand" :to="workspaceHome(workspace.mode)"><strong>计划舱</strong><span>{{ workspace.mode === 'plan' ? '按时间安排任务' : workspace.mode === 'route' ? '按路径推进目标' : '随手记，逐项完成' }}</span></router-link>
      <WorkspaceSwitcher/>
      <DesktopNavigation/>
      <div class="account-tools"><PwaControl/><button class="account-button" :title="`${auth.user.displayName} · 退出`" type="button" @click="logout">{{ auth.user.displayName }} · 退出</button></div>
    </header>
    <div v-if="queueNotice" class="queue-toast" role="status"><strong>已加入离线队列</strong><span>{{ queueNotice }}</span><router-link to="/sync">查看队列</router-link></div>
    <ConnectionBar v-if="auth.user && !productPage" :key="auth.user.id" :busy="reconnecting" @reconnect="reconnect" />
    <ClientUpdateNotice v-if="!productPage" />
    <InAppReminder v-if="auth.user && !productPage" :key="`${auth.user.id}:${serverScope()}`"/>
    <OperationsNotice v-if="auth.user?.role==='ADMIN'&&auth.operationsAvailable&&!productPage" :key="`${auth.user.id}:${serverScope()}`"/>
    <LearningPrompt v-if="auth.user && !productPage"/>
    <main id="main-content" tabindex="-1" :inert="requiredClientUpdate() && !updateRouteAllowed(router.currentRoute.value.path)"><router-view :key="`${['/ai','/verify-email'].includes(router.currentRoute.value.path) ? router.currentRoute.value.path : router.currentRoute.value.fullPath}:${pageRevision}`" /></main>
    <MobileNavigation v-if="auth.user && !productPage" @logout="logout"/>
  </div>
</template>
<style>
.skip-content{position:fixed;z-index:100;left:12px;top:8px;transform:translateY(-150%);padding:12px 18px;background:var(--paper);border:2px solid var(--blue);font-weight:700}.skip-content:focus{transform:none}.account-button{max-width:180px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;padding:10px 18px}.site-header{grid-template-columns:148px 136px minmax(0,1fr) auto}@media(min-width:701px) and (max-width:1000px){.site-header{grid-template-columns:110px 130px minmax(0,1fr) auto}.site-header .brand{padding:0 14px}.site-header .brand span{display:none}.account-button{max-width:126px;padding:8px}.site-header .pwa-control{display:none}}
</style>
