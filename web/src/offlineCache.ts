import type { User } from '@/types';
import { connectionState } from '@/connectionState';

const DB = 'plan-platform-cache';
export const PROFILE_TTL = 24 * 60 * 60 * 1000;
const RESPONSE_TTL = 7 * PROFILE_TTL;
const MAX_RESPONSES = 100;
const MAX_RESPONSE_BYTES=1024*1024,MAX_CACHE_BYTES=10*1024*1024;
export interface CachedIdentity { scope: string; owner: string; user: User; expiresAt: number; signedOut?: boolean }
interface CachedResponse { key: string; scope: string; owner: string; savedAt: number; data: unknown }

async function database(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, 1);
    request.onupgradeneeded = () => {
      request.result.createObjectStore('identities', { keyPath: 'scope' });
      const responses = request.result.createObjectStore('responses', { keyPath: 'key' });
      responses.createIndex('scope', 'scope');
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
async function transaction<T>(store: string, mode: IDBTransactionMode, run: (store: IDBObjectStore, result: (value: T) => void) => void): Promise<T> {
  const db = await database();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, mode); let value: T;
    tx.oncomplete = () => { db.close(); resolve(value); };
    tx.onerror = tx.onabort = () => { db.close(); reject(tx.error ?? new Error('本地缓存不可用')); };
    run(tx.objectStore(store), (data) => { value = data; });
  });
}
export async function rememberIdentity(scope: string, owner: string, user: User): Promise<void> {
  try {
    // Store profile only. Access/refresh tokens and passwords never enter this database.
    await transaction<void>('identities', 'readwrite', (store) => { store.put({ scope, owner, user: structuredClone(user), expiresAt: Date.now() + PROFILE_TTL }); });
  } catch { connectionState.cacheAvailable = false; }
}
export async function cachedIdentity(scope: string): Promise<CachedIdentity | null> {
  try {
    const identity = await transaction<CachedIdentity | undefined>('identities', 'readonly', (store, done) => { store.get(scope).onsuccess = (event) => done((event.target as IDBRequest).result); });
    if (!identity || identity.signedOut) return null;
    if (identity.expiresAt <= Date.now()) { await forgetScope(scope); return null; }
    return identity;
  } catch { connectionState.cacheAvailable = false; return null; }
}
export async function isSignedOut(scope: string): Promise<boolean> {
  try {
    return await transaction<boolean>('identities', 'readonly', (store, done) => { store.get(scope).onsuccess = (event) => done(Boolean((event.target as IDBRequest).result?.signedOut)); });
  } catch { return false; }
}
export async function signOutScope(scope: string): Promise<void> {
  // A HttpOnly refresh cookie cannot be deleted while offline. Persist the user's
  // logout intent so a later online restart cannot silently revive that cookie.
  const db = await database();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(['identities', 'responses'], 'readwrite');
    tx.oncomplete = () => { db.close(); resolve(); };
    tx.onerror = tx.onabort = () => { db.close(); reject(tx.error); };
    tx.objectStore('identities').put({ scope, signedOut: true });
    tx.objectStore('responses').index('scope').openCursor(IDBKeyRange.only(scope)).onsuccess = (event) => {
      const cursor = (event.target as IDBRequest<IDBCursorWithValue | null>).result;
      if (cursor) { cursor.delete(); cursor.continue(); }
    };
  });
}
export async function forgetScope(scope: string): Promise<void> {
  // Deliberately leave the separate offline-operation queue intact.
  await transaction<void>('identities', 'readwrite', (store) => { store.delete(scope); });
  await clearResponses(scope);
}
export async function clearResponses(scope: string): Promise<void> {
  await transaction<void>('responses', 'readwrite', (store) => {
    store.index('scope').openCursor(IDBKeyRange.only(scope)).onsuccess = (event) => {
      const cursor = (event.target as IDBRequest<IDBCursorWithValue | null>).result;
      if (cursor) { cursor.delete(); cursor.continue(); }
    };
  });
}
export function cacheable(url: string): boolean {
  return ['/plans', '/dashboard/today', '/tasks/carryover', '/templates', '/mainlines', '/todos', '/daily/summary', '/daily/items', '/daily/routes', '/daily/reminders', '/weekly/summary', '/weekly/reviews'].includes(url)
    || /^\/weekly\/reviews\/\d{4}-\d{2}-\d{2}$/.test(url)
    || /^\/plans\/[^/]+\/(tags|tasks(?:\/page)?|milestones|stats(?:\/daily)?|recurrences)$/.test(url);
}
export function responseKey(scope: string, owner: string, url: string, params: unknown): string {
  const query = params && typeof params === 'object' ? Object.entries(params).sort(([a], [b]) => a.localeCompare(b)) : params;
  // A cached yesterday must not be presented as today's dashboard.
  const day = url === '/dashboard/today' ? new Date().toLocaleDateString('sv-SE') : '';
  return JSON.stringify([scope, owner, url, query ?? null, day]);
}
export async function cacheResponse(scope: string, owner: string, url: string, params: unknown, data: unknown): Promise<void> {
  try {
    if(new TextEncoder().encode(JSON.stringify(data)).byteLength>MAX_RESPONSE_BYTES)return;
    const db = await database();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(['identities', 'responses'], 'readwrite');
      tx.oncomplete = () => { db.close(); resolve(); };
      tx.onerror = tx.onabort = () => { db.close(); reject(tx.error); };
      const store = tx.objectStore('responses');
      tx.objectStore('identities').get(scope).onsuccess = (event) => {
        const identity = (event.target as IDBRequest).result as CachedIdentity | undefined;
        // A late response must not recreate data after logout or an account switch.
        if (!identity || identity.owner !== owner || identity.expiresAt <= Date.now()) return;
        const savedAt = Date.now();
        store.put({ key: responseKey(scope, owner, url, params), scope, owner, savedAt, data: structuredClone(data) });
        store.getAll().onsuccess = (event) => {
          const rows = ((event.target as IDBRequest).result as CachedResponse[]).sort((a, b) => b.savedAt - a.savedAt);
          let bytes=0,count=0;rows.forEach(row=>{const size=new TextEncoder().encode(JSON.stringify(row.data)).byteLength;if(count>=MAX_RESPONSES||size>MAX_RESPONSE_BYTES||bytes+size>MAX_CACHE_BYTES||savedAt-row.savedAt>RESPONSE_TTL)store.delete(row.key);else{bytes+=size;count++;}});
        };
      };
    });
  } catch { connectionState.cacheAvailable = false; }
}
export async function readResponse(scope: string, owner: string, url: string, params: unknown): Promise<CachedResponse | null> {
  try {
    const row = await transaction<CachedResponse | undefined>('responses', 'readonly', (store, done) => {
      store.get(responseKey(scope, owner, url, params)).onsuccess = (event) => done((event.target as IDBRequest).result);
    });
    return row && Date.now() - row.savedAt <= RESPONSE_TTL ? row : null;
  } catch { connectionState.cacheAvailable = false; return null; }
}

