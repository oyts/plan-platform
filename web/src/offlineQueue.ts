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
  base?: import('./syncComparison').EditableSnapshot;
  resolution?: { originalId:string; originalData:unknown; originalBase?:import('./syncComparison').EditableSnapshot; resolvedAt:string; history?:{id:string;data:unknown;base?:import('./syncComparison').EditableSnapshot}[] };
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

export async function enqueueOperation(operation: Pick<OfflineOperation, 'id' | 'ownerId' | 'method' | 'url' | 'params' | 'data' | 'base'>): Promise<void> {
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
        store.put({ ...operation, base:existing?.base??operation.base, createdAt: existing?.createdAt ?? now, updatedAt: now,
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

/** User-confirmed replacement of exactly one parked conflict; retain its original content and FIFO position. */
export async function replaceConflict(original:OfflineOperation,data:Record<string,unknown>,base:import('./syncComparison').EditableSnapshot):Promise<string>{
  if(original.status!=='CONFLICT'||!['PUT','PATCH'].includes(original.method))throw new Error('只能重新提交已停靠的普通编辑冲突。');
  if(!Number.isSafeInteger(base.version)||base.version<0||data.version!==base.version)throw new Error('重新提交必须明确使用已读取的最新版本。');
  const db=await openDatabase(),next=crypto.randomUUID();
  await new Promise<void>((resolve,reject)=>{const tx=db.transaction(STORE,'readwrite'),store=tx.objectStore(STORE);let failure:Error|undefined;
    store.get(original.id).onsuccess=event=>{const current=(event.target as IDBRequest).result as OfflineOperation|undefined;
      if(!current||current.ownerId!==original.ownerId||current.status!=='CONFLICT'||current.updatedAt!==original.updatedAt||current.url!==original.url||current.method!==original.method||JSON.stringify(current.data)!==JSON.stringify(original.data)){failure=new Error('本地操作已变化，请重新查看冲突。');tx.abort();return;}
      const previous=current.resolution;const history=[...(previous?.history??[]),...(previous?[{id:previous.originalId,data:previous.originalData,base:previous.originalBase}]:[])];
      if(history.length>=20){failure=new Error('此冲突已保留较多次修订，请先复制需要的内容，到原页面处理；原队列未改变。');tx.abort();return;}
      const now=new Date().toISOString();store.put({...current,id:next,data:JSON.parse(JSON.stringify(data)),base:JSON.parse(JSON.stringify(base)),status:'PENDING',attempts:0,error:undefined,createdAt:now,updatedAt:now,resolution:{originalId:current.id,originalData:current.data,originalBase:current.base,resolvedAt:now,history}} satisfies OfflineOperation);store.delete(current.id);
    };
    tx.oncomplete=()=>{db.close();resolve();};tx.onabort=tx.onerror=()=>{db.close();reject(failure??tx.error??new Error('冲突草稿未替换，原操作仍保留。'));};
  });announceChange();return next;
}

export async function queueCount(ownerId: string): Promise<number> {
  return transaction<number>('readonly', (store) => store.index(OWNER_INDEX).count(ownerId));
}
