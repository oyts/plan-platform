import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
const mocks = vi.hoisted(() => ({ post: vi.fn(), clearTokens: vi.fn(), clearNative: vi.fn() }));
vi.mock('@/api/client', () => ({ api: { post: mocks.post }, clearTokens: mocks.clearTokens, saveTokens: vi.fn() }));
vi.mock('@/desktop', () => ({ clearNativeSession: mocks.clearNative }));
import { useAuthStore } from './auth';
beforeEach(() => { setActivePinia(createPinia()); mocks.post.mockReset(); mocks.clearTokens.mockReset(); mocks.clearNative.mockReset(); });
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
