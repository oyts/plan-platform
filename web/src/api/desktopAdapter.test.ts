import { beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import { nativeInvoke as invoke } from '@/desktop';
import { desktopAdapter } from './desktopAdapter';

vi.mock('@/desktop', () => ({ nativeInvoke: vi.fn() }));
const mockedInvoke = vi.mocked(invoke);
const api = axios.create({ baseURL: '/api', adapter: desktopAdapter });
beforeEach(() => { mockedInvoke.mockReset(); });

describe('desktop native transport adapter', () => {
  it('transfers JSON, query, bearer and idempotency key to native transport', async () => {
    mockedInvoke.mockResolvedValue({ status: 201, headers: { 'content-type': 'application/json' }, body: '{"id":"one"}' });
    const response = await api.post('/plans', { title: '学习' }, { params: { page: 2 }, headers: { Authorization: 'Bearer test', 'X-Idempotency-Key': 'once' } });
    expect(response.data).toEqual({ id: 'one' });
    expect(mockedInvoke).toHaveBeenCalledWith('desktop_request', { request: expect.objectContaining({ method: 'POST', path: '/api/plans', query: 'page=2', body: '{"title":"学习"}', headers: expect.objectContaining({ Authorization: 'Bearer test', 'X-Idempotency-Key': 'once' }) }) });
  });
  it('preserves 409 conflict payload instead of queuing it as a network error', async () => {
    mockedInvoke.mockResolvedValue({ status: 409, headers: {}, body: '{"message":"版本不一致"}' });
    await expect(api.patch('/tasks/one', {})).rejects.toMatchObject({ response: { status: 409, data: { message: '版本不一致' } } });
  });
  it('classifies native connection failures as network failures', async () => {
    mockedInvoke.mockRejectedValue('证书无效');
    const error: unknown = await api.get('/plans').catch((reason: unknown) => reason);
    expect(axios.isAxiosError(error)).toBe(true);
    if (axios.isAxiosError(error)) { expect(error.code).toBe('ERR_NETWORK'); expect(error.message).toBe('证书无效'); }
  });
  it('keeps UTF-8 downloads and download headers', async () => {
    mockedInvoke.mockResolvedValue({ status: 200, headers: { 'content-type': 'text/csv', 'content-disposition': 'attachment; filename=plan.csv' }, body: '日期,计划\n2026-10-02,学习' });
    const result = await api.get('/exports/plans', { responseType: 'blob' });
    expect(await result.data.text()).toContain('学习');
    expect(result.headers['content-disposition']).toContain('plan.csv');
  });
  it.each(['https://evil.example/api/plans', '//evil.example/api/plans', '/actuator/health'])('rejects requests outside the API: %s', async (url) => {
    await expect(axios.get(url, { adapter: desktopAdapter })).rejects.toMatchObject({ code: 'ERR_BAD_REQUEST' });
    expect(mockedInvoke).not.toHaveBeenCalled();
  });
});
