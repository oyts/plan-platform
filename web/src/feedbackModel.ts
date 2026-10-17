export const feedbackCategories = { BUG: '功能异常', CONNECTION: '连接与同步', USABILITY: '使用困惑', SUGGESTION: '改进建议', OTHER: '其他' } as const;
export const feedbackStatuses = { OPEN: '待处理', IN_PROGRESS: '处理中', NEEDS_INFO: '待补充', RESOLVED: '已解决', CLOSED: '已关闭' } as const;
export type FeedbackCategory = keyof typeof feedbackCategories;
export type FeedbackStatus = keyof typeof feedbackStatuses;
export type ErrorKind = 'NONE' | 'NETWORK_OR_TLS' | 'TLS' | 'AUTH' | 'PERMISSION' | 'VERSION' | 'BUSY' | 'VALIDATION' | 'CONFLICT' | 'SERVER' | 'UNKNOWN';
export interface SafeDiagnostic { schemaVersion: 1; clientVersion: string; serverVersion?: string; apiVersion?: number; platform: 'WEB' | 'WINDOWS' | 'ANDROID'; occurredAt: string; errorKind: ErrorKind; requestId?: string; queueCount: number; conflictCount: number }
export interface FeedbackForm { category: FeedbackCategory; title: string; steps: string; expectedResult: string; actualResult: string }
export interface FeedbackDraft extends FeedbackForm { operationKey: string; diagnostic: SafeDiagnostic | null; uncertain: boolean }
export interface FeedbackSummary { id: string; title: string; category: FeedbackCategory; status: FeedbackStatus; version: number; createdAt: string; updatedAt: string }
export interface FeedbackDetail { ticket: FeedbackSummary; steps: string; expectedResult: string; actualResult: string; diagnostic: SafeDiagnostic | null; messages: { id: string; authorKind: 'MEMBER' | 'ADMIN'; body: string; createdAt: string }[] }
export const uuidPattern = /^[a-f\d]{8}(-[a-f\d]{4}){3}-[a-f\d]{12}$/i;
const versionPattern = /^\d{1,3}\.\d{1,3}\.\d{1,3}([+-][A-Za-z\d.-]{1,24})?$/;
export function newFeedback(): FeedbackDraft { return { operationKey: crypto.randomUUID(), category: 'BUG', title: '', steps: '', expectedResult: '', actualResult: '', diagnostic: null, uncertain: false }; }
export function classifyFeedbackError(status?: number): ErrorKind {
  if (!status) return 'NETWORK_OR_TLS';
  if (status === 401) return 'AUTH'; if (status === 403) return 'PERMISSION'; if (status === 409) return 'CONFLICT';
  if (status === 426) return 'VERSION'; if (status === 429 || status === 503) return 'BUSY';
  if (status >= 500) return 'SERVER'; if (status === 400 || status === 422) return 'VALIDATION'; return 'UNKNOWN';
}
// Only these scalar fields can cross the diagnostic boundary. Never spread an error, config or queue record.
export function safeDiagnostic(input: SafeDiagnostic): SafeDiagnostic {
  const kinds: ErrorKind[] = ['NONE','NETWORK_OR_TLS','TLS','AUTH','PERMISSION','VERSION','BUSY','VALIDATION','CONFLICT','SERVER','UNKNOWN'];
  if (!versionPattern.test(input.clientVersion) || !['WEB','WINDOWS','ANDROID'].includes(input.platform) || !kinds.includes(input.errorKind)
    || !Number.isFinite(Date.parse(input.occurredAt)) || !Number.isInteger(input.queueCount) || input.queueCount < 0 || input.queueCount > 100000
    || !Number.isInteger(input.conflictCount) || input.conflictCount < 0 || input.conflictCount > input.queueCount) throw new Error('诊断摘要无效。');
  const result: SafeDiagnostic = { schemaVersion: 1, clientVersion: input.clientVersion, platform: input.platform, occurredAt: new Date(input.occurredAt).toISOString(), errorKind: input.errorKind, queueCount: input.queueCount, conflictCount: input.conflictCount };
  if (input.serverVersion && versionPattern.test(input.serverVersion)) result.serverVersion = input.serverVersion;
  if (Number.isInteger(input.apiVersion) && input.apiVersion! > 0 && input.apiVersion! <= 100) result.apiVersion = input.apiVersion;
  if (input.requestId && uuidPattern.test(input.requestId)) result.requestId = input.requestId;
  return result;
}
export function parseFeedbackDraft(value: unknown): FeedbackDraft {
  const row = value as FeedbackDraft;
  if (!row || !uuidPattern.test(row.operationKey) || !Object.prototype.hasOwnProperty.call(feedbackCategories,row.category) || typeof row.uncertain !== 'boolean'
    || !['title','steps','expectedResult','actualResult'].every(key => typeof row[key as keyof FeedbackForm] === 'string')
    || row.title.length > 120 || row.steps.length > 4000 || row.expectedResult.length > 2000 || row.actualResult.length > 2000) throw new Error('本机反馈草稿格式无效，请勿清理存储，可先复制仍可读取的内容。');
  return { operationKey: row.operationKey, category: row.category, title: row.title, steps: row.steps, expectedResult: row.expectedResult, actualResult: row.actualResult, uncertain: row.uncertain, diagnostic: row.diagnostic ? safeDiagnostic(row.diagnostic) : null };
}
