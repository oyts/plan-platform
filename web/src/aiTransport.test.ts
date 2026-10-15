import { beforeEach, expect, it, vi } from 'vitest';
import type { TokenResponse } from '@/types';
vi.mock('@/clientUpdates', () => ({ requiredClientUpdate: () => false, bindClientUpdateOwner: vi.fn() }));
vi.mock('@/desktop', () => ({ isNativeClient: () => false, desktopScope: () => 'fixture', nativeInvoke: vi.fn() }));
vi.mock('@/offlineQueue', () => ({ enqueueOperation: vi.fn(), listOperations: vi.fn().mockResolvedValue([]) }));
vi.stubGlobal('localStorage', { removeItem: vi.fn() });
const { api, saveTokens, clearTokens } = await import('@/api/client');
const { cacheable } = await import('@/offlineCache');
const { connectionState } = await import('@/connectionState');
const { enqueueOperation } = await import('@/offlineQueue');
beforeEach(() => { clearTokens(); vi.clearAllMocks(); saveTokens({ accessToken: 'fixture-only', user: { id: 'ai-fixture' } } as TokenResponse); connectionState.reachable = false; });
it('AI messages and administrator changes are neither cached nor queued while offline', async () => {
  const adapter = vi.fn();
  for (const url of ['/ai/quota', '/ai/capabilities', '/admin/ai/users', '/admin/ai/audit']) {
    expect(cacheable(url)).toBe(false); await expect(api.get(url, { adapter })).rejects.toThrow('连接服务器');
  }
  await expect(api.post('/ai/jobs', { message: 'fixture' }, { adapter })).rejects.toThrow('联网确认');
  await expect(api.put('/admin/ai/settings', { dailyLimit: 3, version: 0 }, { adapter })).rejects.toThrow('联网确认');
  expect(adapter).not.toHaveBeenCalled(); expect(enqueueOperation).not.toHaveBeenCalled();
});
