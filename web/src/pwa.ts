import { computed, onMounted, onUnmounted, ref } from 'vue';

interface InstallPromptEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }
const installPrompt = ref<InstallPromptEvent | null>(null); const updateReady = ref(false); const online = ref(navigator.onLine);
let registration: ServiceWorkerRegistration | null = null;

export function setupPwa(): void {
  if (!('serviceWorker' in navigator)) return;
  navigator.serviceWorker.register('/sw.js').then((registered) => {
    registration = registered;
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
  function update() { registration?.waiting?.postMessage('SKIP_WAITING'); window.location.reload(); }
  return { canInstall: computed(() => Boolean(installPrompt.value) && !standalone.value), standalone, updateReady, online, install, update };
}
