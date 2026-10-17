<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { requiredClientUpdate, updateRouteAllowed } from '@/clientUpdates';
import { isNativeClient } from '@/desktop';
import { workspace } from '@/workspace';
import { planNavigation, routeNavigation, todoNavigation } from '@/workspaceModel';
import AppIcon from './AppIcon.vue';
import AppSheet from './AppSheet.vue';
import PwaControl from './PwaControl.vue';
const emit = defineEmits<{ logout: [] }>();
const auth = useAuthStore(); const route = useRoute(); const open = ref(false);
const primary = computed(() => workspace.mode === 'plan' ? planNavigation : workspace.mode === 'route' ? routeNavigation : todoNavigation);
const secondary = computed(() => [
  ...(workspace.mode === 'plan' ? [
  { path: '/stats', label: '统计', description: '计划与实际用时' },
  { path: '/reviews', label: '复盘', description: '回顾每天的执行' },
  { path: '/records', label: '记录', description: '计时流水与回收站' },
  ] : workspace.mode === 'route' ? [{ path: '/route-records', label: '记录', description: '已删除的路线与步骤' }] : []),
  { path: '/overview', label: '今日与提醒', description: '今日、逾期与即将到期' },
  { path: '/weekly-review', label: '周复盘', description: '本周推进与下周重点' },
  { path: '/exports', label: '数据中心', description: '三模式导出与私有备份恢复' },
  { path: '/sync', label: '同步', description: '待同步操作与冲突' },
  { path: '/ai', label: 'AI 规划', description: '对话、草稿与确认生成' },
  { path: '/feedback', label: '反馈与诊断', description: '本机草稿与处理回复' },
  { path: '/learn', label: '使用教学', description: '分步练习与随时回顾' },
  { path: '/settings', label: '设置', description: '账号与安全' },
  ...(isNativeClient() ? [{ path: '/desktop', label: '客户端', description: '服务器连接与更新' }] : []),
  ...(auth.user?.role === 'ADMIN' ? [{ path: '/members', label: '成员', description: '邀请与账号管理' }] : []),
]);
const moreActive = computed(() => secondary.value.some(item => item.path === route.path));
function allowed(path: string) { return !requiredClientUpdate() || updateRouteAllowed(path); }
watch(() => route.fullPath, () => { open.value = false; });
function logout() { open.value = false; emit('logout'); }
</script>
<template>
  <nav class="mobile-navigation" :class="{ 'mobile-navigation--todo': workspace.mode === 'todo' }" aria-label="手机主导航">
    <template v-for="item in primary" :key="item.path">
      <router-link v-if="allowed(item.path)" :to="item.path" :class="{ active: item.path === '/' ? route.path === '/' : route.path.startsWith(item.path) }"><AppIcon :name="item.icon"/><span>{{ item.label }}</span></router-link>
      <button v-else disabled type="button"><AppIcon :name="item.icon"/><span>{{ item.label }}</span></button>
    </template>
    <button type="button" :class="{ active: moreActive || open }" :aria-expanded="open" aria-haspopup="dialog" @click="open = true"><AppIcon name="more"/><span>更多</span></button>
  </nav>
  <AppSheet :open="open" title="更多功能" title-id="mobile-menu-title" @close="open = false">
    <p class="mobile-account-name">{{ auth.user?.displayName }}</p>
    <nav class="mobile-menu-grid" aria-label="更多功能">
      <template v-for="item in secondary" :key="item.path">
        <router-link v-if="allowed(item.path)" :to="item.path" :aria-label="item.label" @click="open = false"><strong>{{ item.label }}</strong><span>{{ item.description }}</span></router-link>
        <div v-else class="mobile-menu-disabled" aria-disabled="true"><strong>{{ item.label }}</strong><span>更新客户端后可用</span></div>
      </template>
    </nav>
    <PwaControl v-if="!isNativeClient()"/>
    <button class="btn mobile-logout" type="button" @click="logout">{{ auth.user?.displayName }} · 退出</button>
  </AppSheet>
</template>
<style scoped>@media(max-width:900px){.mobile-navigation--todo{grid-template-columns:repeat(2,minmax(0,1fr))}}</style>
