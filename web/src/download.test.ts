import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
const native = vi.hoisted(() => ({ android: true, desktop: false, invoke: vi.fn() }));
vi.mock('@/desktop', () => ({ isAndroid: () => native.android, isDesktop:()=>native.desktop, nativeInvoke: native.invoke }));
import { downloadFile, downloadErrorMessage } from './download';
import { desktopAdapter } from './api/desktopAdapter';
beforeEach(() => { native.invoke.mockReset(); native.android = true; native.desktop=false; });
describe('Android exports', () => {
  it('shares UTF-8 contents through the native file provider', async () => {
    await downloadFile(new Blob(['日期,计划\n2026-10-02,学习'], { type: 'text/csv' }), 'plan-ledger.csv');
    expect(native.invoke).toHaveBeenCalledWith('share_file', { body: '日期,计划\n2026-10-02,学习', fileName: 'plan-ledger.csv', mimeType: 'text/csv' });
  });
  it('shares a private JSON file without changing its Unicode contents',async()=>{
    const body=JSON.stringify({format:'plan-cabin-private-backup',schemaVersion:1,title:'合成数据🎸'});
    await downloadFile(new Blob([body],{type:'application/json'}),'plan-cabin-private.json');
    expect(native.invoke).toHaveBeenCalledWith('share_file',{body,fileName:'plan-cabin-private.json',mimeType:'application/json'});
  });
  it('reads bounded JSON errors instead of treating a failed Blob export as a file',async()=>{
    const error={isAxiosError:true,response:{data:new Blob([JSON.stringify({message:'备份文件超过允许范围。'})])}};
    expect(await downloadErrorMessage(error)).toBe('备份文件超过允许范围。');
    expect(await downloadErrorMessage({isAxiosError:true,response:{data:new Blob(['<html>not JSON</html>'])}})).toBeUndefined();
    expect(await downloadErrorMessage({isAxiosError:true,response:{data:new Blob(['x'.repeat(8193)])}})).toBeUndefined();
  });
  it.each([
    ['plan-tasks.csv', 'text/csv', '\uFEFF日期,任务\r\n2026-10-02,"学习,复盘"'],
    ['review.md', 'text/markdown', '\uFEFF# 每日复盘\n完成学习 ✅'],
    ['plan-ledger.csv', 'text/csv', '\uFEFF日期,备注\n2026-10-02,保留中间的\uFEFF标记'],
  ])('preserves the original UTF-8 BOM and bytes when sharing %s', async (fileName, mimeType, body) => {
    const blob = new Blob([body], { type: mimeType });
    await downloadFile(blob, fileName);
    expect(native.invoke).toHaveBeenCalledWith('share_file', { fileName, mimeType, body });
    const sharedBody = native.invoke.mock.calls[0][1].body as string;
    expect(new TextEncoder().encode(sharedBody)).toEqual(new Uint8Array(await blob.arrayBuffer()));
    expect(sharedBody.startsWith('\uFEFF\uFEFF')).toBe(false);
  });
  it('does not add a BOM to Markdown that did not have one', async () => {
    const body = '# 复盘\n中文与 emoji 🎸\n第二行';
    await downloadFile(new Blob([body], { type: 'text/markdown' }), 'review.md');
    expect(native.invoke).toHaveBeenCalledWith('share_file', { fileName: 'review.md', mimeType: 'text/markdown', body });
  });
  it('preserves BOM bytes from the native API response through the sharing bridge', async () => {
    const body = '\uFEFF日期,任务\n2026-10-02,学习';
    native.invoke.mockResolvedValueOnce({ status: 200, headers: { 'content-type': 'text/csv; charset=UTF-8' }, body });
    const api = axios.create({ baseURL: '/api', adapter: desktopAdapter });
    const result = await api.get('/exports/tasks', { responseType: 'blob' });
    await downloadFile(result.data, 'plan-tasks.csv');
    expect(native.invoke).toHaveBeenLastCalledWith('share_file', {
      fileName: 'plan-tasks.csv', mimeType: 'text/csv; charset=utf-8', body,
    });
    const sharedBytes = new TextEncoder().encode(native.invoke.mock.calls[1][1].body);
    expect(Array.from(sharedBytes.slice(0, 3))).toEqual([0xEF, 0xBB, 0xBF]);
    expect(sharedBytes).toEqual(new TextEncoder().encode(body));
  });
  it('surfaces sharing failures to the caller', async () => {
    native.invoke.mockRejectedValue(new Error('导出目录不可用'));
    await expect(downloadFile(new Blob(['复盘']), 'review.md')).rejects.toThrow('导出目录不可用');
  });
});

describe('Windows native save-as',()=>{
  it('uses a user-selected native destination and preserves UTF-8 BOM',async()=>{
    native.android=false;native.desktop=true;native.invoke.mockResolvedValue(true);
    const body='\uFEFF日期,计划\r\n2026-10-22,合成🎸';
    expect(await downloadFile(new Blob([body]),'合成.csv')).toBe(true);
    expect(native.invoke).toHaveBeenCalledWith('desktop_save_file',{fileName:'合成.csv',body});
  });
  it('returns cancellation without claiming a saved file or retrying',async()=>{
    native.android=false;native.desktop=true;native.invoke.mockResolvedValue(false);
    expect(await downloadFile(new Blob(['{}']),'backup.json')).toBe(false);expect(native.invoke).toHaveBeenCalledTimes(1);
  });
  it('rejects invalid UTF-8 instead of silently changing private backups',async()=>{
    native.android=false;native.desktop=true;
    await expect(downloadFile(new Blob([new Uint8Array([0xff])]),'backup.json')).rejects.toThrow();expect(native.invoke).not.toHaveBeenCalled();
  });
});
