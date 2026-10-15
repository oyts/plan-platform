export interface AiPolicy { dailyLimit: number; version: number }
export interface AiUsage { date: string; resetsAt: string; dailyLimit: number; overrideLimit: number | null; paused: boolean; used: number; pending: number; remaining: number; version: number }
export interface AiMember { id: string; displayName: string; email: string; role: string; quota: AiUsage }
export interface AiPage<T> { items: T[]; page: number; size: number; total: number }
export interface AiAudit { actor: string; target: string | null; changeType: string; beforeLimit: number | null; afterLimit: number | null; beforePaused: boolean; afterPaused: boolean; createdAt: string }
export function quotaLimitValid(value: unknown): value is number { return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10000; }
export function quotaRule(value: number | null) { return value === null ? '使用默认额度' : `${value} 次/天`; }
