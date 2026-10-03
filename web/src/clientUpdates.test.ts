import { beforeEach, describe, expect, it, vi } from 'vitest';
const native = vi.hoisted(() => ({ check: vi.fn(), download: vi.fn(), install: vi.fn(), info: vi.fn(), invoke: vi.fn() }));
vi.mock('@/desktop', () => ({ isNativeClient: () => true, getDesktopInfo: native.info, checkDesktopUpdate: native.check, downloadDesktopUpdate: native.download, installDesktopUpdate: native.install, nativeInvoke: native.invoke }));
import { clientUpdates as state, initializeClientUpdates, checkClientUpdate, setAutomaticDownload, downloadClientUpdate, installClientUpdate, dismissClientUpdate, requiredClientUpdate, updateRouteAllowed } from './clientUpdates';
const info = { version: '0.1.1', versionCode: 5, updatesEnabled: true, updateChannel: 'https://plan.example.cn/client-updates/android/latest.json', serverUrl: 'https://plan.example.cn' };
const optional = { version: '0.1.2', versionCode: 6, notes: '测试更新', mandatory: false };
const storage = new Map<string, string>();
beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key), clear: () => storage.clear() });
  vi.stubGlobal('window', { confirm: vi.fn() });
  localStorage.clear(); vi.clearAllMocks();
  Object.assign(state, { info: null, update: null, checking: false, downloading: false, installing: false, ready: false, autoDownload: false, dismissedVersion: '', error: '', message: '' });
  native.info.mockResolvedValue(info); native.check.mockResolvedValue(optional); native.download.mockResolvedValue(undefined); native.install.mockResolvedValue(undefined);
});
describe('client update consent and lifecycle', () => {
  it('checks on demand without automatic download or install by default', async () => {
    await initializeClientUpdates(); await checkClientUpdate();
    expect(state.autoDownload).toBe(false); expect(state.update).toEqual(optional); expect(native.download).not.toHaveBeenCalled(); expect(native.install).not.toHaveBeenCalled();
    dismissClientUpdate(); expect(state.dismissedVersion).toBe('0.1.2');
  });
  it('automatic download needs explicit channel-bound consent and never installs', async () => {
    await initializeClientUpdates(); setAutomaticDownload(true); await checkClientUpdate();
    expect(native.download).toHaveBeenCalledTimes(1); expect(state.ready).toBe(true); expect(native.install).not.toHaveBeenCalled();
    state.autoDownload = false; await initializeClientUpdates(); expect(state.autoDownload).toBe(true);
    native.info.mockResolvedValue({ ...info, updateChannel: 'https://another.example.cn/latest.json' }); await initializeClientUpdates(); expect(state.autoDownload).toBe(false);
  });
  it('does not mark ready after checksum/signature/download failure and supports retry', async () => {
    await initializeClientUpdates(); await checkClientUpdate(); native.download.mockRejectedValueOnce(new Error('签名校验失败'));
    await downloadClientUpdate(); expect(state.ready).toBe(false); expect(state.error).toContain('签名');
    await downloadClientUpdate(); expect(state.ready).toBe(true);
  });
  it('installer cannot start before download or after user cancels confirmation', async () => {
    await initializeClientUpdates(); await checkClientUpdate(); await installClientUpdate(); expect(native.install).not.toHaveBeenCalled();
    await downloadClientUpdate(); vi.spyOn(window, 'confirm').mockReturnValueOnce(false); await installClientUpdate(); expect(native.install).not.toHaveBeenCalled();
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true); await installClientUpdate(); expect(native.install).toHaveBeenCalledTimes(1);
    native.install.mockRejectedValueOnce(new Error('更新缓存校验失败，请重新下载。'));
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true); await installClientUpdate(); expect(state.ready).toBe(false);
    await downloadClientUpdate(); expect(state.ready).toBe(true);
  });
  it('mandatory update cannot be dismissed and survives offline restart for the same build', async () => {
    await initializeClientUpdates(); native.check.mockResolvedValue({ ...optional, mandatory: true }); await checkClientUpdate();
    dismissClientUpdate(); expect(state.dismissedVersion).toBe(''); expect(requiredClientUpdate()).toBe(true);
    state.update = null; await initializeClientUpdates(); expect(requiredClientUpdate()).toBe(true);
    native.check.mockRejectedValueOnce(new Error('离线')); await checkClientUpdate(); expect(requiredClientUpdate()).toBe(true);
    expect(updateRouteAllowed('/plans')).toBe(false); expect(updateRouteAllowed('/desktop')).toBe(true); expect(updateRouteAllowed('/exports')).toBe(true); expect(updateRouteAllowed('/sync')).toBe(true);
  });
  it('a new installation does not inherit the old build mandatory cache', async () => {
    await initializeClientUpdates(); native.check.mockResolvedValue({ ...optional, mandatory: true }); await checkClientUpdate();
    state.update = null; native.info.mockResolvedValue({ ...info, version: '0.1.2', versionCode: 6 }); await initializeClientUpdates(); expect(requiredClientUpdate()).toBe(false);
  });
  it('rejects malformed bridge results without downloading', async () => {
    await initializeClientUpdates(); native.check.mockResolvedValue({ version: '0.1.2' }); await checkClientUpdate(); expect(state.update).toBeNull(); expect(state.error).toContain('格式'); expect(native.download).not.toHaveBeenCalled();
  });
  it.each(['签名校验失败', '更新包完整性校验失败', '下载中断'])('refuses installation after %s without changing consent', async (message) => {
    await initializeClientUpdates(); await checkClientUpdate();
    native.download.mockRejectedValueOnce(new Error(message));
    await downloadClientUpdate(); await installClientUpdate();
    expect(state.ready).toBe(false); expect(state.autoDownload).toBe(false);
    expect(state.info?.version).toBe(info.version); expect(state.error).toContain(message);
    expect(native.install).not.toHaveBeenCalled();
  });
  it('failed required download remains mandatory until a verified optional policy arrives', async () => {
    await initializeClientUpdates(); native.check.mockResolvedValue({ ...optional, mandatory: true });
    await checkClientUpdate(); native.download.mockRejectedValueOnce(new Error('下载中断'));
    await downloadClientUpdate(); expect(requiredClientUpdate()).toBe(true);
    state.update = null; await initializeClientUpdates(); expect(requiredClientUpdate()).toBe(true);
    native.check.mockResolvedValue(optional); await checkClientUpdate();
    expect(requiredClientUpdate()).toBe(false); state.update = null;
    await initializeClientUpdates(); expect(requiredClientUpdate()).toBe(false);
    expect(native.install).not.toHaveBeenCalled();
  });
});
