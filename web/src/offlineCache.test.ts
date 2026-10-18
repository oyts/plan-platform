import { IDBFactory, IDBKeyRange } from 'fake-indexeddb';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { cacheable, cacheResponse, cachedIdentity, forgetScope, isSignedOut, signOutScope, readResponse, rememberIdentity, responseKey, PROFILE_TTL,cacheInventory,cachedRecordBase } from './offlineCache';
import type { User } from './types';
const user = { id: 'one', email: 'one@example.test', displayName: '缓存测试', role: 'USER', emailVerified: false } as User;
beforeEach(() => { vi.stubGlobal('indexedDB', new IDBFactory()); vi.stubGlobal('IDBKeyRange', IDBKeyRange); });
describe('isolated offline cache', () => {
  it('reports only this owner/server metadata and captures only a matching-version common base',async()=>{
    await rememberIdentity('s','one',user);await rememberIdentity('other','one',user);await cacheResponse('s','one','/plans/p/tasks',undefined,[{id:'task',title:'私有标题',planId:'p',version:3,progress:{version:9}}]);await cacheResponse('other','one','/plans',undefined,['另一服务器']);
    const inventory=await cacheInventory('s','one');expect(inventory).toHaveLength(1);expect(JSON.stringify(inventory)).not.toContain('私有标题');expect(await cacheInventory('s','other-owner')).toEqual([]);expect(await cachedRecordBase('s','one','/tasks/task',3)).toMatchObject({version:3,fields:{title:'私有标题'}});expect(await cachedRecordBase('s','one','/tasks/task',2)).toBeUndefined();expect(await cachedRecordBase('other','one','/tasks/task',3)).toBeUndefined();
  });
  it('does not cache an oversized response or claim it is available',async()=>{await rememberIdentity('s','one',user);await cacheResponse('s','one','/plans',undefined,['中'.repeat(400000)]);expect(await readResponse('s','one','/plans',undefined)).toBeNull();expect(await cacheInventory('s','one')).toEqual([]);});
  it('excludes account administration, exports and unknown API routes', () => {
    for (const url of ['/auth/me', '/admin/invitations', '/plans/p/tasks/export', '/plans/p/reflections/page', '/anything']) expect(cacheable(url)).toBe(false);
    expect(cacheable('/plans/p/tasks/page')).toBe(true);
  });
  it('remembers offline logout until a deliberate login succeeds', async () => {
    await rememberIdentity('s', 'one', user); await signOutScope('s');
    expect(await cachedIdentity('s')).toBeNull(); expect(await isSignedOut('s')).toBe(true);
    await rememberIdentity('s', 'one', user); expect(await isSignedOut('s')).toBe(false);
  });
  it('allows dated experience snapshots but not arbitrary private weekly URLs', () => {
    for (const path of ['/daily/summary','/daily/items','/daily/routes','/daily/reminders','/weekly/summary','/weekly/reviews','/weekly/reviews/2026-10-12']) expect(cacheable(path)).toBe(true);
    expect(cacheable('/weekly/reviews/export')).toBe(false);
    expect(responseKey('s','one','/weekly/summary',{start:'2026-10-12'})).not.toBe(responseKey('s','one','/weekly/summary',{start:'2026-10-19'}));
  });
  it('partitions by server, account and stable query parameters', async () => {
    await rememberIdentity('s1', 'one', user); await rememberIdentity('s2', 'one', user);
    await cacheResponse('s1', 'one', '/plans', { size: 5, page: 1 }, ['private']);
    expect((await readResponse('s1', 'one', '/plans', { page: 1, size: 5 }))?.data).toEqual(['private']);
    expect(await readResponse('s2', 'one', '/plans', { page: 1, size: 5 })).toBeNull();
    expect(await readResponse('s1', 'two', '/plans', { page: 1, size: 5 })).toBeNull();
    expect(await readResponse('s1', 'one', '/plans', { page: 2, size: 5 })).toBeNull();
  });
  it('expires offline identity after 24 hours without extending it on reads', async () => {
    const now = Date.now(); await rememberIdentity('s', 'one', user);
    const before = await cachedIdentity('s');
    expect(before?.expiresAt).toBeGreaterThanOrEqual(now + PROFILE_TTL);
    vi.spyOn(Date, 'now').mockReturnValue(now + PROFILE_TTL + 100);
    expect(await cachedIdentity('s')).toBeNull();
  });
  it('clears private pages on logout and rejects late response caching', async () => {
    await rememberIdentity('s', 'one', user); await cacheResponse('s', 'one', '/plans', null, ['private']);
    await forgetScope('s'); await cacheResponse('s', 'one', '/plans', null, ['late']);
    expect(await cachedIdentity('s')).toBeNull(); expect(await readResponse('s', 'one', '/plans', null)).toBeNull();
  });
  it('rejects late responses from a previous account', async () => {
    await rememberIdentity('s', 'two', { ...user, id: 'two' });
    await cacheResponse('s', 'one', '/plans', null, ['old user']);
    expect(await readResponse('s', 'one', '/plans', null)).toBeNull();
  });
  it('does not put yesterday under the same dashboard key', () => {
    vi.spyOn(Date.prototype, 'toLocaleDateString').mockReturnValue('2026-10-02'); const yesterday = responseKey('s', 'one', '/dashboard/today', null);
    vi.spyOn(Date.prototype, 'toLocaleDateString').mockReturnValue('2026-10-03');
    expect(responseKey('s', 'one', '/dashboard/today', null)).not.toBe(yesterday);
  });
  it('bounds stored responses to 100 queries', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(1800000000000);
    await rememberIdentity('s', 'one', user);
    for (let page = 0; page < 105; page++) { vi.spyOn(Date, 'now').mockReturnValue(1800000000000 + page); await cacheResponse('s', 'one', '/tasks', { page }, []); }
    // Use the same clock for validation, not an unrelated old identity expiry.
    const db = await new Promise<IDBDatabase>(resolve => { const request = indexedDB.open('plan-platform-cache'); request.onsuccess = () => resolve(request.result); });
    const count = await new Promise<number>(resolve => { const request = db.transaction('responses').objectStore('responses').count(); request.onsuccess = () => resolve(request.result); });
    db.close(); expect(count).toBe(100);
  });
});
