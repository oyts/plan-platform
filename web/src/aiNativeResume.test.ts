import { describe, it, expect } from 'vitest';
import { aiNativeResumePath } from './aiPlanning';
const id='11111111-1111-4111-8111-111111111111';
describe('native AI auth restoration intent',()=>{
  it('restores saved conversation and consented target URLs without touching other modes',()=>{
    expect(aiNativeResumePath(`#/ai?session=${id}`)).toBe(`/ai?session=${id}`);
    expect(aiNativeResumePath(`#/ai?mode=PLAN&target=${id}`)).toBe(`/ai?mode=PLAN&target=${id}`);
    expect(aiNativeResumePath('#/ai')).toBe('/ai');
    expect(aiNativeResumePath('#/todos')).toBeNull();
  });
  it('rejects external, malformed, ambiguous or credential-bearing intent',()=>{
    for(const value of ['#//evil.test/ai','#/ai/other','#/ai?token=secret','#/ai?session=bad',`#/ai?session=${id}&session=${id}`,'#/ai?mode=BAD',`#/ai?target=${id}`,'#/ai#other'])expect(aiNativeResumePath(value)).toBeNull();
  });
});
