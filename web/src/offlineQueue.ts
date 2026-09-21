export type OfflineOperationStatus = 'PENDING' | 'CONFLICT' | 'FAILED';

export interface OfflineOperation {
  id: string;
  ownerId: string;
  method: string;
  url: string;
  params?: unknown;
  data?: unknown;
  createdAt: string;
  updatedAt: string;
  attempts: number;
  status: OfflineOperationStatus;
  error?: string;
}

const DATABASE = 'plan-platform-offline';
const STORE = 'operations';
const OWNER_INDEX = 'ownerId';
const CHANGE_EVENT = 'plan-platform:queue-change';

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 2);
    request.onupgradeneeded = () => {
      const database = request.result;
      const store = database.objectStoreNames.contains(STORE)
        ? request.transaction!.objectStore(STORE)
        : database.createObjectStore(STORE, { keyPath: 'id' });
      if (!store.indexNames.contains(OWNER_INDEX)) store.createIndex(OWNER_INDEX, OWNER_INDEX, { unique: false });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('无法打开离线操作队列。'));
  });
}

async function transaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const database = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = database.transaction(STORE, mode);
    const request = action(tx.objectStore(STORE));
    let result: T;
    request.onsuccess = () => { result = request.result; };
    request.onerror = () => reject(request.error ?? new Error('离线操作队列读写失败。'));
    tx.oncomplete = () => { database.close(); resolve(result); };
    tx.onerror = () => { database.close(); reject(tx.error ?? new Error('离线操作队列事务失败。')); };
  });
}

function announceChange(): void {
  window.dispatchEvent(new CustomEvent(CHANGE_EVENT));
}

export function onQueueChange(listener: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, listener);
  return () => window.removeEventListener(CHANGE_EVENT, listener);
}

export async function enqueueOperation(operation: Pick<OfflineOperation, 'id' | 'ownerId' | 'method' | 'url' | 'params' | 'data'>): Promise<void> {
  const existing = await getOperation(operation.id);
  const now = new Date().toISOString();
  await transaction('readwrite', (store) => store.put({
    ...operation,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
    attempts: existing?.attempts ?? 0,
    status: 'PENDING',
    error: undefined,
  } satisfies OfflineOperation));
  announceChange();
}

export async function getOperation(id: string): Promise<OfflineOperation | undefined> {
  return transaction<OfflineOperation | undefined>('readonly', (store) => store.get(id));
}

export async function listOperations(ownerId: string): Promise<OfflineOperation[]> {
  const rows = await transaction<OfflineOperation[]>('readonly', (store) => store.index(OWNER_INDEX).getAll(ownerId));
  return rows.sort((left, right) => left.createdAt.localeCompare(right.createdAt));
}

export async function updateOperation(id: string, patch: Partial<Pick<OfflineOperation, 'attempts' | 'status' | 'error'>>): Promise<void> {
  const current = await getOperation(id);
  if (!current) return;
  await transaction('readwrite', (store) => store.put({ ...current, ...patch, updatedAt: new Date().toISOString() }));
  announceChange();
}

export async function deleteOperation(id: string): Promise<void> {
  await transaction('readwrite', (store) => store.delete(id));
  announceChange();
}

export async function queueCount(ownerId: string): Promise<number> {
  return transaction<number>('readonly', (store) => store.index(OWNER_INDEX).count(ownerId));
}
