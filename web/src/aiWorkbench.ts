import type { AiDraft, AiMode } from '@/aiPlanning';
import { readPrivateDraft, writePrivateDraft, deletePrivateDraft } from '@/privateDrafts';
export interface WorkspaceContent { schemaVersion: 1; mode: AiMode | null; message: string; importJson: string; draft: AiDraft | null; sourceSessionId: string | null; sourceVersion: number | null }
export interface LocalWorkspace { id: string; context: string; name: string; content: WorkspaceContent; updatedAt: string; cloudId: string | null; cloudVersion: number }
export interface SavedWorkspace { id: string; name: string; content: WorkspaceContent; version: number; updatedAt: string; processing: boolean; adjustment: boolean }
export type WorkspaceSummary = Omit<SavedWorkspace, 'content'>;
export function parseWorkspace(value: unknown): WorkspaceContent {
  const c = value as WorkspaceContent;
  if (!c || c.schemaVersion !== 1 || ![null, 'PLAN', 'ROUTE', 'TODO'].includes(c.mode) || typeof c.message !== 'string' || c.message.length > 8000 || typeof c.importJson !== 'string' || c.importJson.length > 128000 || !(c.sourceSessionId === null || typeof c.sourceSessionId === 'string') || !(c.sourceVersion === null || Number.isSafeInteger(c.sourceVersion) && c.sourceVersion >= 0) || !(c.draft === null || typeof c.draft === 'object' && Array.isArray(c.draft.items) && c.draft.items.length <= 100 && Array.isArray(c.draft.stages))) throw new Error('工作区草稿格式无效，原保存内容未被覆盖。');
  if(c.draft && (!['PLAN','ROUTE','TODO'].includes(c.draft.mode)||typeof c.draft.title!=='string'||!Array.isArray(c.draft.restDays)||c.draft.stages.length>30||!c.draft.stages.every(s=>s&&typeof s.localId==='string'&&typeof s.title==='string')||!c.draft.items.every(i=>i&&typeof i.localId==='string'&&typeof i.title==='string'&&typeof i.note==='string'&&Array.isArray(i.prerequisites)&&i.prerequisites.every(v=>typeof v==='string'))))throw new Error('草稿步骤格式不完整，原保存内容未被覆盖。');
  // Explicit fields only: never persist consent, confirm/apply flags, operation keys, tokens or turns.
  const result: WorkspaceContent = { schemaVersion: 1, mode: c.mode, message: c.message, importJson: c.importJson, draft: c.draft, sourceSessionId: c.sourceSessionId, sourceVersion: c.sourceVersion };
  if (new TextEncoder().encode(JSON.stringify(result)).length > 245760) throw new Error('单份草稿最多 240 KiB，请缩短 JSON 或说明。');
  return structuredClone(JSON.parse(JSON.stringify(result)));
}
export class WorkspaceStore {
  private chain: Promise<unknown> = Promise.resolve();
  constructor(private scope: string, private owner: string) {}
  private serial<T>(fn: () => Promise<T>): Promise<T> { const next = this.chain.then(fn, fn); this.chain = next.catch(() => undefined); return next; }
  list(): Promise<LocalWorkspace[]> { return this.serial(async () => {
    const ids = await readPrivateDraft<string[]>(this.scope, this.owner, 'ai-workbench:index') ?? [];
    const entries = await Promise.all(ids.slice(0, 30).map(id => readPrivateDraft<LocalWorkspace>(this.scope, this.owner, `ai-workbench:${id}`)));
    return entries.filter((v): v is LocalWorkspace => !!v).sort((a,b) => b.updatedAt.localeCompare(a.updatedAt));
  }); }
  save(value: LocalWorkspace): Promise<void> { return this.serial(async () => {
    const content = parseWorkspace(value.content);
    const ids = await readPrivateDraft<string[]>(this.scope, this.owner, 'ai-workbench:index') ?? [];
    if (!ids.includes(value.id) && ids.length >= 30) throw new Error('本机最多保存 30 份 AI 草稿，请先导出或删除。');
    // Index first: a failed content write leaves a harmless empty index entry, never an unlisted private draft.
    await writePrivateDraft(this.scope, this.owner, 'ai-workbench:index', [...new Set([value.id, ...ids])]);
    await writePrivateDraft(this.scope, this.owner, `ai-workbench:${value.id}`, { ...value, content });
  }); }
  delete(ids: string[]): Promise<void> { return this.serial(async () => {
    const old = await readPrivateDraft<string[]>(this.scope, this.owner, 'ai-workbench:index') ?? [];
    for (const id of ids) await deletePrivateDraft(this.scope, this.owner, `ai-workbench:${id}`);
    await writePrivateDraft(this.scope, this.owner, 'ai-workbench:index', old.filter(id => !ids.includes(id)));
  }); }
}
/** A generation barrier prevents a queued debounce from recreating a deleted draft. */
export class WorkspaceAutosave {
  private timer?: ReturnType<typeof setTimeout>; private epoch = 0; private running: Promise<unknown> = Promise.resolve();
  schedule(save: () => Promise<void>, done: (error?: unknown) => void) {
    clearTimeout(this.timer); const current = ++this.epoch;
    this.timer = setTimeout(() => { this.running = this.running.catch(() => undefined).then(async () => { if (current !== this.epoch) return; try { await save(); if (current === this.epoch) done(); } catch (e) { if (current === this.epoch) done(e); } }); }, 450);
  }
  async cancel() { ++this.epoch; clearTimeout(this.timer); await this.running.catch(() => undefined); }
}
export async function exportWorkspace(name: string, content: WorkspaceContent) {
  const blob = new Blob([JSON.stringify({ format: 'plan-cabin-ai-draft', schemaVersion: 1, name, exportedAt: new Date().toISOString(), content: parseWorkspace(content) }, null, 2)], { type: 'application/json' });
  const {downloadFile}=await import('@/download');await downloadFile(blob,'plan-cabin-ai-draft.json');
}
export const modeExamples: Record<AiMode, { purpose: string; example: string }> = {
  PLAN: { purpose: '有明确周期，按日期和休息日安排。', example: '考试复习 → 第一周基础 → 周一练习、周二回顾' },
  ROUTE: { purpose: '长期目标，空闲时推进，不设结束日期。', example: '学习吉他 → 乐理基础 → 单音 → 和弦 → 曲目' },
  TODO: { purpose: '当天或近期的独立小事，不需要长期规划。', example: '今天 → 买牛奶、回复邮件、整理桌面' },
};
