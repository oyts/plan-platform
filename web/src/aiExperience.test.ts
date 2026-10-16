import{describe,it,expect}from'vitest';
import{aiFailureGuidance,aiQuotaHint,aiSubmitUncertain}from'./aiExperience';
import{tokenText,usageState}from'./aiUsage';
describe('scoped AI feedback',()=>{
 it('never promises an unknown result was refunded or retried',()=>{const text=aiFailureGuidance('UNKNOWN','TIMEOUT');expect(text).toContain('额度暂时占用');expect(text).toContain('不要连续重发');expect(aiFailureGuidance('FAILED','BUSY')).toContain('额度已退回');expect(aiFailureGuidance('RUNNING',null)).toContain('不代表已经创建');});
 it('only ambiguous submissions require reconciliation',()=>{expect(aiSubmitUncertain(new Error('network'))).toBe(true);expect(aiSubmitUncertain({response:{status:502}})).toBe(true);expect(aiSubmitUncertain({response:{status:429}})).toBe(false);expect(aiSubmitUncertain({response:{status:503,data:{code:'BUSY'}}})).toBe(false);expect(aiSubmitUncertain({response:{status:503}})).toBe(true);});
 it('distinguishes paused accounts and zero allowance without fabricated money',()=>{const q={date:'2026-10-17',resetsAt:'2026-10-17T16:00:00Z',dailyLimit:20,overrideLimit:null,paused:false,used:20,pending:0,remaining:0,version:0};expect(aiQuotaHint(q)).toContain('北京时间');expect(aiQuotaHint({...q,paused:true})).toContain('暂停');expect(tokenText(null)).toBe('未知');expect(tokenText(0)).toBe('0');expect(usageState('UNKNOWN')).toBe('结果待核实');});
});
