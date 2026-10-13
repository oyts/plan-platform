import { describe, expect, it, vi, beforeEach } from 'vitest';
const platform = vi.hoisted(() => ({ android: false, desktop: false, native: vi.fn(), tauri: vi.fn(), credentials: vi.fn() }));
vi.mock('./api/client', () => ({ nativeUpdateAuthorization: platform.credentials }));
vi.mock('@tauri-apps/api/core', () => ({ invoke: platform.tauri, isTauri: () => platform.desktop }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => platform.android, getPlatform: () => platform.android ? 'android' : 'web' }, registerPlugin: () => ({ execute: platform.native }) }));
import { isAndroid, isDesktop, isNativeClient, nativeInvoke, initializeDesktop, checkDesktopUpdate, downloadDesktopUpdate, installDesktopUpdate } from './desktop';
beforeEach(() => { platform.android = false; platform.desktop = false; platform.native.mockReset(); platform.tauri.mockReset(); platform.credentials.mockReset(); });
describe('native platform dispatch', () => {
  it('does not treat the browser as an installed client', () => { expect(isNativeClient()).toBe(false); });
  it('routes Android requests to Java and unwraps results', async () => {
    platform.android = true; platform.native.mockResolvedValue({ value: { status: 200 } });
    expect(isAndroid()).toBe(true); expect(isDesktop()).toBe(false); expect(isNativeClient()).toBe(true);
    expect(await nativeInvoke('desktop_request', { request: { path: '/api/plans' } })).toEqual({ status: 200 });
    expect(platform.native).toHaveBeenCalledWith({ command: 'desktop_request', args: { request: { path: '/api/plans' } } });
    expect(platform.tauri).not.toHaveBeenCalled();
  });
  it('keeps Windows commands on the Rust bridge', async () => {
    platform.desktop = true; platform.tauri.mockResolvedValue({ version: '0.1.0' });
    expect(await nativeInvoke('desktop_info')).toEqual({ version: '0.1.0' }); expect(platform.native).not.toHaveBeenCalled();
  });
  it('routes all Android updater operations through Java, not Tauri', async () => {
    platform.android = true; platform.native.mockResolvedValue({ value: null });
    await checkDesktopUpdate(); await downloadDesktopUpdate(); await installDesktopUpdate();
    expect(platform.native.mock.calls.map(([input]) => input.command)).toEqual(['desktop_check_update', 'desktop_download_update', 'desktop_install_update']);
    expect(platform.tauri).not.toHaveBeenCalled();
  });
  it('protected Android commands receive fresh credentials, never a refresh cookie', async () => {
    platform.android=true;
    vi.stubGlobal('document',{documentElement:{classList:{add:vi.fn()}},addEventListener:vi.fn()});
    platform.native.mockResolvedValueOnce({value:{version:'0.1.6',serverUrl:'https://plan.example.cn',updateRequiresLogin:true}});
    await initializeDesktop();
    const credentials={accessToken:'header.payload.signature',ownerId:'user-one',serverUrl:'https://plan.example.cn'};
    platform.credentials.mockResolvedValue(credentials);platform.native.mockResolvedValue({value:null});
    await checkDesktopUpdate();await downloadDesktopUpdate();await installDesktopUpdate();
    expect(platform.credentials).toHaveBeenCalledTimes(3);
    for(const [input] of platform.native.mock.calls.slice(1))expect(input.args).toEqual(credentials);
    expect(platform.tauri).not.toHaveBeenCalled();
  });
  it('expired or offline protected login blocks native download before transmission', async () => {
    platform.android=true;platform.credentials.mockRejectedValue(new Error('请先联网登录'));
    await expect(downloadDesktopUpdate()).rejects.toThrow('联网登录');expect(platform.native).not.toHaveBeenCalled();
  });
});
