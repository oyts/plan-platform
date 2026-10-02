import axios, { AxiosError, AxiosHeaders, type AxiosAdapter, type AxiosResponse } from 'axios';
import { nativeInvoke } from '@/desktop';

interface NativeResponse { status: number; headers: Record<string, string>; body: string }
export const desktopAdapter: AxiosAdapter = async (config) => {
  const uri = axios.getUri(config);
  const target = new URL(uri, 'https://desktop.invalid');
  if (target.origin !== 'https://desktop.invalid' || !target.pathname.startsWith('/api/')) {
    throw new AxiosError('客户端请求必须使用 /api/ 下的相对地址。', AxiosError.ERR_BAD_REQUEST, config);
  }
  const headers: Record<string, string> = {};
  for (const [name, value] of Object.entries(config.headers.toJSON())) { if (typeof value === 'string') headers[name] = value; }
  let native: NativeResponse;
  try {
    native = await nativeInvoke<NativeResponse>('desktop_request', { request: {
      method: config.method?.toUpperCase() ?? 'GET', path: target.pathname, query: target.search.slice(1) || null,
      headers, body: config.data === undefined || config.data === null ? null : typeof config.data === 'string' ? config.data : JSON.stringify(config.data),
    } });
  } catch (reason) { throw new AxiosError(String(reason), AxiosError.ERR_NETWORK, config); }
  const contentType = native.headers['content-type'] ?? 'application/json';
  const response: AxiosResponse = {
    config, status: native.status, statusText: String(native.status), headers: new AxiosHeaders(native.headers),
    data: config.responseType === 'blob' ? new Blob([native.body], { type: contentType }) : native.body,
  };
  if (!config.validateStatus || config.validateStatus(native.status)) return response;
  throw new AxiosError('请求未完成。', native.status >= 500 ? AxiosError.ERR_BAD_RESPONSE : AxiosError.ERR_BAD_REQUEST, config, undefined, response);
};
