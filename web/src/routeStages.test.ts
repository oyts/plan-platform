import { describe, expect, it } from 'vitest';
import { routeGroups, routeEffort } from './routeStages';
import type { Mainline } from './types';
const line = { stages: [{id:'s',name:'基础',description:'认识吉他',sortOrder:0,version:0}], items:[{id:'a',title:'乐理',description:'音符',sortOrder:0,status:'COMPLETED',stageId:'s'},{id:'b',title:'和弦',description:'练习',sortOrder:1,status:'PENDING',stageId:'s'},{id:'c',title:'曲目',description:'自由练习',sortOrder:2,status:'PENDING',stageId:'gone'}] } as Mainline;
describe('route stage grouping', () => {
  it('sums complete groups before search and never equates unfinished effort with actual remaining time',()=>{const items=Array.from({length:22},(_,i)=>({id:String(i),title:i?'其他':'命中',description:'',sortOrder:i,status:i<2?'COMPLETED':'PENDING',stageId:'s',plannedMinutes:15}));const complete={...line,items} as Mainline;expect(routeEffort(items)).toEqual({total:330,remaining:300});expect(routeGroups(complete,'命中',true)[0]).toMatchObject({total:22,effort:{total:330,remaining:300},items:[]});});
  it('keeps unknown and legacy stages visible without discarding steps', () => {
    const groups = routeGroups(line); expect(groups[0]).toMatchObject({name:'基础',completed:1,total:2}); expect(groups[1].items.map(i=>i.id)).toEqual(['c']);
    expect(routeGroups({...line,stages:[]})[0].items).toHaveLength(3);
  });
  it('filters titles and descriptions but retains total stage progress', () => {
    const group = routeGroups(line,'和弦',true)[0]; expect(group.items.map(i=>i.id)).toEqual(['b']); expect(group.total).toBe(2); expect(group.completed).toBe(1);
    expect(routeGroups(line,'音符',true)[0].items).toEqual([]);
  });
});
