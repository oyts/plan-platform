import { describe, expect, it } from 'vitest';
import { freshWorkspace, pathWorkspace, readWorkspace, rememberWorkspace, workspaceDestination, workspaceKey } from './workspaceModel';
describe('independent workspace preferences', () => {
  it('adds todo without losing prior mode positions', () => {
    let state = rememberWorkspace(freshWorkspace(), '/plans/one'); state = rememberWorkspace(state, '/mainlines?line=guitar');
    state = rememberWorkspace(state, '/todos'); expect(state.mode).toBe('todo');
    expect(workspaceDestination(state,'plan')).toBe('/plans/one'); expect(workspaceDestination(state,'route')).toBe('/mainlines?line=guitar');
    expect(workspaceDestination(state)).toBe('/todos');
    const old = readWorkspace({getItem:()=>JSON.stringify({version:1,mode:'route',planPath:'/plans',routePath:'/routes'})},'s','u');
    expect(old.todoPath).toBe('/todos'); expect(old.mode).toBe('route');
  });
  it('classifies old route URLs and plan details without renaming API identifiers', () => {
    expect(pathWorkspace('/mainlines?line=one')).toBe('route'); expect(pathWorkspace('/plans/uuid-1/studio')).toBe('plan');
    expect(pathWorkspace('/routes')).toBe('route'); expect(pathWorkspace('/route-records')).toBe('route');
    expect(pathWorkspace('/sync')).toBeNull(); expect(pathWorkspace('/desktop')).toBeNull(); expect(pathWorkspace('//evil.test')).toBeNull();
  });
  it('remembers each mode separately and shared pages do not switch modes', () => {
    let state = rememberWorkspace(freshWorkspace(), '/plans/one?filter=test');
    state = rememberWorkspace(state, '/mainlines?line=guitar');
    expect(state.mode).toBe('route'); expect(workspaceDestination(state, 'plan')).toBe('/plans/one?filter=test');
    expect(rememberWorkspace(state, '/settings')).toEqual(state); expect(workspaceDestination(state)).toBe('/mainlines?line=guitar');
  });
  it('scopes keys to both server and account', () => {
    const saved = new Map([[workspaceKey('server-a', 'alice'), JSON.stringify({version:1,...freshWorkspace(),mode:'route',routePath:'/mainlines?line=guitar'})]]);
    const storage = {getItem: (key:string) => saved.get(key) ?? null};
    expect(readWorkspace(storage,'server-a','alice').mode).toBe('route');
    expect(readWorkspace(storage,'server-a','bob')).toEqual(freshWorkspace()); expect(readWorkspace(storage,'server-b','alice')).toEqual(freshWorkspace());
  });
  it.each(['broken', '{"version":2}', '{"version":1,"mode":"route","routePath":"https://evil.test","planPath":"/mainlines"}'])('fails safely for corrupted preferences: %s', text => {
    const state = readWorkspace({getItem:()=>text},'server','user'); expect(state.routePath).toBe('/routes'); expect(state.planPath).toBe('/');
  });
  it('does not remember shared or external destinations as a workspace', () => {
    const state = freshWorkspace(); expect(rememberWorkspace(state,'/desktop')).toEqual(state); expect(rememberWorkspace(state,'/plans#unsafe')).toEqual(state);
  });
});
