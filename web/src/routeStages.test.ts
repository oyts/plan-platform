import { describe, expect, it } from 'vitest';
import { routeGroups } from './routeStages';
import type { Mainline } from './types';
const line = { stages: [{id:'s',name:'基础',description:'认识吉他',sortOrder:0,version:0}], items:[{id:'a',title:'乐理',description:'音符',sortOrder:0,status:'COMPLETED',stageId:'s'},{id:'b',title:'和弦',description:'练习',sortOrder:1,status:'PENDING',stageId:'s'},{id:'c',title:'曲目',description:'自由练习',sortOrder:2,status:'PENDING',stageId:'gone'}] } as Mainline;
describe('route stage grouping', () => {
  it('keeps unknown and legacy stages visible without discarding steps', () => {
    const groups = routeGroups(line); expect(groups[0]).toMatchObject({name:'基础',completed:1,total:2}); expect(groups[1].items.map(i=>i.id)).toEqual(['c']);
    expect(routeGroups({...line,stages:[]})[0].items).toHaveLength(3);
  });
  it('filters titles and descriptions but retains total stage progress', () => {
    const group = routeGroups(line,'和弦',true)[0]; expect(group.items.map(i=>i.id)).toEqual(['b']); expect(group.total).toBe(2); expect(group.completed).toBe(1);
    expect(routeGroups(line,'音符',true)[0].items).toEqual([]);
  });
});
