import { beforeEach, describe, expect, it, vi } from 'vitest';
const native = vi.hoisted(() => ({ android: true, invoke: vi.fn() }));
vi.mock('@/desktop', () => ({ isAndroid: () => native.android, nativeInvoke: native.invoke }));
import { downloadFile } from './download';
beforeEach(() => { native.invoke.mockReset(); native.android = true; });
describe('Android exports', () => {
  it('shares UTF-8 contents through the native file provider', async () => {
    await downloadFile(new Blob(['日期,计划\n2026-10-02,学习'], { type: 'text/csv' }), 'plan-ledger.csv');
    expect(native.invoke).toHaveBeenCalledWith('share_file', { body: '日期,计划\n2026-10-02,学习', fileName: 'plan-ledger.csv', mimeType: 'text/csv' });
  });
  it('surfaces sharing failures to the caller', async () => {
    native.invoke.mockRejectedValue(new Error('导出目录不可用'));
    await expect(downloadFile(new Blob(['复盘']), 'review.md')).rejects.toThrow('导出目录不可用');
  });
});
