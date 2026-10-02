import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router';
import { hasToken } from '@/api/client';
import { isNativeClient as isDesktop } from '@/desktop';
import DashboardView from '@/views/DashboardView.vue';
const router = createRouter({ history: isDesktop() ? createWebHashHistory() : createWebHistory(), routes: [
  { path: '/desktop', component: () => import('@/views/DesktopView.vue'), meta: { public: true, allowAuthenticated: true } },
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  { path: '/reset-password', component: () => import('@/views/ResetPasswordView.vue'), meta: { public: true } },
  { path: '/', component: DashboardView },
  { path: '/schedule', component: () => import('@/views/ScheduleView.vue') },
  { path: '/plans', component: () => import('@/views/PlansView.vue') },
  { path: '/plans/:id', component: () => import('@/views/PlanDetailView.vue') },
  { path: '/plans/:id/studio', component: () => import('@/views/PlanStudioView.vue') },
  { path: '/stats', component: () => import('@/views/StatsView.vue') },
  { path: '/reviews', component: () => import('@/views/ReviewView.vue') },
  { path: '/records', component: () => import('@/views/RecordsView.vue') },
  { path: '/exports', component: () => import('@/views/ExportsView.vue') },
  { path: '/members', component: () => import('@/views/MembersView.vue') },
  { path: '/verify-email', component: () => import('@/views/VerifyEmailView.vue'), meta: { public: true } },
  { path: '/mainlines', component: () => import('@/views/MainlinesView.vue') },
  { path: '/sync', component: () => import('@/views/SyncView.vue') },
  { path: '/settings', component: () => import('@/views/SettingsView.vue') },
] });
router.beforeEach((to) => { if (!to.meta.public && !hasToken()) return '/login'; if (to.meta.public && hasToken() && !to.meta.allowAuthenticated) return '/'; });
export default router;
