import { describe, expect, it } from 'vitest';
import { quotaLimitValid, quotaRule } from './aiQuota';
describe('AI quota form boundaries', () => {
  it('requires an explicit bounded integer including zero', () => {
    for (const value of [0, 1, 10000]) expect(quotaLimitValid(value)).toBe(true);
    for (const value of [-1, 0.5, 10001, NaN, Infinity, '', '3', null, undefined]) expect(quotaLimitValid(value)).toBe(false);
  });
  it('distinguishes inherited quota from a personal zero', () => {
    expect(quotaRule(null)).toBe('使用默认额度'); expect(quotaRule(0)).toBe('0 次/天');
  });
});
