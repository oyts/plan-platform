import { IDBFactory } from 'fake-indexeddb';
import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
const client = vi.hoisted(() => ({ owner: 'server|owner', replay: vi.fn() }));
vi.mock('@/api/client', () => ({ currentQueueOwner: () => client.owner, replayOfflineOperation: client.replay, apiMessage: (error: Error) => error.message }));
import { enqueueOperation, getOperation, listOperations, updateOperation } from './offlineQueue';
import { synchronizeQueue } from './queueSync';
beforeEach(async () => {
  vi.stubGlobal('indexedDB', new IDBFactory()); vi.stubGlobal('window', new EventTarget());
  vi.stubGlobal('navigator', { onLine: true }); client.owner = 'server|owner'; client.replay.mockReset().mockResolvedValue(undefined);
  for (const id of ['first', 'second']) await enqueueOperation({ id, ownerId: client.owner, method: 'PUT', url: `/tasks/${id}`, data: { title: id, version: 0 } });
});
const conflict = () => new AxiosError('版本冲突', 'ERR_BAD_REQUEST', undefined, undefined,
  { status: 409, statusText: 'Conflict', headers: {}, config: { headers: new AxiosHeaders() }, data: {} });

describe('ordered synchronization', () => {
  it('replays in durable order and removes only completed requests', async () => {
    expect(await synchronizeQueue(client.owner)).toEqual({ synced: 2 });
    expect(client.replay.mock.calls.map(([row]) => row.id)).toEqual(['first', 'second']);
    expect(await listOperations(client.owner)).toEqual([]);
  });
  it('stops on a network failure even when navigator still reports online', async () => {
    client.replay.mockRejectedValueOnce(new Error('连接中断'));
    expect(await synchronizeQueue(client.owner)).toMatchObject({ synced: 0, stopped: 'FAILED' });
    expect(client.replay).toHaveBeenCalledTimes(1);
    expect(await getOperation('first')).toMatchObject({ status: 'FAILED', attempts: 1 });
    expect(await getOperation('second')).toMatchObject({ status: 'PENDING', attempts: 0 });
  });
  it('parks 409 and never skips an existing conflict on later attempts', async () => {
    client.replay.mockRejectedValueOnce(conflict());
    expect(await synchronizeQueue(client.owner)).toMatchObject({ stopped: 'CONFLICT' });
    expect(await getOperation('first')).toMatchObject({ status: 'CONFLICT' });
    expect(await synchronizeQueue(client.owner)).toMatchObject({ stopped: 'CONFLICT', synced: 0 });
    expect(client.replay).toHaveBeenCalledTimes(1);
  });
  it('can retry a failed head with its original key before proceeding', async () => {
    await updateOperation('first', { status: 'FAILED', attempts: 1 });
    expect(await synchronizeQueue(client.owner, 1)).toEqual({ synced: 1 });
    expect(client.replay.mock.calls[0][0]).toMatchObject({ id: 'first' });
    expect((await listOperations(client.owner)).map(row => row.id)).toEqual(['second']);
  });
  it('does not send requests while offline or after a user switch', async () => {
    vi.stubGlobal('navigator', { onLine: false });
    expect(await synchronizeQueue(client.owner)).toMatchObject({ stopped: 'OFFLINE' });
    vi.stubGlobal('navigator', { onLine: true }); client.owner = 'other-account';
    expect(await synchronizeQueue('server|owner')).toMatchObject({ stopped: 'IDENTITY' });
    expect(client.replay).not.toHaveBeenCalled();
  });
  it('stops before the next request if the account changes during a batch', async () => {
    client.replay.mockImplementationOnce(async () => { client.owner = 'other-account'; });
    expect(await synchronizeQueue('server|owner')).toMatchObject({ synced: 1, stopped: 'IDENTITY' });
    expect(client.replay).toHaveBeenCalledTimes(1);
    expect(await getOperation('second')).toBeDefined();
  });
  it('blocks overlapping batches in one window', async () => {
    let release!: () => void;
    client.replay.mockImplementationOnce(() => new Promise<void>(resolve => { release = resolve; }));
    const running = synchronizeQueue(client.owner);
    await vi.waitFor(() => expect(client.replay).toHaveBeenCalledTimes(1));
    expect(await synchronizeQueue(client.owner)).toMatchObject({ stopped: 'BUSY', synced: 0 });
    release(); expect(await running).toEqual({ synced: 2 });
  });
  it('honors a lock held by another browser window', async () => {
    const lock = vi.fn(async (_name, _options, callback) => callback(null));
    vi.stubGlobal('navigator', { onLine: true, locks: { request: lock } });
    expect(await synchronizeQueue(client.owner)).toMatchObject({ stopped: 'BUSY' });
    expect(client.replay).not.toHaveBeenCalled();
  });
});
