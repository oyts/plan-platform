import {describe,it,expect} from 'vitest';
import {capabilityGuide,demoCases,healthSummary} from './helpModel';
import {navigationGroups,primaryNavigation} from './navigationCatalog';
describe('help and navigation boundaries',()=>{
  it('does not accept a legacy, failed or unknown health response as compatible',()=>{
    expect(healthSummary({service:'plan-platform-api',ok:true,version:'0.1.15',apiVersion:1})).toEqual({version:'0.1.15',apiVersion:1});
    for(const value of [null,{}, {service:'plan-console-sync',ok:true,version:'1.10.1',apiVersion:1},{service:'plan-platform-api',ok:false,version:'0.1.15',apiVersion:1},{service:'plan-platform-api',ok:true,version:'0.1.15',apiVersion:2},{service:'plan-platform-api',ok:true,version:'not-known',apiVersion:1}])expect(()=>healthSummary(value)).toThrow();
  });
  it('shares one menu while keeping workspace and role boundaries',()=>{
    for(const mode of ['plan','route','todo'] as const){
      const ordinary=navigationGroups(mode,false,false).flatMap(group=>group.items);
      expect(new Set(ordinary.map(item=>item.path)).size).toBe(ordinary.length);
      expect(ordinary.some(item=>item.path==='/help')).toBe(true);
      expect(ordinary.some(item=>['/operations','/members','/desktop'].includes(item.path))).toBe(false);
      const admin=navigationGroups(mode,true,true).flatMap(group=>group.items);
      for(const path of ['/members','/operations','/desktop'])expect(admin.some(item=>item.path===path)).toBe(true);
    }
    expect(primaryNavigation('todo').map(item=>item.path)).toEqual(['/todos']);
    expect(navigationGroups('route',false,false)[0].items.map(item=>item.path)).toEqual(['/route-records']);
    expect(navigationGroups('todo',false,false).flatMap(group=>group.items).some(item=>item.path==='/stats')).toBe(false);
  });
  it('labels synthetic content and gives all capability entries an actual destination',()=>{
    for(const sample of Object.values(demoCases)){expect(sample.name.startsWith('演示：')).toBe(true);expect(sample.items.length).toBeGreaterThan(0);}
    expect(capabilityGuide).toHaveLength(8);expect(capabilityGuide.every(item=>item.path.startsWith('/'))).toBe(true);
  });
});
