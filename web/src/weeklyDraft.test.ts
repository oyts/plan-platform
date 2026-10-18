import {describe,it,expect} from 'vitest';
import {parseWeeklyDraft} from './weeklyDraft';
describe('private weekly draft',()=>{
  it('retains its original cloud version and does not mistake it for a synced review',()=>{const value={schemaVersion:1,weekStart:'2026-10-12',savedAt:new Date().toISOString(),baseVersion:0,content:{wins:'本机收获',blockers:'',nextFocus:'下周'}};expect(parseWeeklyDraft(value,'2026-10-12')).toEqual(value);expect(parseWeeklyDraft(undefined,'2026-10-12')).toBeNull();});
  it('refuses a different week, corrupt text or a false version without replacing data',()=>{const value={schemaVersion:1,weekStart:'2026-10-12',savedAt:new Date().toISOString(),baseVersion:-1,content:{wins:'',blockers:'',nextFocus:''}};expect(()=>parseWeeklyDraft(value,'2026-10-12')).toThrow('格式');expect(()=>parseWeeklyDraft({...value,baseVersion:null},'2026-10-19')).toThrow('格式');expect(()=>parseWeeklyDraft({...value,baseVersion:0,content:{...value.content,wins:'x'.repeat(4001)}},'2026-10-12')).toThrow('格式');});
});
