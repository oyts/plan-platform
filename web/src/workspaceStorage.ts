// Navigation metadata only. No credentials or business records enter this store.
async function database(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open('plan-platform-workspaces', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('preferences');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
async function transact<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await database();
  return new Promise((resolve, reject) => {
    let value: T;
    // Await the commit before displaying the destination. Android may kill the
    // WebView before its asynchronously flushed localStorage reaches disk.
    const tx = db.transaction('preferences', mode);
    const request = action(tx.objectStore('preferences'));
    request.onsuccess = () => { value = request.result; };
    tx.oncomplete = () => { db.close(); resolve(value); };
    tx.onerror = tx.onabort = () => { db.close(); reject(tx.error ?? new Error('Navigation preference unavailable')); };
  });
}
export async function readWorkspaceRecord(key: string): Promise<string | undefined> {
  try { return await transact('readonly', store => store.get(key)); } catch { return undefined; }
}
export async function writeWorkspaceRecord(key: string, serialized: string): Promise<void> {
  try { await transact('readwrite', store => store.put(serialized, key)); } catch { /* Storage refusal must not prevent navigation. */ }
}
