/** Device-local business drafts, separated by server, account and purpose. Never a sync queue. */
async function access<T>(mode:IDBTransactionMode,scope:string,owner:string,purpose:string,value?:unknown):Promise<T|undefined>{
  if(!owner)throw new Error('请先登录再保存本机草稿。');
  const serialized=value===undefined?undefined:JSON.stringify(value);
  if(serialized!==undefined&&new TextEncoder().encode(serialized).byteLength>262144)throw new Error('本机草稿过大，未覆盖已保存内容。');
  const db=await new Promise<IDBDatabase>((resolve,reject)=>{const request=indexedDB.open('plan-platform-private-drafts',1);request.onupgradeneeded=()=>request.result.createObjectStore('drafts');request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});
  return new Promise((resolve,reject)=>{const tx=db.transaction('drafts',mode),store=tx.objectStore('drafts'),key=JSON.stringify([scope,owner,purpose]);let result:T|undefined;
    // Vue reactive proxies are not structured-cloneable. Persist the bounded JSON value, not a live proxy.
    const request=mode==='readonly'?store.get(key):serialized===undefined?store.delete(key):store.put(JSON.parse(serialized),key);
    request.onsuccess=()=>{result=request.result as T;};tx.oncomplete=()=>{db.close();resolve(result);};tx.onerror=tx.onabort=()=>{db.close();reject(tx.error??new Error('本机草稿存储不可用。'));};
  });
}
export const readPrivateDraft=<T>(scope:string,owner:string,purpose:string)=>access<T>('readonly',scope,owner,purpose);
export const writePrivateDraft=(scope:string,owner:string,purpose:string,value:unknown)=>access('readwrite',scope,owner,purpose,value);
export const deletePrivateDraft=(scope:string,owner:string,purpose:string)=>access('readwrite',scope,owner,purpose);
