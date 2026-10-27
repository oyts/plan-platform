import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
const native = vi.hoisted(() => ({ check: vi.fn(), download: vi.fn(), install: vi.fn(), info: vi.fn(), invoke: vi.fn() }));
vi.mock('@/desktop', () => ({ isNativeClient: () => true, getDesktopInfo: native.info, checkDesktopUpdate: native.check, downloadDesktopUpdate: native.download, installDesktopUpdate: native.install, nativeInvoke: native.invoke }));
import { clientUpdates as state, initializeClientUpdates, checkClientUpdate, setAutomaticDownload, downloadClientUpdate, installClientUpdate, dismissClientUpdate, requiredClientUpdate, updateRouteAllowed, bindClientUpdateOwner,clientNoticeVisible,ignoreClientUpdate,restoreClientUpdateNotice } from './clientUpdates';
const info = { version: '0.1.1', versionCode: 5, updatesEnabled: true, updateChannel: 'https://plan.example.cn/client-updates/android/latest.json', serverUrl: 'https://plan.example.cn' };
const optional = { version: '0.1.2', versionCode: 6, notes: '测试更新', mandatory: false };
const storage = new Map<string, string>();
beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key), clear: () => storage.clear() });
  vi.stubGlobal('window', { confirm: vi.fn() });
  localStorage.clear(); vi.clearAllMocks();
  Object.assign(state, { info: null, update: null, checking: false, downloading: false, installing: false, ready: false, autoDownload: false, dismissedVersion: '', error: '', message: '', ownerKey: '',ignored:false,snoozeUntil:0,nextCheck:0,failures:0,noticeTime:Date.now() });
  vi.stubGlobal('navigator',{onLine:true});
  native.info.mockResolvedValue(info); native.check.mockResolvedValue(optional); native.download.mockResolvedValue(undefined); native.install.mockResolvedValue(undefined);
});
afterEach(()=>vi.useRealTimers());
describe('client update consent and lifecycle', () => {
  it('invited download consent and mandatory policy do not transfer to another account', async () => {
    native.info.mockResolvedValue({ ...info, updateRequiresLogin: true });
    await initializeClientUpdates(); setAutomaticDownload(true); expect(state.autoDownload).toBe(false);
    bindClientUpdateOwner('server|user-a'); setAutomaticDownload(true);
    native.check.mockResolvedValue({ ...optional, mandatory: true }); await checkClientUpdate();
    expect(requiredClientUpdate()).toBe(true); expect(state.ready).toBe(true);
    bindClientUpdateOwner('server|user-b'); expect(state.autoDownload).toBe(false); expect(state.ready).toBe(false); expect(state.update).toBeNull();
    bindClientUpdateOwner('server|user-a'); expect(state.autoDownload).toBe(true); expect(requiredClientUpdate()).toBe(true); expect(state.ready).toBe(false);
    bindClientUpdateOwner(null); expect(state.autoDownload).toBe(false); expect(requiredClientUpdate()).toBe(false);
  });
  it('logout while an invited download is pending never restores its ready state', async () => {
    native.info.mockResolvedValue({ ...info, updateRequiresLogin: true });
    await initializeClientUpdates(); bindClientUpdateOwner('server|user-a'); await checkClientUpdate();
    let finish!: () => void; native.download.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve; }));
    const downloading = downloadClientUpdate(); bindClientUpdateOwner(null); finish(); await downloading;
    expect(state.ready).toBe(false); expect(state.update).toBeNull(); expect(state.message).toBe('');
  });
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
describe('bounded checks and durable version choices',()=>{
  it('coalesces startup/login/foreground and persists cooldown across reopen; manual bypasses it',async()=>{
    await initializeClientUpdates();await Promise.all([checkClientUpdate('automatic'),checkClientUpdate('automatic'),checkClientUpdate('automatic')]);
    expect(native.check).toHaveBeenCalledTimes(1);
    state.update=null;await initializeClientUpdates();await checkClientUpdate('automatic');expect(native.check).toHaveBeenCalledTimes(1);
    await checkClientUpdate();expect(native.check).toHaveBeenCalledTimes(2);
  });
  it('offline automatic checks make no request and a new installed build checks immediately',async()=>{
    await initializeClientUpdates();vi.stubGlobal('navigator',{onLine:false});await checkClientUpdate('automatic');expect(native.check).not.toHaveBeenCalled();
    vi.stubGlobal('navigator',{onLine:true});await checkClientUpdate('automatic');
    native.info.mockResolvedValue({...info,version:'0.1.2',versionCode:6});await initializeClientUpdates();await checkClientUpdate('automatic');expect(native.check).toHaveBeenCalledTimes(2);
  });
  it('backs off failures without converting errors into required updates, manual retry remains possible',async()=>{
    vi.useFakeTimers();vi.setSystemTime(new Date('2026-10-27T08:00:00Z'));
    await initializeClientUpdates();native.check.mockRejectedValue(new Error('无网'));
    await checkClientUpdate('automatic');expect(state.nextCheck-Date.now()).toBe(60000);expect(requiredClientUpdate()).toBe(false);
    await checkClientUpdate('automatic');expect(native.check).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(60000);await checkClientUpdate('automatic');expect(state.nextCheck-Date.now()).toBe(120000);
    native.check.mockResolvedValue(optional);await checkClientUpdate();expect(state.failures).toBe(0);expect(state.nextCheck-Date.now()).toBe(900000);
  });
  it('does not request an invited manifest before an owner is bound',async()=>{
    native.info.mockResolvedValue({...info,updateRequiresLogin:true});await initializeClientUpdates();await checkClientUpdate('automatic');await checkClientUpdate();
    expect(native.check).not.toHaveBeenCalled();expect(state.error).toContain('登录');
    bindClientUpdateOwner('server|a');await checkClientUpdate('automatic');expect(native.check).toHaveBeenCalledTimes(1);
  });
  it('ignores one build across reopen, preserves manual download and restores on demand',async()=>{
    await initializeClientUpdates();await checkClientUpdate();ignoreClientUpdate();expect(clientNoticeVisible()).toBe(false);expect(state.autoDownload).toBe(false);
    state.update=null;state.ignored=false;await initializeClientUpdates();await checkClientUpdate();expect(clientNoticeVisible()).toBe(false);
    restoreClientUpdateNotice();expect(clientNoticeVisible()).toBe(true);
    ignoreClientUpdate();await downloadClientUpdate();expect(state.ready).toBe(true);
  });
  it('a new version or Android build prompts again',async()=>{
    await initializeClientUpdates();await checkClientUpdate();ignoreClientUpdate();
    native.check.mockResolvedValue({...optional,versionCode:7});await checkClientUpdate();expect(clientNoticeVisible()).toBe(true);
    ignoreClientUpdate();native.check.mockResolvedValue({...optional,version:'0.1.3',versionCode:8});await checkClientUpdate();expect(clientNoticeVisible()).toBe(true);
  });
  it('snoozes only the selected version for one hour and persists it',async()=>{
    vi.useFakeTimers();vi.setSystemTime(new Date('2026-10-27T08:00:00Z'));
    await initializeClientUpdates();await checkClientUpdate();dismissClientUpdate();expect(clientNoticeVisible()).toBe(false);
    state.update=null;await initializeClientUpdates();await checkClientUpdate();expect(clientNoticeVisible()).toBe(false);
    vi.advanceTimersByTime(3600000);expect(clientNoticeVisible()).toBe(true);
  });
  it('separates ignored versions and auto consent by invited account and trusted channel',async()=>{
    native.info.mockResolvedValue({...info,updateRequiresLogin:true});await initializeClientUpdates();bindClientUpdateOwner('server|a');await checkClientUpdate();ignoreClientUpdate();
    bindClientUpdateOwner('server|b');await checkClientUpdate();expect(clientNoticeVisible()).toBe(true);
    bindClientUpdateOwner('server|a');await checkClientUpdate();expect(clientNoticeVisible()).toBe(false);
    native.info.mockResolvedValue({...info,updateRequiresLogin:true,updateChannel:'https://another.example.cn/latest.json'});await initializeClientUpdates();await checkClientUpdate();expect(clientNoticeVisible()).toBe(true);
  });
  it('a verified mandatory policy overrides ignored/snoozed choices and cannot be hidden',async()=>{
    await initializeClientUpdates();await checkClientUpdate();ignoreClientUpdate();dismissClientUpdate();
    native.check.mockResolvedValue({...optional,mandatory:true});await checkClientUpdate();expect(clientNoticeVisible()).toBe(true);
    ignoreClientUpdate();dismissClientUpdate();expect(clientNoticeVisible()).toBe(true);expect(requiredClientUpdate()).toBe(true);
    expect(updateRouteAllowed('/sync')).toBe(true);expect(updateRouteAllowed('/exports')).toBe(true);
  });
  it('discarded account responses cannot set policy or preferences for the next account',async()=>{
    native.info.mockResolvedValue({...info,updateRequiresLogin:true});await initializeClientUpdates();bindClientUpdateOwner('server|a');
    let finish!:(v:typeof optional)=>void;native.check.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
    const pending=checkClientUpdate();bindClientUpdateOwner('server|b');finish({...optional,mandatory:true});await pending;
    expect(state.update).toBeNull();expect(state.nextCheck).toBe(0);expect(requiredClientUpdate()).toBe(false);
  });
  it('storage failure cannot silently enable auto download or hide an optional update',async()=>{
    await initializeClientUpdates();await checkClientUpdate();vi.spyOn(localStorage,'setItem').mockImplementation(()=>{throw new Error('full');});
    ignoreClientUpdate();expect(clientNoticeVisible()).toBe(true);setAutomaticDownload(true);expect(state.autoDownload).toBe(false);
  });
  it('missing notes are allowed, remote text is bounded, malformed results do not erase a verified required policy',async()=>{
    await initializeClientUpdates();native.check.mockResolvedValue({...optional,notes:null});await checkClientUpdate();expect(state.update?.notes).toBeNull();
    native.check.mockResolvedValue({...optional,notes:'a'.repeat(8000),mandatory:true});await checkClientUpdate();expect(state.update?.notes?.length).toBe(4000);
    native.check.mockResolvedValue({...optional,versionCode:-1});await checkClientUpdate();expect(requiredClientUpdate()).toBe(true);expect(state.error).toContain('格式');
  });
});
