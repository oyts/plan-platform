import { invoke, isTauri } from '@tauri-apps/api/core';
import { Capacitor, registerPlugin } from '@capacitor/core';
interface AndroidBridge { execute(options: { command: string; args: Record<string, unknown> }): Promise<{ value: unknown }> }
const androidBridge = registerPlugin<AndroidBridge>('PlanNative');

export interface DesktopInfo { version: string; serverUrl: string; updatesEnabled: boolean; debug?: boolean; versionCode?: number; updateChannel?: string; updateRequiresLogin?: boolean }
export interface DesktopUpdate { version: string; notes: string | null; mandatory: boolean; minimumVersion?: string; versionCode?: number; accountId?: string }
let info: DesktopInfo | null = null;
export function isDesktop(): boolean { return isTauri(); }
export function isAndroid(): boolean { return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android'; }
export function isNativeClient(): boolean { return isDesktop() || isAndroid(); }
export async function nativeInvoke<T>(command: string, args: Record<string, unknown> = {}): Promise<T> {
  if (isAndroid()) return (await androidBridge.execute({ command, args })).value as T;
  return invoke<T>(command, args);
}
export function desktopScope(): string { return info?.serverUrl ?? ''; }
export async function initializeDesktop(): Promise<void> {
  if (!isNativeClient()) return;
  info = await nativeInvoke<DesktopInfo>('desktop_info');
  document.documentElement.classList.add(isAndroid() ? 'android-client' : 'desktop-client');
  document.addEventListener('click', (event) => {
    const link = (event.target as HTMLElement).closest?.('a[href]') as HTMLAnchorElement | null;
    if (!link || !/^https?:/.test(link.href) || link.origin === window.location.origin) return;
    event.preventDefault();
    void nativeInvoke('desktop_open_url', { url: link.href }).catch(() => { window.alert('无法打开外部链接，请检查设备浏览器。'); });
  });
}
export async function getDesktopInfo(): Promise<DesktopInfo> { return info ?? await nativeInvoke<DesktopInfo>('desktop_info'); }
export async function saveDesktopServer(serverUrl: string): Promise<void> { await nativeInvoke('desktop_save_server', { serverUrl }); }
export async function probeDesktopServer(serverUrl: string): Promise<string> { return nativeInvoke<string>('desktop_probe', { serverUrl }); }
export async function clearNativeSession(): Promise<void> { if (isNativeClient()) await nativeInvoke('clear_native_session'); }
async function updateAuthorization(): Promise<Record<string, unknown>> {
  if (!isAndroid() || !info?.updateRequiresLogin) return {};
  // Resolve after client initialization to avoid an auth/client/update module cycle.
  return (await import('./api/client')).nativeUpdateAuthorization();
}
export async function checkDesktopUpdate(): Promise<DesktopUpdate | null> { return nativeInvoke<DesktopUpdate | null>('desktop_check_update', await updateAuthorization()); }
export async function downloadDesktopUpdate(): Promise<void> { await nativeInvoke('desktop_download_update', await updateAuthorization()); }
export async function installDesktopUpdate(): Promise<void> { await nativeInvoke('desktop_install_update', await updateAuthorization()); }
