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
  sequence?: number;
}

const DATABASE = 'plan-platform-offline';
const STORE = 'operations';
const OWNER_INDEX = 'ownerId';
const METADATA = 'metadata';
const CHANGE_EVENT = 'plan-platform:queue-change';

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 3);
    request.onupgradeneeded = () => {
      const database = request.result;
      const store = database.objectStoreNames.contains(STORE)
        ? request.transaction!.objectStore(STORE)
        : database.createObjectStore(STORE, { keyPath: 'id' });
      if (!store.indexNames.contains(OWNER_INDEX)) store.createIndex(OWNER_INDEX, OWNER_INDEX, { unique: false });
      if (!database.objectStoreNames.contains(METADATA)) database.createObjectStore(METADATA, { keyPath: 'key' });
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
    tx.onabort = () => { database.close(); reject(tx.error ?? new Error('离线操作队列事务已取消。')); };
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
  const database = await openDatabase();
  await new Promise<void>((resolve, reject) => {
    // One transaction assigns the durable order even when tabs enqueue in the same millisecond.
    const tx = database.transaction([STORE, METADATA], 'readwrite');
    const store = tx.objectStore(STORE);
    const metadata = tx.objectStore(METADATA);
    let failure: Error | undefined;
    const existingRequest = store.get(operation.id);
    existingRequest.onsuccess = () => {
      const existing = existingRequest.result as OfflineOperation | undefined;
      if (existing && existing.ownerId !== operation.ownerId) {
        failure = new Error('同一离线操作键不能用于不同账号或服务器。');
        tx.abort(); return;
      }
      const counterRequest = metadata.get('sequence');
      counterRequest.onsuccess = () => {
        const next = (counterRequest.result?.value ?? 0) + 1;
        const sequence = existing?.sequence ?? next;
        if (!existing?.sequence) metadata.put({ key: 'sequence', value: next });
        const now = new Date().toISOString();
        store.put({ ...operation, createdAt: existing?.createdAt ?? now, updatedAt: now,
          attempts: existing?.attempts ?? 0, status: 'PENDING', error: undefined, sequence } satisfies OfflineOperation);
      };
    };
    tx.oncomplete = () => { database.close(); resolve(); };
    tx.onerror = tx.onabort = () => { database.close(); reject(failure ?? tx.error ?? new Error('离线操作队列事务失败。')); };
  });
  announceChange();
}

export async function getOperation(id: string): Promise<OfflineOperation | undefined> {
  return transaction<OfflineOperation | undefined>('readonly', (store) => store.get(id));
}

export async function listOperations(ownerId: string): Promise<OfflineOperation[]> {
  const rows = await transaction<OfflineOperation[]>('readonly', (store) => store.index(OWNER_INDEX).getAll(ownerId));
  return rows.sort((left, right) => {
    if (left.sequence !== undefined && right.sequence !== undefined) return left.sequence - right.sequence;
    // Existing v2 rows predate ordered v3 writes; preserve their chronological order first.
    if (left.sequence !== undefined) return 1;
    if (right.sequence !== undefined) return -1;
    return left.createdAt.localeCompare(right.createdAt) || left.id.localeCompare(right.id);
  });
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
