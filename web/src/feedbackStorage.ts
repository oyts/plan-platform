import { parseFeedbackDraft, type FeedbackDraft } from './feedbackModel';
async function transact<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await new Promise<IDBDatabase>((resolve,reject) => {
    const request=indexedDB.open('plan-platform-feedback',1);
    request.onupgradeneeded=()=>request.result.createObjectStore('drafts');
    request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);
  });
  return new Promise((resolve,reject)=>{
    let value:T;const tx=db.transaction('drafts',mode), request=action(tx.objectStore('drafts'));
    request.onsuccess=()=>{value=request.result;};
    tx.oncomplete=()=>{db.close();resolve(value);};
    tx.onerror=tx.onabort=()=>{db.close();reject(tx.error??new Error('无法保存本机反馈草稿。'));};
  });
}
function key(scope:string,owner:string){return JSON.stringify([scope,owner]);}
export async function loadFeedbackDraft(scope:string,owner:string):Promise<FeedbackDraft|null>{const row=await transact<unknown>('readonly',store=>store.get(key(scope,owner)));return row===undefined?null:parseFeedbackDraft(row);}
export async function saveFeedbackDraft(scope:string,owner:string,draft:FeedbackDraft):Promise<void>{const value=parseFeedbackDraft(draft);await transact('readwrite',store=>store.put(value,key(scope,owner)));}
export async function removeFeedbackDraft(scope:string,owner:string):Promise<void>{await transact('readwrite',store=>store.delete(key(scope,owner)));}
