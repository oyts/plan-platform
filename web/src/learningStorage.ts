async function transact<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open('plan-platform-learning', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('progress');
    request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
  });
  return new Promise((resolve, reject) => {
    let value: T;
    const tx = db.transaction('progress', mode); const request = action(tx.objectStore('progress'));
    request.onsuccess = () => { value = request.result; };
    tx.oncomplete = () => { db.close(); resolve(value); };
    tx.onerror = tx.onabort = () => { db.close(); reject(tx.error ?? new Error('Learning storage unavailable')); };
  });
}
export function loadLearning(key: string): Promise<string | undefined> { return transact('readonly', store => store.get(key)); }
export function saveLearning(key: string, value: string): Promise<unknown> { return transact('readwrite', store => store.put(value, key)); }