export interface CacheEntry{key:string;url:string;params:Record<string,unknown>|null;savedAt:number;expiresAt:number;bytes:number}
export async function cacheInventory(scope:string,owner:string):Promise<CacheEntry[]>{
  const rows=await transaction<CachedResponse[]>('responses','readonly',(store,done)=>{store.index('scope').getAll(scope).onsuccess=event=>done((event.target as IDBRequest).result);});
  return rows.filter(row=>row.owner===owner&&Date.now()-row.savedAt<=RESPONSE_TTL).map(row=>{const parts=JSON.parse(row.key);return{key:row.key,url:parts[2],params:Array.isArray(parts[3])?Object.fromEntries(parts[3]):null,savedAt:row.savedAt,expiresAt:row.savedAt+RESPONSE_TTL,bytes:new TextEncoder().encode(JSON.stringify(row.data)).byteLength};});
}
export async function cachedRecordBase(scope:string,owner:string,url:string,version:unknown):Promise<import('./syncComparison').EditableSnapshot|undefined>{
  const {recordKind,editableRecord}=await import('./syncComparison');const kind=recordKind(url);if(!kind||!Number.isSafeInteger(version))return;
  const id=url.slice(url.lastIndexOf('/')+1);const rows=await transaction<CachedResponse[]>('responses','readonly',(store,done)=>{store.index('scope').getAll(scope).onsuccess=event=>done((event.target as IDBRequest).result);});
  for(const row of rows.filter(row=>row.owner===owner&&Date.now()-row.savedAt<=RESPONSE_TTL).sort((a,b)=>b.savedAt-a.savedAt)){
    const visit=(value:unknown):Record<string,unknown>|undefined=>{if(!value||typeof value!=='object')return;if(Array.isArray(value)){for(const item of value){const match=visit(item);if(match)return match;}return;}
      const object=value as Record<string,unknown>;if(object.id===id&&object.version===version){const fields=editableRecord(kind,object);if(fields)return fields;}for(const child of Object.values(object)){const match=visit(child);if(match)return match;}
    };
    const fields=visit(row.data);if(fields)return{version:version as number,fields};
  }
}
