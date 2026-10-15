import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useAuthStore } from '@/stores/auth';
import './style.css';
import './android.css';
import './mobile.css';
import { setupPwa } from './pwa';
import { initializeDesktop, isNativeClient as isDesktop, isAndroid } from './desktop';
import { initializeClientUpdates, checkClientUpdate } from './clientUpdates';
import { workspace, workspaceEntry } from './workspace';
import { workspaceHome } from './workspaceModel';
import { aiNativeResumePath } from './aiPlanning';
const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
async function bootstrap(): Promise<void> {
  await initializeDesktop();
  const aiResume = isDesktop() ? aiNativeResumePath(location.hash) : null;
  await initializeClientUpdates();
  const auth = useAuthStore(pinia);
  if (!isDesktop()) await auth.bootstrap();
  app.use(router);
  await router.isReady();
  app.mount('#app');
  if (isDesktop() && !isAndroid()) void checkClientUpdate();
  if (isAndroid()) {
    const { App: androidApp } = await import('@capacitor/app');
    await androidApp.addListener('backButton', ({ canGoBack }) => {
      const dialog = document.querySelector<HTMLDialogElement>('dialog[open]');
      if (dialog) { dialog.dispatchEvent(new Event('cancel', { cancelable: true })); return; }
      const path = router.currentRoute.value.path;
      if (['/', '/routes', '/todos', '/login'].includes(path)) {
        if (window.confirm('退出计划舱？正在运行的计时不会因此停止。')) void androidApp.exitApp();
      } else if (canGoBack) router.back();
      else void router.replace(auth.user ? workspaceHome(workspace.mode) : '/login');
    });
  }
  if (isDesktop()) {
    await auth.bootstrap();
    if (auth.user && router.currentRoute.value.path === '/login') await router.replace(aiResume ?? await workspaceEntry());
    if (isAndroid() && auth.user) void checkClientUpdate();
  }
}
void bootstrap().catch(() => {
  const root = document.getElementById('app');
  if (root) root.textContent = '客户端初始化失败，请关闭后重试。如果问题持续，请保留配置文件并联系维护者。';
});
setupPwa();
