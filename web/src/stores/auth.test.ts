import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
const mocks = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn(), clearTokens: vi.fn(), clearNative: vi.fn(), identity: vi.fn(), forget: vi.fn(), restore: vi.fn(), signedOut: vi.fn() }));
vi.mock('@/api/client', () => ({ api: { post: mocks.post, get: mocks.get }, clearTokens: mocks.clearTokens, saveTokens: vi.fn(), serverScope: () => 'server', currentQueueOwner: () => 'user', restoreOfflineIdentity: mocks.restore }));
vi.mock('@/offlineCache', () => ({ cachedIdentity: mocks.identity, forgetScope: mocks.forget, signOutScope: mocks.forget, isSignedOut: mocks.signedOut, rememberIdentity: vi.fn() }));
vi.mock('@/desktop', () => ({ clearNativeSession: mocks.clearNative }));
import { useAuthStore } from './auth';
import { connectionState } from '@/connectionState';
beforeEach(() => { setActivePinia(createPinia()); Object.values(mocks).forEach(mock => mock.mockReset()); mocks.forget.mockResolvedValue(undefined); connectionState.reachable = true; connectionState.offlineSession = false; });
describe('native logout safety', () => {
  it.each(['logout', 'logoutAll'] as const)('clears local credentials even when %s cannot reach the server', async (action) => {
    mocks.post.mockRejectedValue(new Error('offline')); mocks.clearNative.mockResolvedValue(undefined);
    const store = useAuthStore();
    await expect(store[action]()).rejects.toThrow('offline');
    expect(mocks.clearNative).toHaveBeenCalledOnce(); expect(mocks.clearTokens).toHaveBeenCalledOnce(); expect(store.user).toBeNull();
  });
  it('drops in-memory authorization even if the native vault cannot be cleared', async () => {
    mocks.post.mockResolvedValue({}); mocks.clearNative.mockRejectedValue(new Error('vault locked'));
    const store = useAuthStore();
    await expect(store.logout()).rejects.toThrow('vault locked'); expect(mocks.clearTokens).toHaveBeenCalledOnce();
  });
});

describe('offline login restoration', () => {
  it('does not revive a refresh cookie after an explicit offline logout', async () => {
    mocks.signedOut.mockResolvedValue(true); const store = useAuthStore(); await store.bootstrap();
    expect(mocks.post).not.toHaveBeenCalled(); expect(store.user).toBeNull();
  });
  it('can sign out locally without waiting for an unavailable server', async () => {
    connectionState.reachable = false; const store = useAuthStore(); await store.logout();
    expect(mocks.post).not.toHaveBeenCalled(); expect(mocks.forget).toHaveBeenCalledWith('server');
  });
  it('restores a bounded profile only for a network failure', async () => {
    mocks.get.mockRejectedValue(new Error('offline'));
    mocks.post.mockRejectedValue({ isAxiosError: true });
    mocks.identity.mockResolvedValue({ user: { id: 'one', displayName: '离线用户' } });
    const store = useAuthStore(); await store.bootstrap();
    expect(store.user?.id).toBe('one'); expect(mocks.restore).toHaveBeenCalledOnce();
  });
  it('does not restore private data when the server rejects the session', async () => {
    mocks.post.mockRejectedValue({ isAxiosError: true, response: { status: 401 } });
    const store = useAuthStore(); await store.bootstrap();
    expect(store.user).toBeNull(); expect(mocks.identity).not.toHaveBeenCalled(); expect(mocks.forget).toHaveBeenCalledWith('server');
  });
  it('logout removes the offline identity even when the network fails', async () => {
    mocks.post.mockRejectedValue(new Error('offline')); const store = useAuthStore();
    await expect(store.logout()).rejects.toThrow('offline'); expect(mocks.forget).toHaveBeenCalledWith('server');
  });
});
