import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { TokenResponse } from '@/types';
import { enqueueOperation, listOperations, type OfflineOperation } from '@/offlineQueue';
import { cacheable, cachedIdentity, cacheResponse, clearResponses, forgetScope, readResponse, rememberIdentity } from '@/offlineCache';
import { projectPending } from '@/offlineProjection';
import { connectionState, markConnected, markDisconnected, requiresConnection } from '@/connectionState';
import { isNativeClient as isDesktop, desktopScope } from '@/desktop';
import { desktopAdapter } from './desktopAdapter';
import { requiredClientUpdate } from '@/clientUpdates';

type OfflineRequestConfig = AxiosRequestConfig & { retried?: boolean; offlineReplay?: boolean; idempotencyKey?: string };
type InternalOfflineRequestConfig = InternalAxiosRequestConfig & { retried?: boolean; offlineReplay?: boolean; idempotencyKey?: string; cacheOwner?: string | null; cacheScope?: string; sessionEpoch?: number };

export class OfflineQueuedError extends Error {
  constructor() { super('操作已保存到离线队列，联网后可在同步中心继续。'); this.name = 'OfflineQueuedError'; }
}
export class OfflineUnavailableError extends Error {
  constructor(message = '此内容尚未缓存，请联网加载后再离线查看。') { super(message); this.name = 'OfflineUnavailableError'; }
}

let accessToken: string | null = null;
let queueOwnerId: string | null = null;
let authenticatedScope = '';
let epoch = 0;
export function serverScope(): string { return isDesktop() ? desktopScope() : `web:${import.meta.env.VITE_API_BASE ?? '/api'}`; }
localStorage.removeItem('plan-platform:access-token');
localStorage.removeItem('plan-platform:refresh-token');
const connection = { baseURL: isDesktop() ? '/api' : import.meta.env.VITE_API_BASE ?? '/api', timeout: 15000, withCredentials: true, ...(isDesktop() ? { adapter: desktopAdapter } : {}) };
export const api = axios.create(connection);
const refreshApi = axios.create(connection);
api.interceptors.request.use(async (config) => {
  const offlineConfig = config as InternalOfflineRequestConfig;
  if (requiredClientUpdate() && !['GET', 'HEAD'].includes(config.method?.toUpperCase() ?? 'GET') && !config.url?.startsWith('/auth/')) throw new OfflineUnavailableError('当前客户端版本已停止支持，请先更新。已有待同步操作仍保留，更新后再提交。');
  const owner = currentQueueOwner();
  offlineConfig.cacheOwner = owner; offlineConfig.cacheScope = serverScope(); offlineConfig.sessionEpoch = epoch;
  if (accessToken && owner) config.headers.Authorization = `Bearer ${accessToken}`;
  if (config.method?.toUpperCase() === 'GET' && owner && requiresConnection() && config.url !== '/auth/config') {
    if (!cacheable(config.url ?? '')) throw new OfflineUnavailableError('此功能需要连接服务器，请先恢复连接。');
    config.adapter = async () => cachedReply(offlineConfig);
  }
  if (isQueueable(offlineConfig)) {
    offlineConfig.idempotencyKey ??= crypto.randomUUID();
    offlineConfig.headers['X-Idempotency-Key'] = offlineConfig.idempotencyKey;
    if (!offlineConfig.offlineReplay && (!navigator.onLine || requiresConnection())) {
      await queueRequest(offlineConfig);
      throw new OfflineQueuedError();
    }
  }
  if (!['GET', 'HEAD'].includes(config.method?.toUpperCase() ?? 'GET') && !config.url?.startsWith('/auth/') && requiresConnection()) {
    throw new OfflineUnavailableError('计时、完成、删除及此项操作需要联网确认，请先恢复连接。');
  }
  return config;
});

