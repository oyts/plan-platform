import { beforeEach, describe, expect, it, vi } from 'vitest';
const mocked=vi.hoisted(()=>({owner:'a' as string|null,scope:'cloud',load:vi.fn(),save:vi.fn()}));
vi.mock('@/api/client',()=>({currentQueueOwner:()=>mocked.owner,serverScope:()=>mocked.scope}));
vi.mock('./learningStorage',()=>({loadLearning:mocked.load,saveLearning:mocked.save}));
import { advanceLesson, bindLearning, dismissLesson, learning, practiceLesson, resetLearning, restartLesson } from './learning';
import { freshPractice, freshProgress, learningKey } from './learningModel';
beforeEach(()=>{resetLearning();mocked.owner='a';mocked.scope='cloud';mocked.load.mockReset().mockResolvedValue(undefined);mocked.save.mockReset().mockResolvedValue(undefined);});
describe('learning lifecycle',()=>{
  it('loads, persists dismissal and only advances after a real exercise action',async()=>{
    await bindLearning(); expect(learning.ready).toBe(true); await dismissLesson('todo'); expect(mocked.save).toHaveBeenCalledWith(learningKey('cloud','a'),expect.any(String));
    expect(await advanceLesson('todo')).toBe(false); await practiceLesson('todo',{...freshPractice(),task:'练习事项'}); expect(await advanceLesson('todo')).toBe(true);expect(learning.progress.todo.step).toBe(1);
    await restartLesson('todo');expect(learning.progress.todo.practice.task).toBe('');expect(learning.progress.todo.dismissed).toBe(true);
  });
  it('discards a delayed previous account read after account switching',async()=>{
    let resolveOld!:(value:string)=>void; mocked.load.mockImplementationOnce(()=>new Promise<string>(resolve=>{resolveOld=resolve;}));
    const old=bindLearning();mocked.owner='b';await bindLearning();
    const progress=freshProgress();progress.todo.dismissed=true;resolveOld(JSON.stringify({version:1,progress}));await old;
    expect(learning.progress.todo.dismissed).toBe(false);await dismissLesson('plan');expect(mocked.save).toHaveBeenLastCalledWith(learningKey('cloud','b'),expect.any(String));
  });
  it('fails visibly for storage refusal without disabling lessons',async()=>{
    mocked.load.mockRejectedValue(new Error('blocked'));await bindLearning();expect(learning.ready).toBe(true);expect(learning.storageError).toBe(true);
    mocked.save.mockRejectedValue(new Error('quota'));await dismissLesson('sync');expect(learning.storageError).toBe(true);expect(learning.progress.sync.dismissed).toBe(true);
  });
  it('server switching and logout clear in-memory progress',async()=>{
    await dismissLesson('todo');mocked.scope='elsewhere';await bindLearning();expect(learning.progress.todo.dismissed).toBe(false);
    resetLearning();expect(learning.ready).toBe(false);expect(learning.progress).toEqual(freshProgress());
  });
  it('does not apply a pending action to another account or persist when signed out',async()=>{
    let resolveOld!:(value:undefined)=>void;mocked.load.mockImplementationOnce(()=>new Promise<undefined>(resolve=>{resolveOld=resolve;}));
    const pending=dismissLesson('todo');mocked.owner='b';await bindLearning();resolveOld(undefined);await pending;
    expect(learning.progress.todo.dismissed).toBe(false);expect(mocked.save).not.toHaveBeenCalled();
    mocked.owner=null;await dismissLesson('todo');expect(mocked.save).not.toHaveBeenCalled();
  });
  it('does not show a successful dismissal or completion before storage commits',async()=>{
    await bindLearning();let commit!:()=>void;mocked.save.mockImplementationOnce(()=>new Promise<void>(resolve=>{commit=resolve;}));
    const pending=dismissLesson('plan');await vi.waitFor(()=>expect(mocked.save).toHaveBeenCalled());
    expect(learning.progress.plan.dismissed).toBe(false);commit();await pending;expect(learning.progress.plan.dismissed).toBe(true);
  });
});
