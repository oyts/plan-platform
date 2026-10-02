import { reactive } from 'vue';

export const connectionState = reactive({
  reachable: typeof navigator === 'undefined' || navigator.onLine,
  offlineSession: false,
  cacheReadAt: 0,
  cacheAvailable: true,
});
export function markConnected(): void { connectionState.reachable = true; }
export function markDisconnected(): void { connectionState.reachable = false; }
export function requiresConnection(): boolean { return !connectionState.reachable || connectionState.offlineSession; }
if (typeof window !== 'undefined') window.addEventListener('offline', markDisconnected);
