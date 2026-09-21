import { createRouter, createWebHistory } from 'vue-router';
import { hasToken } from '@/api/client';
import DashboardView from '@/views/DashboardView.vue';
const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  { path: '/', component: DashboardView },
  { path: '/schedule', component: () => import('@/views/ScheduleView.vue') },
  { path: '/plans', component: () => import('@/views/PlansView.vue') },
  { path: '/plans/:id', component: () => import('@/views/PlanDetailView.vue') },
  { path: '/plans/:id/studio', component: () => import('@/views/PlanStudioView.vue') },
  { path: '/stats', component: () => import('@/views/StatsView.vue') },
  { path: '/reviews', component: () => import('@/views/ReviewView.vue') },
  { path: '/records', component: () => import('@/views/RecordsView.vue') },
  { path: '/exports', component: () => import('@/views/ExportsView.vue') },
  { path: '/mainlines', component: () => import('@/views/MainlinesView.vue') },
  { path: '/settings', component: () => import('@/views/SettingsView.vue') },
] });
router.beforeEach((to) => { if (!to.meta.public && !hasToken()) return '/login'; if (to.meta.public && hasToken()) return '/'; });
export default router;
