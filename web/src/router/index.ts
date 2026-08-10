import { createRouter, createWebHistory } from 'vue-router';
import { hasToken } from '@/api/client';
import DashboardView from '@/views/DashboardView.vue';
const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  { path: '/', component: DashboardView },
  { path: '/plans', component: () => import('@/views/PlansView.vue') },
  { path: '/plans/:id', component: () => import('@/views/PlanDetailView.vue') },
  { path: '/mainlines', component: () => import('@/views/MainlinesView.vue') },
  { path: '/settings', component: () => import('@/views/SettingsView.vue') },
] });
router.beforeEach((to) => { if (!to.meta.public && !hasToken()) return '/login'; if (to.meta.public && hasToken()) return '/'; });
export default router;
