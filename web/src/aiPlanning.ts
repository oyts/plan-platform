export type AiMode = 'PLAN' | 'ROUTE' | 'TODO';
export const aiModeNames: Record<AiMode, string> = { PLAN: '周期计划', ROUTE: '目标路线', TODO: '今日待办' };
// Native auth restores after the router mounts so offline connection settings
// remain reachable. Preserve only a validated AI deep link across that redirect.
export function aiNativeResumePath(hash: string): string | null {
  if (!hash.startsWith('#/ai')) return null;
  try {
    const url = new URL(hash.slice(1), 'https://native.invalid');
    if (url.origin !== 'https://native.invalid' || url.pathname !== '/ai' || url.hash) return null;
    for (const key of url.searchParams.keys()) if (!['session', 'mode', 'target'].includes(key) || url.searchParams.getAll(key).length !== 1) return null;
    for (const key of ['session', 'target']) if (url.searchParams.has(key) && !/^[a-f0-9-]{36}$/.test(url.searchParams.get(key)!)) return null;
    if (url.searchParams.has('mode') && !['PLAN', 'ROUTE', 'TODO'].includes(url.searchParams.get('mode')!)) return null;
    if (url.searchParams.has('target') && !url.searchParams.has('mode')) return null;
    return '/ai' + url.search;
  } catch { return null; }
}
export interface AiItem { localId: string; title: string; note: string; dueDate: string | null; plannedMinutes: number; stageId: string | null; prerequisites: string[] }
export interface AiDraft { schemaVersion: 1; mode: AiMode; revision: number; title: string; startDate: string | null; endDate: string | null; restDays: number[]; stages: { localId: string; title: string }[]; items: AiItem[] }
export interface AiAdjustment { original: AiDraft; protectedItems: string[]; path: string }
export interface AiSession { id: string; mode: AiMode | null; recommendedMode: AiMode | null; version: number; draft: AiDraft | null; applied: { mode: AiMode; path: string; count: number } | null; expiresAt: string; turns: { id: string; state: string; message: string; reply: string | null; error: string | null; createdAt: string }[]; adjustment?: AiAdjustment | null }
export interface AiSummary { id: string; mode: AiMode | null; version: number; createdAt: string; expiresAt: string; applied: boolean; adjustment?: boolean; processing?: boolean }
export interface AiDifference { id: string; title: string; kind: 'ADD' | 'MODIFY'; fields: { label: string; before: string; after: string }[] }
export function aiDifferences(original: AiDraft, proposed: AiDraft): AiDifference[] {
  const result: AiDifference[] = [];
  const display = (value: unknown) => value === null || value === '' || value === undefined ? '未设置' : Array.isArray(value) ? value.join('、') || '无' : String(value);
  const fields = (pairs: [string, unknown, unknown][]) => pairs.filter(([, a, b]) => JSON.stringify(a) !== JSON.stringify(b)).map(([label, a, b]) => ({ label, before: display(a), after: display(b) }));
  const metadata = fields([['名称', original.title, proposed.title], ['开始日期', original.startDate, proposed.startDate], ['结束日期', original.endDate, proposed.endDate], ['每周休息日（1 为周一，7 为周日）', original.restDays, proposed.restDays]]);
  if (metadata.length) result.push({ id: 'metadata', title: '规划设置', kind: 'MODIFY', fields: metadata });
  for (const stage of proposed.stages) {
    const old = original.stages.find(s => s.localId === stage.localId);
    if (!old || old.title !== stage.title) result.push({ id: `stage:${stage.localId}`, title: '路线阶段', kind: old ? 'MODIFY' : 'ADD', fields: [{ label: '阶段名称', before: old?.title ?? '尚未创建', after: stage.title }] });
  }
  for (const item of proposed.items) {
    const old = original.items.find(i => i.localId === item.localId);
    const stage = (draft: AiDraft, id: string | null) => draft.stages.find(s => s.localId === id)?.title ?? '不分阶段';
    const dependencies = (draft: AiDraft, ids: string[]) => ids.map(id => draft.items.find(i => i.localId === id)?.title ?? '无效步骤');
    const changes = old ? fields([['标题', old.title, item.title], ['说明', old.note, item.note], ['日期', old.dueDate, item.dueDate], ['预计分钟', old.plannedMinutes, item.plannedMinutes], ['阶段', stage(original, old.stageId), stage(proposed, item.stageId)], ['前置步骤', dependencies(original, old.prerequisites), dependencies(proposed, item.prerequisites)]]) : [{ label: '事项内容', before: '尚未创建', after: [item.title, item.note, item.dueDate ?? '自由推进', `${item.plannedMinutes} 分钟`, stage(proposed, item.stageId), ...dependencies(proposed, item.prerequisites)].filter(Boolean).join(' · ') }];
    if (changes.length) result.push({ id: `item:${item.localId}`, title: item.title || '未填写标题', kind: old ? 'MODIFY' : 'ADD', fields: changes });
  }
  return result;
}
export function aiPending(session: AiSession | null): boolean { return session?.turns.some(t => t.state === 'QUEUED' || t.state === 'RUNNING') ?? false; }
export function aiState(state: string): string { return ({ QUEUED: '等待处理', RUNNING: '正在生成', SUCCEEDED: '已回复', FAILED: '请求失败，额度已退回', UNKNOWN: '结果待核实，额度暂时占用' } as Record<string, string>)[state] ?? '状态未知'; }
export function aiInitialMode(query: unknown): AiMode | null { return typeof query === 'string' && ['PLAN', 'ROUTE', 'TODO'].includes(query.toUpperCase()) ? query.toUpperCase() as AiMode : null; }
export function aiAppliedPath(path: unknown): string | null { return typeof path === 'string' && (/^\/plans\/[a-f0-9-]{36}$/.test(path) || /^\/mainlines\?line=[a-f0-9-]{36}$/.test(path) || path === '/todos') ? path : null; }
export function aiExternalPrompt(mode: AiMode): string {
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' });
  const sample: AiDraft = { schemaVersion: 1, mode, revision: 0, title: '请替换为规划名称', startDate: mode === 'PLAN' ? today : null, endDate: mode === 'PLAN' ? today : null, restDays: [], stages: [], items: [{ localId: 'step1', title: '请替换为具体事项', note: '请替换为执行说明', dueDate: mode === 'ROUTE' ? null : today, plannedMinutes: 30, stageId: null, prerequisites: [] }] };
  return `请先和我讨论目标及限制，确认信息后生成计划舱的${aiModeNames[mode]}草稿。只输出以下结构的完整 JSON，不要代码围栏或额外字段。下面是格式示例，不是我的真实计划：\n${JSON.stringify(sample, null, 2)}\n规则：标题最多120字，说明最多2000字；最多100项，首次建议不超过12项；localId仅英文数字下划线连字符且唯一。PLAN必须确认起止日期与每周休息日（ISO星期1至7），任务日期在范围内且不在休息日，stages为空。ROUTE无日期及休息日，可有阶段，前置依赖只引用现有步骤且不能成环。TODO每项必须确认日期，无阶段、周期范围或依赖。非ROUTE的stageId为null且prerequisites为空。每项预计时长为1至1440整数，TODO可为0。所有示例字段必须保留，空值用null或[]。不要声称已创建真实计划。`;
}
export function aiExternalAdjustmentPrompt(adjustment: AiAdjustment, draft: AiDraft): string {
  return `请与我讨论这份${aiModeNames[draft.mode]}的调整需求，最后只返回完整 schemaVersion 1 JSON 草稿，不要代码围栏或执行任何操作。以下是我明确选择的对象，不代表其他计划。\n必须保留所有原事项和阶段的 localId，保留原事项相对顺序，不得删除或合并。受保护事项的所有字段保持原样；原计划起点不变，结束日期只能延长；新任务和调整排期不能在休息日。可追加新事项/阶段，不能改完成状态、历史或计时，不能声称已应用。\n受保护标识：${JSON.stringify(adjustment.protectedItems)}\n当前完整草稿：\n${JSON.stringify(draft, null, 2)}\n只修改我们明确讨论的部分，其他内容原样保留；待我在计划舱逐项核对并确认后再应用。`;
}
