import { computed, onMounted, onUnmounted, ref } from 'vue';
import { isNativeClient as isDesktop } from '@/desktop';

interface InstallPromptEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }
const installPrompt = ref<InstallPromptEvent | null>(null); const updateReady = ref(false); const online = ref(navigator.onLine);
let registration: ServiceWorkerRegistration | null = null;

export function setupPwa(): void {
  if (isDesktop() || !('serviceWorker' in navigator)) return;
  navigator.serviceWorker.register('/sw.js').then((registered) => {
    registration = registered;
    updateReady.value=!!registered.waiting;
    registered.addEventListener('updatefound', () => {
      const worker = registered.installing;
      worker?.addEventListener('statechange', () => { if (worker.state === 'installed' && navigator.serviceWorker.controller) updateReady.value = true; });
    });
  }).catch(() => undefined);
}
export function usePwaStatus() {
  const standalone = computed(() => window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true);
  const onPrompt = (event: Event) => { event.preventDefault(); installPrompt.value = event as InstallPromptEvent; };
  const onOnline = () => { online.value = true; }; const onOffline = () => { online.value = false; };
  onMounted(() => { window.addEventListener('beforeinstallprompt', onPrompt); window.addEventListener('online', onOnline); window.addEventListener('offline', onOffline); });
  onUnmounted(() => { window.removeEventListener('beforeinstallprompt', onPrompt); window.removeEventListener('online', onOnline); window.removeEventListener('offline', onOffline); });
  async function install() { const prompt = installPrompt.value; if (!prompt) return; await prompt.prompt(); await prompt.userChoice; installPrompt.value = null; }
  function update() {
    if(!registration?.waiting)return;
    if(!window.confirm('刷新网页版本会重新加载页面，请先保存未提交的输入。本机待同步队列会保留，不会自动提交。现在刷新吗？'))return;
    navigator.serviceWorker.addEventListener('controllerchange',()=>window.location.reload(),{once:true});
    registration.waiting.postMessage('SKIP_WAITING');
  }
  return { canInstall: computed(() => Boolean(installPrompt.value) && !standalone.value), standalone, updateReady, online, install, update };
}
