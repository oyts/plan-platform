export type LessonId = 'plan' | 'route' | 'todo' | 'sync';
export const lessons = [
  { id: 'plan', title: '周期计划', summary: '有开始和结束日期，按天安排任务。', example: '七天阅读练习', destination: '/plans', steps: ['设置周期', '选择休息日', '安排任务', '完成任务'], minutes: 3, usage: ['在“周期计划”模式打开“计划”，填写“新建计划”并保存。', '进入自己的计划，在任务页添加任务；在“日程”按日期查看。', '需要调整时在计划设置修改日期或每周休息日，已有任务不会被删除。'] },
  { id: 'route', title: '目标路线', summary: '没有固定结束日期，有空就推进一个步骤。', example: '从零学习吉他', destination: '/mainlines', steps: ['写下目标', '划分阶段', '设置先后顺序', '推进一步'], minutes: 3, usage: ['切换到“目标路线”模式，在“路线”创建自己的目标。', '用“阶段”分组，再添加步骤，写下完成这个步骤的验收标准。', '按需设置前置依赖；有空再推进，不用填每日日期。'] },
  { id: 'todo', title: '今日待办', summary: '临时想到的一件事，不用先建计划。', example: '寄出一份资料', destination: '/todos', steps: ['记录一件事', '补充备注', '标记完成', '重新安排'], minutes: 2, usage: ['切换到“今日待办”，用“快速记录”记一件事，或点“添加待办”。', '勾选完成；用“状态”筛选已完成或全部事项。', '点击“编辑”修改安排日期，切换“查看日期”查看改期后的待办。'] },
  { id: 'sync', title: '离线与同步', summary: '分清本地保存、云端同步与冲突处理。', example: '两台设备修改同一任务', destination: '/sync', steps: ['准备离线', '保存到本地', '识别冲突', '选择保留版本'], minutes: 2, usage: ['先联网加载需要的页面；离线后，只能使用已缓存的内容。', '联网后打开“同步”，先检查本地操作和待处理冲突。', '比较冲突内容并选择保留版本，再“同步等待项”；不要用这个练习替代真实队列核对。'] },
] as const;
export function isLesson(value: unknown): value is LessonId { return lessons.some(item => item.id === value); }
export function lessonForPath(path: string): LessonId | null {
  if (path === '/' || path === '/schedule' || /^\/plans(?:\/|$)/.test(path)) return 'plan';
  if (['/routes', '/mainlines', '/route-progress'].includes(path)) return 'route';
  if (path === '/todos') return 'todo';
  return path === '/sync' ? 'sync' : null;
}
export interface Practice {
  name: string; start: string; end: string; rest: number[]; task: string; taskDate: string;
  stage: string; prerequisite: boolean; done: boolean; note: string; rescheduled: boolean;
  cached: boolean; queued: boolean; resolution: string;
}
export interface LessonProgress { step: number; completed: boolean; dismissed: boolean; practice: Practice }
export type LearningProgress = Record<LessonId, LessonProgress>;
export function freshPractice(): Practice { return { name: '', start: '', end: '', rest: [], task: '', taskDate: '', stage: '', prerequisite: false, done: false, note: '', rescheduled: false, cached: false, queued: false, resolution: '' }; }
export function freshProgress(): LearningProgress { return Object.fromEntries(lessons.map(item => [item.id, { step: 0, completed: false, dismissed: false, practice: freshPractice() }])) as LearningProgress; }
export function learningKey(server: string, owner: string): string { return `learning:v1:${encodeURIComponent(server)}:${encodeURIComponent(owner)}`; }
function validDate(value: string): boolean { const date = new Date(`${value}T12:00:00Z`); return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value; }
export function stepReady(id: LessonId, step: number, practice: Practice): boolean {
  const p = practice;
  if (id === 'plan') {
    if (step === 0) return !!p.name.trim() && validDate(p.start) && validDate(p.end) && p.end >= p.start && (Date.parse(p.end) - Date.parse(p.start)) / 86400000 <= 30;
    if (step === 1) return p.rest.length > 0 && p.rest.length < 7;
    if (step === 2) return !!p.task.trim() && validDate(p.taskDate) && p.taskDate >= p.start && p.taskDate <= p.end && !p.rest.includes(new Date(`${p.taskDate}T12:00:00Z`).getUTCDay() || 7);
    return p.done;
  }
  if (id === 'route') return [!!p.name.trim(), !!p.stage.trim(), p.prerequisite, p.done][step] ?? false;
  if (id === 'todo') return [!!p.task.trim(), !!p.note.trim(), p.done, p.rescheduled][step] ?? false;
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
        for (const key of ['name', 'start', 'end', 'task', 'taskDate', 'stage', 'note', 'resolution'] as const) if (typeof draft[key] === 'string') p.practice[key] = draft[key].slice(0, 180);
        for (const key of ['prerequisite', 'done', 'rescheduled', 'cached', 'queued'] as const) p.practice[key] = draft[key] === true;
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
