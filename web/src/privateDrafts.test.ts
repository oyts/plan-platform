import 'fake-indexeddb/auto';
import {describe,it,expect} from 'vitest';
import {readPrivateDraft,writePrivateDraft,deletePrivateDraft} from './privateDrafts';
import {reactive} from 'vue';
describe('private device drafts',()=>{
  it('serializes reactive drafts before IndexedDB structured cloning',async()=>{const owner=crypto.randomUUID();await writePrivateDraft('server',owner,'pending',reactive({request:{name:'副本'},confirmed:true}));expect(await readPrivateDraft('server',owner,'pending')).toEqual({request:{name:'副本'},confirmed:true});});
  it('keeps server, owner and purpose isolated, supports reopen and does not sync',async()=>{
    const owner=crypto.randomUUID();await writePrivateDraft('server-a',owner,'organize-pending',{operationKey:'synthetic',request:{name:'练习副本'}});
    expect(await readPrivateDraft('server-b',owner,'organize-pending')).toBeUndefined();expect(await readPrivateDraft('server-a','another-owner','organize-pending')).toBeUndefined();expect(await readPrivateDraft('server-a',owner,'weekly')).toBeUndefined();
    expect(await readPrivateDraft('server-a',owner,'organize-pending')).toEqual({operationKey:'synthetic',request:{name:'练习副本'}});
    await deletePrivateDraft('server-a',owner,'organize-pending');expect(await readPrivateDraft('server-a',owner,'organize-pending')).toBeUndefined();
  });
  it('refuses oversized replacement and missing identity without deleting old content',async()=>{
    const owner=crypto.randomUUID();await writePrivateDraft('server',owner,'weekly',{wins:'原草稿'});await expect(writePrivateDraft('server',owner,'weekly',{wins:'x'.repeat(262145)})).rejects.toThrow('过大');expect(await readPrivateDraft('server',owner,'weekly')).toEqual({wins:'原草稿'});await expect(writePrivateDraft('server','','weekly',{})).rejects.toThrow('登录');
  });
});
