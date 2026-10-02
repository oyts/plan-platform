import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { TokenResponse } from '@/types';
import type { OfflineOperation } from '@/offlineQueue';
const platform = vi.hoisted(() => ({ desktop: true, server: 'https://one.example.cn' }));
vi.mock('@/desktop', () => ({ isNativeClient: () => platform.desktop, desktopScope: () => platform.server, nativeInvoke: vi.fn() }));
vi.mock('@/offlineQueue', () => ({ enqueueOperation: vi.fn(), listOperations: vi.fn().mockResolvedValue([]) }));
vi.stubGlobal('localStorage', { removeItem: vi.fn() });
const { clearTokens, currentQueueOwner, replayOfflineOperation, saveTokens } = await import('./client');
const session = { accessToken: 'test-only', user: { id: 'user-one' } } as TokenResponse;
beforeEach(() => { clearTokens(); platform.desktop = true; platform.server = 'https://one.example.cn'; });

describe('offline identity partitioning', () => {
  it('partitions desktop operations by server and account', () => {
    saveTokens(session);
    expect(currentQueueOwner()).toBe('https://one.example.cn|user-one');
    platform.server = 'https://two.example.cn';
    expect(currentQueueOwner()).toBeNull();
    clearTokens(); expect(currentQueueOwner()).toBeNull();
  });
  it('does not change browser queue ownership format', () => {
    platform.desktop = false; saveTokens(session); expect(currentQueueOwner()).toBe('user-one');
  });
  it('rejects another server or account before transmitting anything', async () => {
    saveTokens(session);
    for (const ownerId of ['https://two.example.cn|user-one', 'https://one.example.cn|user-two', 'user-one']) {
      await expect(replayOfflineOperation({ ownerId } as OfflineOperation)).rejects.toThrow('不属于当前服务器和账号');
    }
  });
});