let refreshing: Promise<string> | null = null;
api.interceptors.response.use(async (response) => {
  const request = response.config as InternalOfflineRequestConfig;
  if (request.cacheOwner && (request.sessionEpoch !== epoch || request.cacheOwner !== currentQueueOwner())) throw new Error('账号或服务器已变化，请重新读取页面。');
  if (!response.headers['x-plan-local-cache']) {
    markConnected(); connectionState.cacheReadAt = 0;
    if (request.method?.toUpperCase() === 'GET' && request.cacheOwner && cacheable(request.url ?? '')) {
      await cacheResponse(request.cacheScope!, request.cacheOwner, request.url!, request.params, response.data);
    }
    if (request.method?.toUpperCase() === 'DELETE' && request.cacheOwner) await clearResponses(request.cacheScope!).catch(() => { connectionState.cacheAvailable = false; });
  }
  if (!response.headers['x-plan-local-cache'] && request.method?.toUpperCase() === 'GET' && request.cacheOwner && cacheable(request.url ?? '')) {
    response.data = projectPending(response.data, request.url!, await readableOperations(request.cacheOwner));
  }
  return response;
}, async (error: AxiosError) => {
  if (error instanceof OfflineQueuedError || error instanceof OfflineUnavailableError) throw error;
  const request = error.config as InternalOfflineRequestConfig | undefined;
  if (request?.cacheOwner && (request.sessionEpoch !== epoch || request.cacheOwner !== currentQueueOwner())) throw new Error('账号或服务器已变化，请重新读取页面。');
  if (!error.response) markDisconnected();
  if (!error.response && request && isQueueable(request) && !request.offlineReplay) {
    await queueRequest(request);
    throw new OfflineQueuedError();
  }
  if (!error.response && request?.method?.toUpperCase() === 'GET' && request.cacheOwner && cacheable(request.url ?? '')) return cachedReply(request);
  const publicAuthRequest = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout', '/auth/password-reset/', '/auth/email-verification/confirm']
    .some((path) => request?.url?.includes(path));
  if (error.response?.status === 401 && request && (request.retried || request.url === '/auth/refresh')) {
    clearTokens(); await forgetScope(serverScope()).catch(() => undefined);
    window.dispatchEvent(new CustomEvent('plan-platform:session-invalidated'));
  }
  if (error.response?.status !== 401 || !request || request.retried || publicAuthRequest) throw error;
  request.retried = true;
  refreshing ??= refreshApi.post<TokenResponse>('/auth/refresh')
    .then(async ({ data }) => { saveTokens(data); await rememberIdentity(serverScope(), currentQueueOwner()!, data.user); return data.accessToken; })
    .finally(() => { refreshing = null; });
  try {
    request.headers.Authorization = `Bearer ${await refreshing}`;
  } catch (refreshError) {
    if (axios.isAxiosError(refreshError) && !refreshError.response) markDisconnected();
    // An explicit 401 is not a network-only outage. Fail closed if refresh cannot validate it.
    clearTokens(); await forgetScope(serverScope()).catch(() => undefined); window.dispatchEvent(new CustomEvent('plan-platform:session-invalidated'));
    throw refreshError;
  }
  return api(request);
});

