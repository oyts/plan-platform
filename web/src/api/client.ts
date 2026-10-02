import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { TokenResponse } from '@/types';
import { enqueueOperation, type OfflineOperation } from '@/offlineQueue';
import { isNativeClient as isDesktop, desktopScope } from '@/desktop';
import { desktopAdapter } from './desktopAdapter';

type OfflineRequestConfig = AxiosRequestConfig & { retried?: boolean; offlineReplay?: boolean; idempotencyKey?: string };
type InternalOfflineRequestConfig = InternalAxiosRequestConfig & { retried?: boolean; offlineReplay?: boolean; idempotencyKey?: string };

export class OfflineQueuedError extends Error {
  constructor() { super('操作已保存到离线队列，联网后可在同步中心继续。'); this.name = 'OfflineQueuedError'; }
}

let accessToken: string | null = null;
let queueOwnerId: string | null = null;
localStorage.removeItem('plan-platform:access-token');
localStorage.removeItem('plan-platform:refresh-token');
const connection = { baseURL: isDesktop() ? '/api' : import.meta.env.VITE_API_BASE ?? '/api', timeout: 15000, withCredentials: true, ...(isDesktop() ? { adapter: desktopAdapter } : {}) };
export const api = axios.create(connection);
const refreshApi = axios.create(connection);
api.interceptors.request.use(async (config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  const offlineConfig = config as InternalOfflineRequestConfig;
  if (isQueueable(offlineConfig)) {
    offlineConfig.idempotencyKey ??= crypto.randomUUID();
    offlineConfig.headers['X-Idempotency-Key'] = offlineConfig.idempotencyKey;
    if (!offlineConfig.offlineReplay && !navigator.onLine) {
      await queueRequest(offlineConfig);
      throw new OfflineQueuedError();
    }
  }
  return config;
});

let refreshing: Promise<string> | null = null;
api.interceptors.response.use((response) => response, async (error: AxiosError) => {
  if (error instanceof OfflineQueuedError) throw error;
  const request = error.config as InternalOfflineRequestConfig | undefined;
  if (!error.response && request && isQueueable(request) && !request.offlineReplay) {
    await queueRequest(request);
    throw new OfflineQueuedError();
  }
  const publicAuthRequest = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout', '/auth/password-reset/', '/auth/email-verification/confirm']
    .some((path) => request?.url?.includes(path));
  if (error.response?.status !== 401 || !request || request.retried || publicAuthRequest) throw error;
  request.retried = true;
  refreshing ??= refreshApi.post<TokenResponse>('/auth/refresh')
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
  queueOwnerId = tokens.user.id;
}
export function clearTokens(): void { accessToken = null; queueOwnerId = null; }
export function hasToken(): boolean { return Boolean(accessToken); }
export function currentQueueOwner(): string | null { return queueOwnerId ? (isDesktop() ? `${desktopScope()}|${queueOwnerId}` : queueOwnerId) : null; }
export async function replayOfflineOperation(operation: OfflineOperation): Promise<void> {
  if (operation.ownerId !== currentQueueOwner()) throw new Error('该离线操作不属于当前服务器和账号。');
  const config: OfflineRequestConfig = {
    method: operation.method,
    url: operation.url,
    params: operation.params,
    data: operation.data,
    headers: { 'X-Idempotency-Key': operation.id },
    idempotencyKey: operation.id,
    offlineReplay: true,
  };
  await api.request(config);
}
export function apiMessage(error: unknown): string {
  if (error instanceof OfflineQueuedError) return '';
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as { message?: string; fields?: Record<string, string>; errors?: Record<string, string> } | undefined;
    const fieldErrors = payload?.fields ?? payload?.errors;
    const fieldError = fieldErrors ? Object.values(fieldErrors)[0] : undefined;
    if (fieldError) return fieldError;
    if (payload?.message) return payload.message;
    if (error.response) return `请求未完成（HTTP ${error.response.status}）。`;
    return isDesktop() ? error.message : '无法连接后端服务，请确认 Spring Boot 已启动。';
  }
  return error instanceof Error ? error.message : '操作失败。';
}

function isQueueable(config: { method?: string; url?: string; offlineReplay?: boolean }): boolean {
  const method = config.method?.toUpperCase();
  const url = config.url ?? '';
  if (!method || !['POST', 'PUT', 'PATCH'].includes(method)) return false;
  if (!/^\/(plans|tags|tasks|milestones|templates|mainlines)(\/|$)/.test(url)) return false;
  return !/\/(timer|start|pause|complete|manual-time|defer|recurring|recurrences)(\/|$)/.test(url);
}

async function queueRequest(config: InternalOfflineRequestConfig): Promise<void> {
  if (!queueOwnerId) throw new Error('当前登录身份不可用，无法安全保存离线操作。');
  const id = config.idempotencyKey ?? String(config.headers['X-Idempotency-Key'] ?? crypto.randomUUID());
  await enqueueOperation({ id, ownerId: currentQueueOwner()!, method: config.method?.toUpperCase() ?? 'POST', url: config.url ?? '/', params: clone(config.params), data: clone(config.data) });
  window.dispatchEvent(new CustomEvent('plan-platform:offline-queued'));
}

function clone(value: unknown): unknown {
  if (value === undefined || value === null) return value;
  if (typeof value === 'string') {
    try { return JSON.parse(value); } catch { return value; }
  }
  return structuredClone(value);
}
