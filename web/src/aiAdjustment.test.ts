import { expect, it } from 'vitest';
import { aiDifferences, aiExternalAdjustmentPrompt, type AiDraft } from './aiPlanning';
function base(): AiDraft { return {schemaVersion:1,mode:'PLAN',revision:0,title:'原周期',startDate:'2026-10-01',endDate:'2026-10-31',restDays:[7],stages:[],items:[{localId:'existing_1',title:'原任务',note:'保留说明',dueDate:'2026-10-16',plannedMinutes:30,stageId:null,prerequisites:[]}]}; }
it('does not fabricate changes for an unchanged draft', () => {
  const original=base(); expect(aiDifferences(original,structuredClone(original))).toEqual([]);
});
it('shows only actual metadata and item changes with before and after values', () => {
  const original=base(), changed=structuredClone(original);changed.endDate='2026-11-07';changed.items[0]!.plannedMinutes=15;
  const differences=aiDifferences(original,changed);expect(differences).toHaveLength(2);
  expect(differences[0]!.fields).toEqual([{label:'结束日期',before:'2026-10-31',after:'2026-11-07'}]);
  expect(differences[1]!.fields).toEqual([{label:'预计分钟',before:'30',after:'15'}]);
});
it('marks new stages and steps as additions and names dependencies without exposing database ids', () => {
  const original=base();original.mode='ROUTE';original.startDate=null;original.endDate=null;original.restDays=[];original.items[0]!.dueDate=null;
  const changed=structuredClone(original);changed.stages.push({localId:'new-stage',title:'新阶段'});changed.items.push({...changed.items[0]!,localId:'new-step',title:'追加练习',stageId:'new-stage',prerequisites:['existing_1']});
  const differences=aiDifferences(original,changed);expect(differences).toHaveLength(2);expect(differences.every(x=>x.kind==='ADD')).toBe(true);expect(differences[1]!.fields[0]!.after).toContain('原任务');
});
it('external adjustment format contains only selected draft and protection instructions, not private paths', () => {
  const original=base();const prompt=aiExternalAdjustmentPrompt({original,protectedItems:['existing_1'],path:'/plans/private-database-id'},original);
  expect(prompt).toContain('existing_1');expect(prompt).toContain('不得删除');expect(prompt).toContain('受保护');expect(prompt).not.toContain('private-database-id');
});
