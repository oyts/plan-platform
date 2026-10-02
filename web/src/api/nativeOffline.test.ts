import { IDBFactory, IDBKeyRange } from 'fake-indexeddb';
import { beforeEach, describe, expect, it, vi } from 'vitest';
const native = vi.hoisted(() => ({ invoke: vi.fn() }));
vi.mock('@/desktop', () => ({ isNativeClient: () => true, desktopScope: () => 'http://127.0.0.1:43129', nativeInvoke: native.invoke }));
vi.stubGlobal('localStorage', { removeItem: vi.fn() });
const { api, saveTokens, clearTokens, currentQueueOwner, serverScope } = await import('./client');
const { rememberIdentity, cachedIdentity } = await import('@/offlineCache');
const user = { id: 'one', email: 'one@example.test', displayName: '原生测试', role: 'USER', emailVerified: false };
beforeEach(async () => {
  vi.stubGlobal('indexedDB', new IDBFactory()); vi.stubGlobal('IDBKeyRange', IDBKeyRange); vi.stubGlobal('window', new EventTarget()); vi.stubGlobal('navigator', { onLine: true });
  native.invoke.mockReset(); clearTokens(); saveTokens({ accessToken: 'test-only', user, expiresInSeconds: 60 });
  await rememberIdentity(serverScope(), currentQueueOwner()!, user);
});
describe('native transport offline behavior', () => {
  it('keeps a successful online read usable when device storage is unavailable', async () => {
    vi.stubGlobal('indexedDB', { open: () => { throw new Error('storage denied'); } });
    native.invoke.mockResolvedValue({ status: 200, headers: {}, body: '[]' });
    expect((await api.get('/plans')).data).toEqual([]);
  });
  it('uses JSON cache when USB forwarding breaks while Wi-Fi remains online', async () => {
    native.invoke.mockResolvedValueOnce({ status: 200, headers: { 'content-type': 'application/json' }, body: '[{"id":"p","name":"原生缓存"}]' });
    await api.get('/plans'); native.invoke.mockRejectedValue(new Error('connection lost'));
    expect((await api.get('/plans')).data).toEqual([{ id: 'p', name: '原生缓存' }]);
    const calls = native.invoke.mock.calls.length;
    expect((await api.get('/plans')).data).toHaveLength(1); expect(native.invoke).toHaveBeenCalledTimes(calls);
  });
  it('fails closed after 401 plus a rejected refresh', async () => {
    native.invoke.mockResolvedValueOnce({ status: 200, headers: {}, body: '[]' }); await api.get('/plans');
    native.invoke.mockResolvedValue({ status: 401, headers: {}, body: '{}' });
    await expect(api.get('/plans')).rejects.toMatchObject({ response: { status: 401 } });
    expect(currentQueueOwner()).toBeNull(); expect(await cachedIdentity(serverScope())).toBeNull();
  });
  it('does not replace an explicit 401 with cache when refresh then loses network', async () => {
    native.invoke.mockResolvedValueOnce({ status: 200, headers: {}, body: '[]' }); await api.get('/plans');
    native.invoke.mockResolvedValueOnce({ status: 401, headers: {}, body: '{}' }).mockRejectedValue(new Error('connection lost'));
    await expect(api.get('/plans')).rejects.toThrow('connection lost'); expect(currentQueueOwner()).toBeNull();
  });
});
