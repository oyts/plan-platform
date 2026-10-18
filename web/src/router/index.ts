import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router';
import { hasSession } from '@/api/client';
import { isNativeClient as isDesktop } from '@/desktop';
import { requiredClientUpdate, updateRouteAllowed } from '@/clientUpdates';
import { observeWorkspacePath, workspaceEntry } from '@/workspace';
import HomeEntryView from '@/views/HomeEntryView.vue';
import ProductHomeView from '@/views/ProductHomeView.vue';
import { navigationScroll } from './scroll';
const router = createRouter({ history: isDesktop() ? createWebHashHistory() : createWebHistory(), scrollBehavior: navigationScroll, routes: [
  { path: '/desktop', component: () => import('@/views/DesktopView.vue'), meta: { public: true, allowAuthenticated: true } },
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  { path: '/reset-password', component: () => import('@/views/ResetPasswordView.vue'), meta: { public: true } },
  { path: '/', component: HomeEntryView, meta: { public: true, allowAuthenticated: true } },
  { path: '/welcome', component: ProductHomeView, meta: { public: true, allowAuthenticated: true, landing: true } },
  { path: '/todos', component: () => import('@/views/TodosView.vue') },
  { path: '/ai', component: () => import('@/views/AiPlanningView.vue') },
  { path: '/overview', component: () => import('@/views/DailyOverviewView.vue') },
  { path: '/weekly-review', component: () => import('@/views/WeeklyReviewView.vue') },
  { path: '/feedback', component: () => import('@/views/FeedbackView.vue') },
  { path: '/organize', component: () => import('@/views/OrganizeView.vue') },
  { path: '/offline', component: () => import('@/views/OfflineView.vue') },
  { path: '/operations', component: () => import('@/views/OperationsView.vue') },
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
  { path: '/routes', component: () => import('@/views/RouteHomeView.vue') },
  { path: '/route-progress', component: () => import('@/views/RouteProgressView.vue') },
  { path: '/route-records', component: () => import('@/views/RecordsView.vue'), props: { routeOnly: true } },
  { path: '/sync', component: () => import('@/views/SyncView.vue') },
  { path: '/settings', component: () => import('@/views/SettingsView.vue') },
  { path: '/learn/:lesson?', component: () => import('@/views/LearningView.vue') },
] });
router.beforeEach((to) => { if (requiredClientUpdate() && !updateRouteAllowed(to.path)) return '/desktop'; if ((isDesktop() && to.path === '/' || !to.meta.public) && !hasSession()) return '/login'; if (to.meta.public && hasSession() && !to.meta.allowAuthenticated) return requiredClientUpdate() ? '/desktop' : workspaceEntry(); });
router.beforeResolve(async (to) => { if (hasSession() && (!to.meta.public || to.path === '/')) await observeWorkspacePath(to.fullPath); });
export default router;
