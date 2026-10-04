import { describe, expect, it } from 'vitest';
import { projectPending } from './offlineProjection';
import type { OfflineOperation } from './offlineQueue';
const task = { id: 't', title: '原标题', plannedMinutes: 20, version: 3, progress: { status: 'TODO', version: 5 } };
const edit = (status = 'PENDING') => ({ method: 'PUT', url: '/tasks/t', status, data: { title: '本地标题', version: 900, progress: { version: 999 } } } as OfflineOperation);
describe('local intent projection', () => {
  it('projects route step edits without changing execution state or server versions', () => {
    const line = { id:'l',name:'路线',completed:0,items:[{id:'i',title:'原步骤',plannedMinutes:20,unlocked:false,status:'PENDING',version:3,stageId:'s',dependencyMode:'EXPLICIT',prerequisiteIds:['p']}] };
    const operation = {method:'PUT',url:'/mainlines/items/i',status:'PENDING',data:{title:'离线标题',version:99,status:'COMPLETED',unlocked:true}} as OfflineOperation;
    expect(projectPending([line],'/mainlines',[operation])).toMatchObject([{items:[{title:'离线标题',version:3,unlocked:false,status:'PENDING',stageId:'s',prerequisiteIds:['p'],localState:'PENDING'}]}]);
    expect(projectPending([line],'/mainlines',[{...operation,status:'CONFLICT'}])).toMatchObject([{items:[{title:'原步骤',localState:'CONFLICT'}]}]);
  });
  it('projects offline rest-day edits without changing the base version', () => {
    const plan = { id: 'p', name: '计划', taskCount: 2, restDays: [], version: 4 };
    const operation = { method: 'PUT', url: '/plans/p', status: 'PENDING', data: { restDays: [1, 7], version: 4 } } as OfflineOperation;
    expect(projectPending([plan], '/plans', [operation])).toMatchObject([{ restDays: [1, 7], version: 4, localState: 'PENDING' }]);
    expect(plan.restDays).toEqual([]);
    expect(projectPending([plan], '/plans', [{ ...operation, status: 'CONFLICT' }])).toMatchObject([{ restDays: [], localState: 'CONFLICT' }]);
  });
  it('shows local edits without inventing server versions or progress', () => {
    const projected = projectPending([task], '/plans/p/tasks', [edit()]) as typeof task[];
    expect(projected[0]).toMatchObject({ title: '本地标题', version: 3, progress: { version: 5 }, localState: 'PENDING' });
    expect(task.title).toBe('原标题');
  });
  it('shows the actual snapshot when a local operation conflicts', () => {
    expect(projectPending([task], '/plans/p/tasks', [edit('CONFLICT')])).toEqual([{ ...task, localState: 'CONFLICT' }]);
  });
  it('projects nested task pages and dashboard tasks', () => {
    expect(projectPending({ content: [task] }, '/plans/p/tasks/page', [edit()])).toMatchObject({ content: [{ title: '本地标题' }] });
  });
  it('makes an offline creation explicitly local-only', () => {
    const create = { id: 'new', method: 'POST', url: '/plans', status: 'PENDING', data: { name: '草稿' } } as OfflineOperation;
    expect(projectPending([], '/plans', [create])).toMatchObject([{ id: 'local:new', name: '草稿', localOnly: true, localState: 'PENDING' }]);
    expect(projectPending([], '/plans/other/tasks', [create])).toEqual([]);
  });
});
