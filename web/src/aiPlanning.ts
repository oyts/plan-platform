export type AiMode = 'PLAN' | 'ROUTE' | 'TODO';
export const aiModeNames: Record<AiMode, string> = { PLAN: '周期计划', ROUTE: '目标路线', TODO: '今日待办' };
export interface AiItem { localId: string; title: string; note: string; dueDate: string | null; plannedMinutes: number; stageId: string | null; prerequisites: string[] }
export interface AiDraft { schemaVersion: 1; mode: AiMode; revision: number; title: string; startDate: string | null; endDate: string | null; restDays: number[]; stages: { localId: string; title: string }[]; items: AiItem[] }
export interface AiSession { id: string; mode: AiMode | null; recommendedMode: AiMode | null; version: number; draft: AiDraft | null; applied: { mode: AiMode; path: string; count: number } | null; expiresAt: string; turns: { id: string; state: string; message: string; reply: string | null; error: string | null; createdAt: string }[] }
export interface AiSummary { id: string; mode: AiMode | null; version: number; createdAt: string; expiresAt: string; applied: boolean }
export function aiPending(session: AiSession | null): boolean { return session?.turns.some(t => t.state === 'QUEUED' || t.state === 'RUNNING') ?? false; }
export function aiState(state: string): string { return ({ QUEUED: '等待处理', RUNNING: '正在生成', SUCCEEDED: '已回复', FAILED: '请求失败，额度已退回', UNKNOWN: '结果待核实，额度暂时占用' } as Record<string, string>)[state] ?? '状态未知'; }
export function aiInitialMode(query: unknown): AiMode | null { return typeof query === 'string' && ['PLAN', 'ROUTE', 'TODO'].includes(query.toUpperCase()) ? query.toUpperCase() as AiMode : null; }
export function aiAppliedPath(path: unknown): string | null { return typeof path === 'string' && (/^\/plans\/[a-f0-9-]{36}$/.test(path) || /^\/mainlines\?line=[a-f0-9-]{36}$/.test(path) || path === '/todos') ? path : null; }
export function aiExternalPrompt(mode: AiMode): string {
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' });
  const sample: AiDraft = { schemaVersion: 1, mode, revision: 0, title: '请替换为规划名称', startDate: mode === 'PLAN' ? today : null, endDate: mode === 'PLAN' ? today : null, restDays: [], stages: [], items: [{ localId: 'step1', title: '请替换为具体事项', note: '请替换为执行说明', dueDate: mode === 'ROUTE' ? null : today, plannedMinutes: 30, stageId: null, prerequisites: [] }] };
  return `请先和我讨论目标及限制，确认信息后生成计划舱的${aiModeNames[mode]}草稿。只输出以下结构的完整 JSON，不要代码围栏或额外字段。下面是格式示例，不是我的真实计划：\n${JSON.stringify(sample, null, 2)}\n规则：标题最多120字，说明最多2000字；最多100项，首次建议不超过12项；localId仅英文数字下划线连字符且唯一。PLAN必须确认起止日期与每周休息日（ISO星期1至7），任务日期在范围内且不在休息日，stages为空。ROUTE无日期及休息日，可有阶段，前置依赖只引用现有步骤且不能成环。TODO每项必须确认日期，无阶段、周期范围或依赖。非ROUTE的stageId为null且prerequisites为空。每项预计时长为1至1440整数，TODO可为0。所有示例字段必须保留，空值用null或[]。不要声称已创建真实计划。`;
}
