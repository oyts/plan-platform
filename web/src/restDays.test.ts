import { describe, expect, it } from 'vitest';
import { isPlanRestDay, restDaySummary } from './restDays';
describe('per-plan rest days', () => {
  it('never assumes weekends for an empty or legacy configuration', () => {
    expect(isPlanRestDay('2026-10-03', [])).toBe(false);
    expect(isPlanRestDay('2026-10-04', undefined)).toBe(false);
  });
  it('uses ISO weekdays including Sunday and a weekday rest day', () => {
    expect(isPlanRestDay('2026-10-05', [1, 7])).toBe(true);
    expect(isPlanRestDay('2026-10-04', [1, 7])).toBe(true);
    expect(isPlanRestDay('2026-10-03', [1, 7])).toBe(false);
  });
  it('reflects a subsequent edit instead of a global weekend rule', () => {
    expect(isPlanRestDay('2026-10-03', [6])).toBe(true);
    expect(isPlanRestDay('2026-10-03', [3])).toBe(false);
    expect(isPlanRestDay('2026-10-07', [3])).toBe(true);
  });
  it('distinguishes an old server from an explicitly empty selection', () => {
    expect(restDaySummary(undefined)).toContain('升级服务器');
    expect(restDaySummary([])).toBe('未设置固定休息日');
    expect(restDaySummary([7, 1])).toBe('每周 星期一、星期日 休息');
  });
});
