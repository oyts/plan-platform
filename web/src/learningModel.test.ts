import { describe, it, expect } from 'vitest';
import { freshPractice, freshProgress, isLesson, learningKey, lessonForPath, readProgress, stepReady } from './learningModel';
describe('isolated learning model', () => {
  it('only prompts supported business pages, never login or public website', () => {
    expect(lessonForPath('/plans/one/studio')).toBe('plan'); expect(lessonForPath('/schedule')).toBe('plan');
    expect(lessonForPath('/mainlines')).toBe('route'); expect(lessonForPath('/todos')).toBe('todo'); expect(lessonForPath('/sync')).toBe('sync');
    expect(lessonForPath('/ai')).toBe('ai');
    for (const path of ['/login','/register','/welcome','/desktop','/learn','/members','/plans-fake']) expect(lessonForPath(path)).toBeNull();
    expect(isLesson('plan')).toBe(true); expect(isLesson('constructor')).toBe(false);
  });
  it('separates account and server keys without delimiter collisions', () => {
    expect(learningKey('a:b','c')).not.toBe(learningKey('a','b:c'));
    expect(learningKey('cloud','a')).not.toBe(learningKey('cloud','b'));
    expect(learningKey('cloud','a')).not.toBe(learningKey('local','a'));
  });
  it('validates dates and leaves weekends to explicit user choice', () => {
    const p = {...freshPractice(), name:'练习', start:'2026-10-12', end:'2026-10-18', task:'阅读', taskDate:'2026-10-17', rest:[7]};
    expect(stepReady('plan',0,p)).toBe(true); expect(stepReady('plan',1,p)).toBe(true); expect(stepReady('plan',2,p)).toBe(true);
    expect(stepReady('plan',2,{...p,taskDate:'2026-10-18'})).toBe(false);
    expect(stepReady('plan',2,{...p,taskDate:'2026-10-19'})).toBe(false);
    for (const start of ['2026-99-99','2026-02-30','','2026-10-19']) expect(stepReady('plan',0,{...p,start})).toBe(false);
    expect(stepReady('plan',0,{...p,end:'2027-10-18'})).toBe(false);
    expect(stepReady('plan',1,{...p,rest:[1,2,3,4,5,6,7]})).toBe(false);
  });
  it('requires action before advancing, including an explicit conflict resolution', () => {
    const p = freshPractice(); for (const id of ['plan','route','todo','sync'] as const) for(let step=0;step<4;step++) expect(stepReady(id,step,p)).toBe(false);
    expect(stepReady('route',2,{...p,prerequisite:true})).toBe(true);
    expect(stepReady('sync',3,{...p,resolution:'review'})).toBe(false); expect(stepReady('sync',3,{...p,resolution:'cloud'})).toBe(true);
  });
  it('restores completion including the reviewed conflict prerequisites', () => {
    const progress=freshProgress(); progress.sync={step:3,completed:true,dismissed:false,practice:{...freshPractice(),cached:true,queued:true,resolution:'local'}};
    expect(readProgress(JSON.stringify({version:1,progress})).sync).toEqual(progress.sync);
  });
  it('requires consent, selected mode, a manual edit and explicit creation in AI practice', () => {
    const p = {...freshPractice(),name:'两周英语复习',aiConsent:true,aiMode:'PLAN'};
    expect(stepReady('ai',0,p)).toBe(true);expect(stepReady('ai',0,{...p,aiMode:'ROUTE'})).toBe(false);
    expect(stepReady('ai',0,{...p,aiConsent:false})).toBe(false);
    expect(stepReady('ai',1,{...p,aiPreviewed:true,task:'复习10个英语单词'})).toBe(false);
    expect(stepReady('ai',1,{...p,aiPreviewed:true,task:'复习5个英语单词'})).toBe(true);
    expect(stepReady('ai',2,{...p,aiConfirmed:true})).toBe(false);
    expect(stepReady('ai',2,{...p,aiConfirmed:true,aiCreated:true})).toBe(true);
    expect(stepReady('ai',3,{...p,aiShifted:true,resolution:'review'})).toBe(false);
    expect(stepReady('ai',3,{...p,aiShifted:true,resolution:'apply'})).toBe(true);
  });
  it('adds the AI lesson to old v1 progress without resetting the previous four courses', () => {
    const progress=freshProgress();progress.todo={step:3,completed:true,dismissed:true,practice:{...freshPractice(),task:'资料',note:'确认地址',done:true,rescheduled:true}};
    const old:Partial<typeof progress>={...progress};delete old.ai;
    const restored=readProgress(JSON.stringify({version:1,progress:old}));
    expect(restored.todo).toEqual(progress.todo);expect(restored.ai).toEqual(freshProgress().ai);
    const forged=readProgress(JSON.stringify({version:1,progress:{ai:{step:3,completed:true,practice:{aiMode:'evil',aiConsent:'true'}}}}));
    expect(forged.ai.step).toBe(0);expect(forged.ai.practice.aiConsent).toBe(false);expect(forged.ai.practice.aiMode).toBe('');
  });
  it('rejects corrupt progress, clamps unsafe input and does not accept arbitrary properties', () => {
    for(const text of ['broken','{"version":2}','null']) expect(readProgress(text)).toEqual(freshProgress());
    const restored=readProgress(JSON.stringify({version:1,progress:{plan:{step:99,completed:true,practice:{rest:[7,7,0,9,-1,'1'],name:'x'.repeat(500),__proto__:{hacked:true}}}}}));
    expect(restored.plan.step).toBe(0); expect(restored.plan.completed).toBe(false); expect(restored.plan.practice.rest).toEqual([7]); expect(restored.plan.practice.name).toHaveLength(180);
  });
});
