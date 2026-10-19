import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { TokenResponse } from '@/types';
import {AxiosError} from 'axios';
import type { OfflineOperation } from '@/offlineQueue';
const platform = vi.hoisted(() => ({ desktop: true, server: 'https://one.example.cn' }));
const updatePolicy = vi.hoisted(() => ({ required: false }));
vi.mock('@/clientUpdates', () => ({ requiredClientUpdate: () => updatePolicy.required, bindClientUpdateOwner: vi.fn() }));
vi.mock('@/desktop', () => ({ isNativeClient: () => platform.desktop, desktopScope: () => platform.server, nativeInvoke: vi.fn() }));
vi.mock('@/offlineQueue', () => ({ enqueueOperation: vi.fn(), listOperations: vi.fn().mockResolvedValue([]) }));
vi.stubGlobal('localStorage', { removeItem: vi.fn() });
const { api, clearTokens, currentQueueOwner, replayOfflineOperation, saveTokens, restoreOfflineIdentity, nativeUpdateAuthorization } = await import('./client');
const session = { accessToken: 'test-only', user: { id: 'user-one' } } as TokenResponse;
beforeEach(() => { clearTokens(); platform.desktop = true; platform.server = 'https://one.example.cn'; updatePolicy.required = false; });

describe('offline identity partitioning', () => {
  it('refreshes native reminder state after an online task mutation but not a read', async () => {
    const previous=globalThis.window,dispatchEvent=vi.fn();vi.stubGlobal('window',{dispatchEvent});
    try{saveTokens(session);const adapter=vi.fn(async config=>({data:{},status:200,statusText:'OK',headers:{},config}));await api.post('/tasks/synthetic/complete',{}, {adapter});expect(dispatchEvent.mock.calls[0][0].type).toBe('plan-platform:reminder-refresh');dispatchEvent.mockClear();await api.get('/statistics/daily',{adapter});expect(dispatchEvent).not.toHaveBeenCalled();}finally{vi.stubGlobal('window',previous);}
  });
  it('an expired verification link does not refresh, log out or queue the active account', async () => {
    saveTokens(session);const owner=currentQueueOwner();
    const adapter=vi.fn(async config=>{throw new AxiosError('Expired verification','ERR_BAD_REQUEST',config,undefined,{status:401,statusText:'Unauthorized',data:{message:'验证链接已过期'},headers:{},config});});
    await expect(api.post('/auth/email-verification/confirm',{token:'synthetic-expired'},{adapter})).rejects.toThrow('Expired verification');
    expect(adapter).toHaveBeenCalledTimes(1);expect(currentQueueOwner()).toBe(owner);
    expect((await import('@/offlineQueue')).enqueueOperation).not.toHaveBeenCalled();
  });
  it('adds an opaque request correlation id without queueing feedback submissions', async () => {
    saveTokens(session);const original=api.defaults.adapter;
    api.defaults.adapter=async config=>{expect(config.headers['X-Request-ID']).toMatch(/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i);return {data:{ok:true},status:201,statusText:'Created',headers:{},config};};
    try{await api.post('/feedback',{title:'synthetic'});expect((await import('@/offlineQueue')).enqueueOperation).not.toHaveBeenCalled();}finally{api.defaults.adapter=original;}
  });
  it('requires a live access token; cached offline identity cannot authorize updates', async () => {
    await expect(nativeUpdateAuthorization()).rejects.toThrow('联网登录');
    restoreOfflineIdentity({ id: 'user-one' });
    await expect(nativeUpdateAuthorization()).rejects.toThrow('联网登录');
  });
  it('only returns current owner/server credentials after an authenticated identity check', async () => {
    saveTokens(session);
    const original=api.defaults.adapter;
    api.defaults.adapter=async config=>({data:session.user,status:200,statusText:'OK',headers:{},config});
    try { expect(await nativeUpdateAuthorization()).toEqual({accessToken:'test-only',ownerId:'user-one',serverUrl:platform.server}); }
    finally {api.defaults.adapter=original;}
  });
  it('rejects a login change while identity validation is pending', async () => {
    saveTokens(session);
    const original=api.defaults.adapter;
    api.defaults.adapter=async config=>{saveTokens({...session,user:{...session.user,id:'user-two'}});return {data:session.user,status:200,statusText:'OK',headers:{},config};};
    try { await expect(nativeUpdateAuthorization()).rejects.toThrow('账号或服务器已变化'); }
    finally {api.defaults.adapter=original;}
  });
  it('blocks required-version business writes and replay before sending or enqueueing', async () => {
    saveTokens(session); updatePolicy.required = true;
    const adapter = vi.fn();
    await expect(api.patch('/plans/test', { title: 'blocked' }, { adapter })).rejects.toThrow('请先更新');
    await expect(replayOfflineOperation({ ownerId: currentQueueOwner(), method: 'PATCH', url: '/plans/test', data: { title: 'blocked' } } as OfflineOperation)).rejects.toThrow('请先更新');
    expect(adapter).not.toHaveBeenCalled();
    const { enqueueOperation } = await import('@/offlineQueue');
    expect(enqueueOperation).not.toHaveBeenCalled();
  });
  it.each(['POST', 'PUT', 'PATCH', 'DELETE'])('required policy blocks %s without mutating the replay payload', async (method) => {
    saveTokens(session); updatePolicy.required = true;
    const adapter = vi.fn();
    await expect(api.request({ method, url: '/tasks/test', data: { title: 'retained', version: 2 }, adapter })).rejects.toThrow('请先更新');
    const row = { id: 'original-id', ownerId: currentQueueOwner(), method, url: '/tasks/test', data: { title: 'retained', version: 2 }, status: 'PENDING', attempts: 0 } as OfflineOperation;
    const original = structuredClone(row);
    await expect(replayOfflineOperation(row)).rejects.toThrow('请先更新');
    expect(row).toEqual(original); expect(adapter).not.toHaveBeenCalled();
  });
  it('required policy keeps read requests available for export and synchronization views', async () => {
    saveTokens(session); updatePolicy.required = true;
    const adapter = vi.fn(async config => ({ data: { available: true }, status: 200, statusText: 'OK', headers: {}, config }));
    expect((await api.get('/statistics/daily', { adapter })).data).toEqual({ available: true });
    expect(adapter).toHaveBeenCalledTimes(1);
  });
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
