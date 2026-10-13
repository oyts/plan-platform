import { IDBFactory } from 'fake-indexeddb';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadLearning, saveLearning } from './learningStorage';
beforeEach(()=>vi.stubGlobal('indexedDB',new IDBFactory()));
describe('durable learning storage',()=>{
  it('awaits commit, overwrites only the selected account and reads unknown keys safely',async()=>{
    await saveLearning('a','old');await saveLearning('b','other');await saveLearning('a','new');
    expect(await loadLearning('a')).toBe('new');expect(await loadLearning('b')).toBe('other');expect(await loadLearning('c')).toBeUndefined();
  });
  it('reports storage refusal to the learning UI instead of pretending to save',async()=>{
    vi.stubGlobal('indexedDB',{open:()=>{throw new Error('refused');}});
    await expect(loadLearning('a')).rejects.toThrow('refused');await expect(saveLearning('a','value')).rejects.toThrow('refused');
  });
});
