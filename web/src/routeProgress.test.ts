import { describe, expect, it } from 'vitest';
import { nextRouteItem, routeSummary } from './routeProgress';
import type { Mainline, MainlineItem } from './types';
const item = (id: string, status: string, sortOrder: number, unlocked = true): MainlineItem => ({id,status,sortOrder,unlocked,accumulatedSeconds:60,title:id,description:'',acceptance:[],plannedMinutes:30,note:'',startedAt:null,completedAt:null,version:0});
const line = (items:MainlineItem[]):Mainline => ({id:'one',name:'路线',description:'',color:'#002FA7',version:0,completed:0,total:items.length,items});
describe('route-only continuation and progress', () => {
  it('prioritizes running then paused steps without starting a timer', () => {
    expect(nextRouteItem(line([item('next','PENDING',0),item('paused','PAUSED',1),item('running','RUNNING',2)]))?.id).toBe('running');
    expect(nextRouteItem(line([item('next','PENDING',0),item('paused','PAUSED',1)]))?.id).toBe('paused');
  });
  it('keeps completed and locked steps out of next-step recommendations', () => {
    expect(nextRouteItem(line([item('done','COMPLETED',0),item('locked','PENDING',1,false)]))).toBeUndefined();
  });
  it('uses only route items and saved durations, without calendar quotas', () => {
    expect(routeSummary([line([item('done','COMPLETED',0),item('next','PENDING',1)])])).toEqual({routes:1,completed:1,total:2,seconds:120});
    expect(routeSummary([])).toEqual({routes:0,completed:0,total:0,seconds:0});
  });
});
