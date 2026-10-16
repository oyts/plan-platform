import {describe,it,expect} from 'vitest';
import {pacingPrompts} from './aiPacing';
import type {AiAdjustment} from './aiPlanning';
const adjustment=(mode:'PLAN'|'ROUTE'|'TODO'):AiAdjustment=>({path:'/plans/test',protectedItems:['done'],original:{schemaVersion:1,mode,revision:0,title:'合成案例',startDate:'2026-10-12',endDate:'2026-10-25',restDays:[7],stages:[],items:['done','pending'].map(localId=>({localId,title:localId,note:'',dueDate:'2026-10-16',plannedMinutes:20,stageId:null,prerequisites:[]}))}});
describe('non-executing pacing prompt suggestions',()=>{
  it('extends only the deadline and names history/rest-day protection',()=>{
    const a=adjustment('PLAN'),before=JSON.stringify(a),items=pacingPrompts(a);
    expect(items[0].message).toContain('2026-11-01');expect(items[0].message).toContain('保持原休息日');expect(items[0].message).toContain('不改写已完成事项');expect(JSON.stringify(a)).toBe(before);
  });
  it('does not invent dates for invalid/overflowing plan ends',()=>{
    for(const end of ['2026-02-30','9999-12-31','']){const a=adjustment('PLAN');a.original.endDate=end;expect(pacingPrompts(a).some(x=>x.id==='extend')).toBe(false);}
  });
  it('keeps routes independent of daily scheduling and todos scoped to one item',()=>{
    expect(pacingPrompts(adjustment('ROUTE'))[0].message).toContain('不强制按天安排');expect(pacingPrompts(adjustment('TODO'))[0].message).toContain('不追加别的待办');
  });
  it('does not suggest paid rewrites when every original item is protected',()=>{
    const a=adjustment('PLAN');a.protectedItems=['done','pending'];expect(pacingPrompts(a)).toEqual([]);
  });
});
