import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { TokenResponse } from '@/types';

let accessToken: string | null = null;
localStorage.removeItem('plan-platform:access-token');
localStorage.removeItem('plan-platform:refresh-token');
export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE ?? '/api', timeout: 15000, withCredentials: true });
api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

let refreshing: Promise<string> | null = null;
api.interceptors.response.use((response) => response, async (error: AxiosError) => {
  const request = error.config as (InternalAxiosRequestConfig & { retried?: boolean }) | undefined;
  const publicAuthRequest = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']
    .some((path) => request?.url?.includes(path));
  if (error.response?.status !== 401 || !request || request.retried || publicAuthRequest) throw error;
  request.retried = true;
  refreshing ??= axios.post<TokenResponse>(`${api.defaults.baseURL}/auth/refresh`, undefined, { withCredentials: true })
    .then(({ data }) => { saveTokens(data); return data.accessToken; })
    .finally(() => { refreshing = null; });
  try {
    request.headers.Authorization = `Bearer ${await refreshing}`;
  } catch (refreshError) {
    clearTokens();
    throw refreshError;
  }
  return api(request);
});

export function saveTokens(tokens: TokenResponse): void {
  accessToken = tokens.accessToken;
}
export function clearTokens(): void { accessToken = null; }
export function hasToken(): boolean { return Boolean(accessToken); }
export function apiMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as { message?: string; fields?: Record<string, string>; errors?: Record<string, string> } | undefined;
    const fieldErrors = payload?.fields ?? payload?.errors;
    const fieldError = fieldErrors ? Object.values(fieldErrors)[0] : undefined;
    if (fieldError) return fieldError;
    if (payload?.message) return payload.message;
    if (error.response) return `请求未完成（HTTP ${error.response.status}）。`;
    return '无法连接后端服务，请确认 Spring Boot 已启动。';
  }
  return error instanceof Error ? error.message : '操作失败。';
}
