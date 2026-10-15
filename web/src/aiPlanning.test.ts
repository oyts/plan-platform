import { expect, it } from 'vitest';
import { aiInitialMode, aiPending, aiAppliedPath, aiState, aiExternalPrompt, type AiSession } from './aiPlanning';
it('validates the three explicit entry modes without falling back to a different mode', () => {
  expect(aiInitialMode('plan')).toBe('PLAN'); expect(aiInitialMode('ROUTE')).toBe('ROUTE'); expect(aiInitialMode('TODO')).toBe('TODO');
  expect(aiInitialMode('other')).toBeNull(); expect(aiInitialMode(['PLAN'])).toBeNull();
});
it('only polls admitted unfinished requests and distinguishes uncertain results', () => {
  expect(aiPending(null)).toBe(false);
  for (const state of ['QUEUED', 'RUNNING', 'FAILED', 'UNKNOWN', 'SUCCEEDED']) expect(aiPending({ turns: [{ state }] } as AiSession)).toBe(['QUEUED', 'RUNNING'].includes(state));
  expect(aiState('UNKNOWN')).toContain('暂时占用'); expect(aiState('FAILED')).toContain('退回');
});
it('refuses external or unexpected apply-result navigation', () => {
  const id = '00000000-0000-0000-0000-000000000001';
  for (const path of ['/todos', `/plans/${id}`, `/mainlines?line=${id}`]) expect(aiAppliedPath(path)).toBe(path);
  for (const path of ['//evil.test', 'javascript:alert(1)', '/members', 'https://plan.zijun701.cn/todos', null]) expect(aiAppliedPath(path)).toBeNull();
});
it('supplies a clearly labelled external AI JSON format without real user data', () => {
  for (const mode of ['PLAN', 'ROUTE', 'TODO'] as const) { const prompt = aiExternalPrompt(mode); expect(prompt).toContain(`"mode": "${mode}"`); expect(prompt).toContain('格式示例，不是我的真实计划'); expect(prompt).toContain('不要声称已创建'); }
});
