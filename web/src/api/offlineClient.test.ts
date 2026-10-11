import { IDBFactory, IDBKeyRange } from 'fake-indexeddb';
import { AxiosError, type AxiosAdapter } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/desktop', () => ({ isNativeClient: () => false, desktopScope: () => '', nativeInvoke: vi.fn() }));
vi.stubGlobal('localStorage', { removeItem: vi.fn() });
const { api, saveTokens, clearTokens, currentQueueOwner, serverScope, OfflineQueuedError } = await import('./client');
const { rememberIdentity, readResponse } = await import('@/offlineCache');
const { connectionState } = await import('@/connectionState');
const { listOperations } = await import('@/offlineQueue');
const user = { id: 'one', email: 'one@example.test', displayName: '测试', role: 'USER', emailVerified: false };
const successful: AxiosAdapter = async config => ({ data: [{ id: 't', title: '原任务', plannedMinutes: 1, progress: { version: 1 }, version: 0 }], status: 200, statusText: 'OK', headers: {}, config });
const disconnected: AxiosAdapter = async config => { throw new AxiosError('network lost', 'ERR_NETWORK', config); };
beforeEach(async () => {
  vi.stubGlobal('indexedDB', new IDBFactory()); vi.stubGlobal('IDBKeyRange', IDBKeyRange); vi.stubGlobal('window', new EventTarget()); vi.stubGlobal('navigator', { onLine: true });
  clearTokens(); saveTokens({ accessToken: 'test-only', expiresInSeconds: 60, user });
  await rememberIdentity(serverScope(), currentQueueOwner()!, user);
  connectionState.cacheAvailable = true;
});
describe('offline API safety', () => {
  it('queues standalone todo completion and deletion with a retained version', async () => {
    const params = {from:'2026-10-12',to:'2026-10-12',deleted:false};
    const todoCache:AxiosAdapter = async config => ({data:[{id:'todo',title:'临时事项',dueDate:'2026-10-12',done:false,deleted:false,version:3}],status:200,statusText:'OK',headers:{},config});
    await api.get('/todos',{params,adapter:todoCache});
    await expect(api.put('/todos/todo',{title:'完成事项',dueDate:'2026-10-12',done:true,deleted:false,version:3},{adapter:disconnected})).rejects.toBeInstanceOf(OfflineQueuedError);
    expect((await api.get('/todos',{params,adapter:disconnected})).data).toMatchObject([{title:'完成事项',done:true,version:3,localState:'PENDING'}]);
    await expect(api.put('/todos/todo',{title:'删除事项',version:3,deleted:true})).rejects.toThrow('已有本地修改');
    expect(await listOperations('one')).toHaveLength(1);
  });
  it('queues route content edits but refuses topology changes during an outage', async () => {
    const routeCache: AxiosAdapter = async config => ({ data: [{ id:'l',name:'路线',completed:0,items:[{id:'i',title:'原步骤',plannedMinutes:20,unlocked:true,version:0}] }], status:200,statusText:'OK',headers:{},config });
    await api.get('/mainlines', {adapter:routeCache});
    await expect(api.put('/mainlines/items/i',{title:'离线步骤',version:0},{adapter:disconnected})).rejects.toBeInstanceOf(OfflineQueuedError);
    expect((await api.get('/mainlines',{adapter:disconnected})).data[0].items[0]).toMatchObject({title:'离线步骤',localState:'PENDING'});
    connectionState.reachable=true;
    await expect(api.put('/mainlines/items/other',{title:'调整',routing:{dependencyMode:'NONE'}},{adapter:disconnected})).rejects.not.toBeInstanceOf(OfflineQueuedError);
    connectionState.reachable=true;
    await expect(api.post('/mainlines/l/stages',{name:'新阶段'},{adapter:disconnected})).rejects.not.toBeInstanceOf(OfflineQueuedError);
    expect(await listOperations('one')).toHaveLength(1);
  });
  it('falls back only after a network failure and projects a queued edit immediately', async () => {
    await api.get('/plans/p/tasks', { adapter: successful });
    await expect(api.put('/tasks/t', { title: '本地任务', version: 0 }, { adapter: disconnected })).rejects.toBeInstanceOf(OfflineQueuedError);
    expect((await api.get('/plans/p/tasks', { adapter: disconnected })).data[0]).toMatchObject({ title: '本地任务', localState: 'PENDING', version: 0 });
    expect((await listOperations('one')).length).toBe(1);
    expect((await readResponse(serverScope(), 'one', '/plans/p/tasks', undefined))?.data).toMatchObject([{ title: '原任务' }]);
  });
  it.each([403, 404])('does not serve a private cache for HTTP %s', async status => {
    await api.get('/plans/p/tasks', { adapter: successful });
    const rejected: AxiosAdapter = async config => { throw new AxiosError('denied', 'ERR_BAD_RESPONSE', config, undefined, { status, statusText: 'denied', headers: {}, data: {}, config }); };
    await expect(api.get('/plans/p/tasks', { adapter: rejected })).rejects.toMatchObject({ response: { status } });
  });
  it('does not fabricate an empty list for an unvisited range', async () => {
    await expect(api.get('/plans/p/tasks/page', { params: { from: '2026-11-01' }, adapter: disconnected })).rejects.toThrow('尚未缓存');
  });
  it('blocks timing and deletion rather than queuing them', async () => {
    connectionState.reachable = false;
    await expect(api.post('/tasks/t/timer/start')).rejects.toThrow('需要联网');
    await expect(api.delete('/tasks/t')).rejects.toThrow('需要联网');
    expect(await listOperations('one')).toEqual([]);
  });
  it('rejects late responses after logout', async () => {
    const delayed: AxiosAdapter = async config => { clearTokens(); return { data: ['private'], status: 200, statusText: 'OK', headers: {}, config }; };
    await expect(api.get('/plans', { adapter: delayed })).rejects.toThrow('账号或服务器已变化');
  });
});
