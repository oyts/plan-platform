import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { TokenResponse } from '@/types';

const ACCESS_KEY = 'plan-platform:access-token';
const REFRESH_KEY = 'plan-platform:refresh-token';
export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE ?? '/api', timeout: 15000 });
api.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<string> | null = null;
api.interceptors.response.use((response) => response, async (error: AxiosError) => {
  const request = error.config as (InternalAxiosRequestConfig & { retried?: boolean }) | undefined;
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (error.response?.status !== 401 || !request || request.retried || !refreshToken || request.url?.includes('/auth/refresh')) throw error;
  request.retried = true;
  refreshing ??= axios.post<TokenResponse>(`${api.defaults.baseURL}/auth/refresh`, { refreshToken })
    .then(({ data }) => { saveTokens(data); return data.accessToken; })
    .finally(() => { refreshing = null; });
  request.headers.Authorization = `Bearer ${await refreshing}`;
  return api(request);
});

export function saveTokens(tokens: TokenResponse): void {
  localStorage.setItem(ACCESS_KEY, tokens.accessToken); localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
}
export function clearTokens(): void { localStorage.removeItem(ACCESS_KEY); localStorage.removeItem(REFRESH_KEY); }
export function hasToken(): boolean { return Boolean(localStorage.getItem(ACCESS_KEY)); }
export function getRefreshToken(): string | null { return localStorage.getItem(REFRESH_KEY); }
export function apiMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as { message?: string; errors?: Record<string, string> } | undefined;
    const fieldError = payload?.errors ? Object.values(payload.errors)[0] : undefined;
    if (fieldError) return fieldError;
    if (payload?.message) return payload.message;
    if (error.response) return `请求未完成（HTTP ${error.response.status}）。`;
    return '无法连接后端服务，请确认 Spring Boot 已启动。';
  }
  return error instanceof Error ? error.message : '操作失败。';
}
