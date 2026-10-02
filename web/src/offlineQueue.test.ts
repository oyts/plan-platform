import { IDBFactory } from 'fake-indexeddb';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { enqueueOperation, getOperation, listOperations, queueCount, updateOperation, deleteOperation, onQueueChange } from './offlineQueue';

const operation = (id: string, ownerId = 'server-one|owner') => ({ id, ownerId, method: 'POST', url: '/plans', data: { name: id } });
beforeEach(() => {
  vi.stubGlobal('indexedDB', new IDBFactory());
  vi.stubGlobal('window', new EventTarget());
});

describe('durable offline queue', () => {
  it('preserves enqueue order for concurrent writes with identical timestamps', async () => {
    vi.spyOn(Date.prototype, 'toISOString').mockReturnValue('2026-10-02T00:00:00.000Z');
    await Promise.all([enqueueOperation(operation('z-first')), enqueueOperation(operation('a-second')), enqueueOperation(operation('m-third'))]);
    expect((await listOperations('server-one|owner')).map(row => row.id)).toEqual(['z-first', 'a-second', 'm-third']);
    vi.restoreAllMocks();
  });
  it('isolates two servers and two accounts', async () => {
    await enqueueOperation(operation('one')); await enqueueOperation(operation('two', 'server-two|owner'));
    await enqueueOperation(operation('three', 'server-one|friend'));
    expect(await queueCount('server-one|owner')).toBe(1);
    expect((await listOperations('server-one|friend')).map(row => row.id)).toEqual(['three']);
  });
  it('retains order and attempt count when the same request is queued again', async () => {
    await enqueueOperation(operation('first')); await enqueueOperation(operation('second'));
    await updateOperation('first', { attempts: 2, status: 'FAILED', error: 'network' });
    const previous = await getOperation('first');
    await enqueueOperation(operation('first'));
    const current = await getOperation('first');
    expect(current).toMatchObject({ attempts: 2, status: 'PENDING', sequence: previous?.sequence, createdAt: previous?.createdAt });
    expect((await listOperations('server-one|owner')).map(row => row.id)).toEqual(['first', 'second']);
  });
  it('rejects operation-key reuse across owners without losing the original row', async () => {
    await enqueueOperation(operation('same-key'));
    await expect(enqueueOperation(operation('same-key', 'server-two|owner'))).rejects.toThrow('不同账号或服务器');
    expect((await getOperation('same-key'))?.ownerId).toBe('server-one|owner');
  });
  it('notifies subscribed windows and stops after unsubscribe', async () => {
    const changed = vi.fn(); const stop = onQueueChange(changed);
    await enqueueOperation(operation('event')); expect(changed).toHaveBeenCalledTimes(1);
    stop(); await deleteOperation('event'); expect(changed).toHaveBeenCalledTimes(1);
    expect(await queueCount('server-one|owner')).toBe(0);
  });
  it('reads v2 records before newly ordered writes without discarding stored requests', async () => {
    await new Promise<void>((resolve, reject) => {
      const request = indexedDB.open('plan-platform-offline', 2);
      request.onupgradeneeded = () => {
        const store = request.result.createObjectStore('operations', { keyPath: 'id' });
        store.createIndex('ownerId', 'ownerId');
        store.put({ ...operation('legacy'), createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z', attempts: 1, status: 'CONFLICT', error: '旧冲突' });
      };
      request.onsuccess = () => { request.result.close(); resolve(); };
      request.onerror = () => reject(request.error);
    });
    await enqueueOperation(operation('new'));
    expect((await listOperations('server-one|owner')).map(row => row.id)).toEqual(['legacy', 'new']);
    expect(await getOperation('legacy')).toMatchObject({ attempts: 1, status: 'CONFLICT', error: '旧冲突' });
  });
});
