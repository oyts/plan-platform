export type LessonId = 'plan' | 'route' | 'todo' | 'sync' | 'ai' | 'template';
export const lessons = [
  { id: 'plan', title: '周期计划', summary: '有开始和结束日期，按天安排任务。', example: '七天阅读练习', destination: '/plans', steps: ['设置周期', '选择休息日', '安排任务', '完成任务'], minutes: 3, usage: ['在“周期计划”模式打开“计划”，填写“新建计划”并保存。', '进入自己的计划，在任务页添加任务；在“日程”按日期查看。', '需要调整时在计划设置修改日期或每周休息日，已有任务不会被删除。'] },
  { id: 'route', title: '目标路线', summary: '没有固定结束日期，有空就推进一个步骤。', example: '从零学习吉他', destination: '/mainlines', steps: ['写下目标', '划分阶段', '设置先后顺序', '推进一步'], minutes: 3, usage: ['切换到“目标路线”模式，在“路线”创建自己的目标。', '用“阶段”分组，再添加步骤，写下完成这个步骤的验收标准。', '按需设置前置依赖；有空再推进，不用填每日日期。'] },
  { id: 'todo', title: '今日待办', summary: '临时想到的一件事，不用先建计划。', example: '寄出一份资料', destination: '/todos', steps: ['记录一件事', '补充备注', '标记完成', '重新安排'], minutes: 2, usage: ['切换到“今日待办”，用“快速记录”记一件事，或点“添加待办”。', '勾选完成；用“状态”筛选已完成或全部事项。', '点击“编辑”修改安排日期，切换“查看日期”查看改期后的待办。'] },
  { id: 'sync', title: '离线与同步', summary: '分清本地保存、云端同步与冲突处理。', example: '两台设备修改同一任务', destination: '/sync', steps: ['准备离线', '保存到本地', '识别冲突', '选择保留版本'], minutes: 2, usage: ['先联网加载需要的页面；离线后，只能使用已缓存的内容。', '联网后打开“同步”，先检查本地操作和待处理冲突。', '比较冲突内容并选择保留版本，再“同步等待项”；不要用这个练习替代真实队列核对。'] },
  { id: 'ai', title: 'AI 规划', summary: '用自己的话说目标，核对草稿后再生成；忙起来也能调整节奏。', example: '两周英语复习', destination: '/ai', steps: ['说明目标与选模式', '预览并手改草稿', '确认后才生成', '放慢已有计划'], minutes: 3, usage: ['在“AI 规划”说明目标、可用时间和期限；同意说明后发送，每次成功回复计 1 次。AI 推荐模式后仍由你选择。', '核对草稿，也可手工修改或继续对话；先保存修改，再勾选确认生成。', '调整已有内容时，从具体计划、路线或待办的“AI 调整”进入；同意载入后说明节奏要求，核对差异后确认应用。已完成事项受保护，本地有未同步操作时先处理队列。'] },
  { id: 'template', title: '整套模板', summary: '把可复用的方法留下，预览后建立独立副本。', example: '复用一周阅读方法', destination: '/templates', steps: ['选择结构来源', '预览并保存模板', '确认建立新副本', '验证互不影响'], minutes: 3, usage: ['在“更多 → 个人模板库”选择本人的周期计划、目标路线或已保存的完整AI新建草稿，填写模板资料。', '先检查结构，再勾选并确认保存；使用时重新命名，周期计划还要选择周期与休息日。', '模板只保存结构，不复制完成和计时历史。模板修改不影响已有副本；结果未知时原样重试，不要重新建立多份。'] },
] as const;
export function isLesson(value: unknown): value is LessonId { return lessons.some(item => item.id === value); }
export function lessonForPath(path: string): LessonId | null {
  if (path === '/' || path === '/schedule' || /^\/plans(?:\/|$)/.test(path)) return 'plan';
  if (['/routes', '/mainlines', '/route-progress'].includes(path)) return 'route';
  if (path === '/todos') return 'todo';
  if (path === '/ai') return 'ai';
  if (path === '/templates') return 'template';
  return path === '/sync' ? 'sync' : null;
}
export interface Practice {
  name: string; start: string; end: string; rest: number[]; task: string; taskDate: string;
  stage: string; prerequisite: boolean; done: boolean; note: string; rescheduled: boolean;
  cached: boolean; queued: boolean; resolution: string;
  aiConsent: boolean; aiMode: string; aiPreviewed: boolean; aiConfirmed: boolean; aiCreated: boolean; aiShifted: boolean;
  templateKind: string; templateReviewed: boolean; templateSaved: boolean; templateCopyName: string;
  templateCopyConfirmed: boolean; templateCopied: boolean; templateSourceChanged: boolean;
}
export interface LessonProgress { step: number; completed: boolean; dismissed: boolean; practice: Practice }
export type LearningProgress = Record<LessonId, LessonProgress>;
export function freshPractice(): Practice { return { name: '', start: '', end: '', rest: [], task: '', taskDate: '', stage: '', prerequisite: false, done: false, note: '', rescheduled: false, cached: false, queued: false, resolution: '', aiConsent: false, aiMode: '', aiPreviewed: false, aiConfirmed: false, aiCreated: false, aiShifted: false, templateKind:'',templateReviewed:false,templateSaved:false,templateCopyName:'',templateCopyConfirmed:false,templateCopied:false,templateSourceChanged:false }; }
export function freshProgress(): LearningProgress { return Object.fromEntries(lessons.map(item => [item.id, { step: 0, completed: false, dismissed: false, practice: freshPractice() }])) as LearningProgress; }
export function learningKey(server: string, owner: string): string { return `learning:v1:${encodeURIComponent(server)}:${encodeURIComponent(owner)}`; }
function validDate(value: string): boolean { const date = new Date(`${value}T12:00:00Z`); return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value; }
export const MAX_PRACTICE_DAYS = 31;
export function planPracticeValidation(p: Pick<Practice, 'name' | 'start' | 'end'>) {
  const errors: { name?: string; start?: string; end?: string } = {};
  const startValid = validDate(p.start), endValid = validDate(p.end);
  const latestEnd = startValid
    ? new Date(Math.min(Date.parse(`${p.start}T12:00:00Z`) + (MAX_PRACTICE_DAYS - 1) * 86400000, Date.parse('9999-12-31T12:00:00Z'))).toISOString().slice(0, 10)
    : null;
  const days = startValid && endValid && p.end >= p.start
    ? Math.round((Date.parse(`${p.end}T12:00:00Z`) - Date.parse(`${p.start}T12:00:00Z`)) / 86400000) + 1
    : null;
  if (!p.name.trim()) errors.name = '请填写练习计划名称。';
  if (!p.start) errors.start = '请选择练习开始日期。';
  else if (!startValid) errors.start = '请输入有效的练习开始日期。';
  if (!p.end) errors.end = '请选择练习结束日期。';
  else if (!endValid) errors.end = '请输入有效的练习结束日期。';
  else if (startValid && p.end < p.start) errors.end = '结束日期不能早于开始日期。';
  else if (days !== null && days > MAX_PRACTICE_DAYS) errors.end = `当前选择 ${days} 天，最多 ${MAX_PRACTICE_DAYS} 天（包含开始和结束当天）。请将结束日期调整至 ${latestEnd} 或之前。`;
  return { errors, days, latestEnd, ready: Object.keys(errors).length === 0, reason: Object.values(errors).join('') };
}
export function stepReady(id: LessonId, step: number, practice: Practice): boolean {
  const p = practice;
  if (id === 'template') return [!!p.name.trim()&&['PLAN','ROUTE'].includes(p.templateKind),p.templateReviewed&&p.templateSaved,p.templateCopied&&p.templateCopyConfirmed&&templateCopyValidation(p).ready,p.templateSourceChanged&&p.done][step]??false;
  if (id === 'plan') {
    if (step === 0) return planPracticeValidation(p).ready;
    if (step === 1) return p.rest.length > 0 && p.rest.length < 7;
    if (step === 2) return !!p.task.trim() && validDate(p.taskDate) && p.taskDate >= p.start && p.taskDate <= p.end && !p.rest.includes(new Date(`${p.taskDate}T12:00:00Z`).getUTCDay() || 7);
    return p.done;
  }
  if (id === 'route') return [!!p.name.trim(), !!p.stage.trim(), p.prerequisite, p.done][step] ?? false;
  if (id === 'todo') return [!!p.task.trim(), !!p.note.trim(), p.done, p.rescheduled][step] ?? false;
  if (id === 'ai') return [!!p.name.trim() && p.aiConsent && p.aiMode === 'PLAN', p.aiPreviewed && !!p.task.trim() && p.task.trim() !== '复习10个英语单词', p.aiConfirmed && p.aiCreated, p.aiShifted && p.resolution === 'apply'][step] ?? false;
  return [p.cached, p.queued, ['review', 'cloud', 'local'].includes(p.resolution), ['cloud', 'local'].includes(p.resolution)][step] ?? false;
}
export function readProgress(serialized?: string): LearningProgress {
  const result = freshProgress();
  try {
    const saved = JSON.parse(serialized ?? 'null');
    if (saved?.version !== 1) return result;
    for (const lesson of lessons) {
      const source = saved.progress?.[lesson.id]; if (!source) continue;
      const p = result[lesson.id];
      p.dismissed = source.dismissed === true;
      const draft = source.practice;
      if (draft && typeof draft === 'object') {
        for (const key of ['name', 'start', 'end', 'task', 'taskDate', 'stage', 'note', 'resolution','templateCopyName'] as const) if (typeof draft[key] === 'string') p.practice[key] = draft[key].slice(0, 180);
        for (const key of ['prerequisite', 'done', 'rescheduled', 'cached', 'queued', 'aiConsent', 'aiPreviewed', 'aiConfirmed', 'aiCreated', 'aiShifted','templateReviewed','templateSaved','templateCopyConfirmed','templateCopied','templateSourceChanged'] as const) p.practice[key] = draft[key] === true;
        if (['PLAN', 'ROUTE', 'TODO'].includes(draft.aiMode)) p.practice.aiMode = draft.aiMode;
        if (['PLAN','ROUTE'].includes(draft.templateKind)) p.practice.templateKind=draft.templateKind;
        if (Array.isArray(draft.rest)) p.practice.rest = [...new Set<number>(draft.rest.filter((day: unknown) => Number.isInteger(day) && Number(day) >= 1 && Number(day) <= 7))];
      }
      const requested = Number.isInteger(source.step) ? Math.max(0, Math.min(3, source.step)) : 0;
      // Corrupt or stale drafts cannot skip prerequisites.
      while (p.step < requested && stepReady(lesson.id, p.step, p.practice)) p.step++;
      p.completed = source.completed === true && p.step === 3 && stepReady(lesson.id, 3, p.practice);
    }
  } catch { /* A rejected preference must not prevent product use. */ }
  return result;
}
export function templateCopyValidation(p:Practice):{ready:boolean;reason:string}{
  if(!p.templateCopyName.trim())return{ready:false,reason:'请填写练习副本名称。'};
  if(p.templateKind==='ROUTE')return{ready:true,reason:''};
  if(p.templateKind!=='PLAN')return{ready:false,reason:'请先选择来源模式。'};
  const period=planPracticeValidation({name:p.templateCopyName,start:p.start,end:p.end});
  if(!period.ready)return{ready:false,reason:period.reason};
  if((period.days??0)<7)return{ready:false,reason:'这个练习结构需要容纳完整七天，请延长结束日期；不会丢弃后面的任务。'};
  return{ready:true,reason:''};
}
