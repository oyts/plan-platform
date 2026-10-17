import { classifyFeedbackError, uuidPattern, type ErrorKind } from './feedbackModel';
interface SafeFailure { errorKind: ErrorKind; occurredAt: string; requestId?: string }
let recent: { key: string; failure: SafeFailure } | null = null;
let server: { scope: string; serverVersion: string; apiVersion: number } | null = null;
export function rememberFeedbackServer(scope: string, data: unknown): void {
  const value=data as {serverVersion?:unknown;apiVersion?:unknown}|null;
  if(value&&typeof value.serverVersion==='string'&&/^\d{1,3}\.\d{1,3}\.\d{1,3}([+-][A-Za-z\d.-]{1,24})?$/.test(value.serverVersion)&&typeof value.apiVersion==='number'&&Number.isInteger(value.apiVersion)&&value.apiVersion>0&&value.apiVersion<=100)
    server={scope,serverVersion:value.serverVersion,apiVersion:value.apiVersion};
}
export function feedbackServer(scope:string): {serverVersion?:string;apiVersion?:number} { return server?.scope===scope?{serverVersion:server.serverVersion,apiVersion:server.apiVersion}:{}; }
export function rememberFeedbackFailure(key: string, status?: number, requestId?: unknown): void {
  recent = { key, failure: { errorKind: classifyFeedbackError(status), occurredAt: new Date().toISOString(), ...(typeof requestId === 'string' && uuidPattern.test(requestId) ? { requestId } : {}) } };
}
export function feedbackFailure(key: string): SafeFailure { return recent?.key === key ? { ...recent.failure } : { errorKind: 'NONE', occurredAt: new Date().toISOString() }; }
export function clearFeedbackFailure(): void { recent = null; }