export function saveTokens(tokens: TokenResponse): void {
  if (queueOwnerId !== tokens.user.id || authenticatedScope !== serverScope()) epoch++;
  accessToken = tokens.accessToken;
  queueOwnerId = tokens.user.id;
  authenticatedScope = serverScope(); connectionState.offlineSession = false; markConnected();
}
export function restoreOfflineIdentity(user: { id: string }): void { epoch++; accessToken = null; queueOwnerId = user.id; authenticatedScope = serverScope(); connectionState.offlineSession = true; markDisconnected(); }
export function clearTokens(): void { epoch++; accessToken = null; queueOwnerId = null; connectionState.offlineSession = false; connectionState.cacheReadAt = 0; }
export function hasToken(): boolean { return Boolean(accessToken && currentQueueOwner()); }
export function hasSession(): boolean { return Boolean(currentQueueOwner()); }
export function currentQueueOwner(): string | null { return queueOwnerId && authenticatedScope === serverScope() ? (isDesktop() ? `${authenticatedScope}|${queueOwnerId}` : queueOwnerId) : null; }
export async function replayOfflineOperation(operation: OfflineOperation): Promise<void> {
  if (operation.ownerId !== currentQueueOwner()) throw new Error('该离线操作不属于当前服务器和账号。');
  if (!hasToken()) throw new Error('请先恢复连接并验证登录，再同步本地操作。');
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

function isQueueable(config: { method?: string; url?: string; data?: unknown; offlineReplay?: boolean }): boolean {
  const method = config.method?.toUpperCase();
  const url = config.url ?? '';
  if (!method || !['POST', 'PUT', 'PATCH'].includes(method)) return false;
  if (!/^\/(plans|tags|tasks|milestones|templates|mainlines)(\/|$)/.test(url)) return false;
  if (url.startsWith('/mainlines')) {
    // Topology needs an authoritative graph. Never queue linked creations or
    // prerequisite/stage changes after an ambiguous network failure.
    if (method === 'POST' || /\/stages(\/|$)/.test(url)) return false;
    const data = clone(config.data);
    if (data && typeof data === 'object' && ('routing' in data || 'executionMode' in data)) return false;
  }
  return !/\/(timer|start|pause|complete|manual-time|defer|progress|recurring|recurrences)(\/|$)/.test(url);
}

async function queueRequest(config: InternalOfflineRequestConfig): Promise<void> {
  if (config.url?.includes('/local:')) throw new OfflineUnavailableError('新建记录尚未同步，请先同步后再添加关联内容。');
  if (!currentQueueOwner()) throw new Error('当前登录身份不可用，无法安全保存离线操作。');
  const identity = await cachedIdentity(serverScope());
  if (!identity || identity.owner !== currentQueueOwner()) throw new OfflineUnavailableError('离线工作区已过期或不可用，请联网重新登录。');
  if (['PUT', 'PATCH'].includes(config.method?.toUpperCase() ?? '') && (await listOperations(identity.owner)).some(row => row.url === config.url)) throw new OfflineUnavailableError('此记录已有本地修改，请先同步或处理队列，再继续编辑。');
  const id = config.idempotencyKey ?? String(config.headers['X-Idempotency-Key'] ?? crypto.randomUUID());
  await enqueueOperation({ id, ownerId: currentQueueOwner()!, method: config.method?.toUpperCase() ?? 'POST', url: config.url ?? '/', params: clone(config.params), data: clone(config.data) });
  window.dispatchEvent(new CustomEvent('plan-platform:offline-queued'));
}

async function cachedReply(request: InternalOfflineRequestConfig) {
  const owner = request.cacheOwner;
  if (!owner || request.sessionEpoch !== epoch || owner !== currentQueueOwner()) throw new OfflineUnavailableError('登录身份已变化，请重新登录。');
  const identity = await cachedIdentity(request.cacheScope!);
  if (!identity || identity.owner !== owner) throw new OfflineUnavailableError('离线工作区已过期，请联网重新登录。');
  const cached = await readResponse(request.cacheScope!, owner, request.url!, request.params);
  if (!cached) throw new OfflineUnavailableError();
  if (request.sessionEpoch !== epoch || owner !== currentQueueOwner()) throw new OfflineUnavailableError('登录身份已变化，请重新登录。');
  connectionState.cacheReadAt = Math.max(connectionState.cacheReadAt, cached.savedAt);
  return { data: projectPending(cached.data, request.url!, await readableOperations(owner)), status: 200, statusText: 'Local cache', headers: { 'x-plan-local-cache': String(cached.savedAt) }, config: request };
}
async function readableOperations(owner: string): Promise<OfflineOperation[]> {
  try { return await listOperations(owner); }
  catch { connectionState.cacheAvailable = false; return []; }
}

function clone(value: unknown): unknown {
  if (value === undefined || value === null) return value;
  if (typeof value === 'string') {
    try { return JSON.parse(value); } catch { return value; }
  }
  return structuredClone(value);
}
