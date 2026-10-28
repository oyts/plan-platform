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
import { weeklyNativeResumePath } from './weeklyDraft';
import {validReminderPath} from './reminders';
import {onQueueChange,queueCount} from './offlineQueue';
import {currentQueueOwner} from './api/client';
const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
async function bootstrap(): Promise<void> {
  await initializeDesktop();
  const nativeResume = isDesktop() ? aiNativeResumePath(location.hash) ?? weeklyNativeResumePath(location.hash) : null;
  await initializeClientUpdates();
  const auth = useAuthStore(pinia);
  if (!isDesktop()) await auth.bootstrap();
  app.use(router);
  await router.isReady();
  app.mount('#app');
  if (isDesktop() && !isAndroid()) void checkClientUpdate('automatic');
  if (isAndroid()) {
    const { App: androidApp } = await import('@capacitor/app');
    const openReminder=async()=>{if(!auth.user)return;const {nativeInvoke}=await import('./desktop');const path=await nativeInvoke<unknown>('reminder_take_path');if(validReminderPath(path))await router.push(path);};
    await androidApp.addListener('appStateChange',({isActive})=>{if(isActive){void openReminder();void checkClientUpdate('automatic');}});
    window.addEventListener('plan-platform:reminder-ready',()=>{void openReminder();});
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
    const suspendForQueue=async()=>{const owner=currentQueueOwner();if(!owner)return;try{if(await queueCount(owner)>0&&owner===currentQueueOwner()){const {nativeInvoke}=await import('./desktop');await nativeInvoke('reminder_clear');}}catch{/* No new reminder is enabled when queue inspection fails. */}};
    onQueueChange(()=>{void suspendForQueue();});
    window.addEventListener('plan-platform:reminder-refresh',()=>{void import('./desktop').then(m=>m.nativeInvoke('reminder_refresh')).catch(()=>undefined);});
    window.addEventListener('plan-platform:session-invalidated',()=>{void import('./desktop').then(m=>m.nativeInvoke('reminder_clear')).catch(()=>undefined);});
    await auth.bootstrap();
    await suspendForQueue();
    if (auth.user && router.currentRoute.value.path === '/login') await router.replace(nativeResume ?? await workspaceEntry());
    if (isAndroid() && auth.user) void checkClientUpdate('automatic');
    if(isAndroid()&&auth.user)window.dispatchEvent(new Event('plan-platform:reminder-ready'));
  }
}
void bootstrap().catch(() => {
  const root = document.getElementById('app');
  if (root) root.textContent = '客户端初始化失败，请关闭后重试。如果问题持续，请保留配置文件并联系维护者。';
});
setupPwa();
