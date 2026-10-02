import { describe, expect, it, vi, beforeEach } from 'vitest';
const platform = vi.hoisted(() => ({ android: false, desktop: false, native: vi.fn(), tauri: vi.fn() }));
vi.mock('@tauri-apps/api/core', () => ({ invoke: platform.tauri, isTauri: () => platform.desktop }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => platform.android, getPlatform: () => platform.android ? 'android' : 'web' }, registerPlugin: () => ({ execute: platform.native }) }));
import { isAndroid, isDesktop, isNativeClient, nativeInvoke } from './desktop';
beforeEach(() => { platform.android = false; platform.desktop = false; platform.native.mockReset(); platform.tauri.mockReset(); });
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
});
