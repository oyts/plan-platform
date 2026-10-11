import { describe, expect, it } from 'vitest';
import { localDate, projectTodos, shiftDate } from './todos';
import type { OfflineOperation } from './offlineQueue';
const base = { id:'one', title:'原事项', dueDate:'2026-10-12', note:'', done:false, deleted:false, version:3 };
const operation = (data:unknown, status = 'PENDING', method = 'PUT', url = '/todos/one') => ({id:'op',method,url,data,status,createdAt:'2026-10-12T01:00:00Z'}) as OfflineOperation;
describe('standalone todo projection', () => {
  it('uses local calendar dates and handles month rollover', () => { expect(localDate(new Date(2026,9,12,0,1))).toBe('2026-10-12'); expect(shiftDate('2026-10-01',-1)).toBe('2026-09-30'); });
  it('keeps server snapshots immutable and version unchanged while projecting an edit', () => {
    expect(projectTodos([base],[operation({title:'本地事项',done:true})],{from:'2026-10-12',to:'2026-10-12'})).toMatchObject([{title:'本地事项',done:true,version:3,localState:'PENDING'}]); expect(base.title).toBe('原事项');
  });
  it('shows server content for conflicts and failures', () => { for(const status of ['CONFLICT','FAILED']) expect(projectTodos([base],[operation({title:'不应覆盖'},status)])).toMatchObject([{title:'原事项',localState:status}]); });
  it('adds drafts only to the matching date range', () => {
    const op=operation({title:' 新事项 ',dueDate:'2026-10-13'},'PENDING','POST','/todos');
    expect(projectTodos([], [op], {from:'2026-10-12',to:'2026-10-12'})).toEqual([]);
    expect(projectTodos([], [op], {from:'2026-10-13',to:'2026-10-13'})).toMatchObject([{title:'新事项',id:'local:op',localOnly:true}]);
  });
  it('projects tombstones, restoration and date moves without leaking into other views', () => {
    expect(projectTodos([base],[operation({deleted:true})])).toEqual([]);
    expect(projectTodos([base],[operation({deleted:true})],{deleted:true})).toMatchObject([{deleted:true}]);
    expect(projectTodos([{...base,deleted:true}],[operation({deleted:false})],{deleted:true})).toEqual([]);
    expect(projectTodos([base],[operation({dueDate:'2026-10-15'})],{from:'2026-10-12',to:'2026-10-12'})).toEqual([]);
  });
});
