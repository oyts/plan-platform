import type { AiAdjustment } from './aiPlanning';
export interface PacingPrompt { id: string; label: string; message: string }
function plusWeek(date: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
  const value = new Date(date + 'T12:00:00Z');
  if (!Number.isFinite(value.getTime()) || value.toISOString().slice(0,10) !== date) return null;
  value.setUTCDate(value.getUTCDate() + 7);
  const result = value.toISOString().slice(0,10);
  return /^\d{4}-\d{2}-\d{2}$/.test(result) ? result : null;
}
export function pacingPrompts(adjustment: AiAdjustment): PacingPrompt[] {
  const base = adjustment.original;
  if (base.items.length && base.items.every(item => adjustment.protectedItems.includes(item.localId))) return [];
  const keep = '保留所有原事项标识、顺序和未要求修改的内容，不删除或合并事项，不改写已完成事项及执行历史。请先返回完整建议草稿，由我核对后确认应用。';
  if (base.mode === 'PLAN') {
    const end = base.endDate && plusWeek(base.endDate);
    return [
      ...(end ? [{ id: 'extend', label: '周期延长一周', message: `最近比较忙，请保留原起点，把计划结束日期延长到${end}；仅把未开始事项分散到新的周期范围内，保持原休息日且避开休息日排期。${keep}` }] : []),
      { id: 'shorten', label: '每项缩短到15分钟', message: `请仅把未开始事项的预计时长调整为15分钟，按这个时长调整任务说明，不改日期、周期或休息日。${keep}` },
    ];
  }
  if (base.mode === 'ROUTE') return [{ id: 'route', label: '每次只练10分钟', message: `最近空闲较少，请仅把未开始步骤调整为每次10分钟，并相应缩减练习说明；保留原阶段、前置依赖，不强制按天安排。${keep}` }];
  return [{ id: 'todo', label: '把这件事写得更明确', message: `请把这条未完成待办的标题和备注写得更明确、便于着手执行；保留原日期，只修改这一条，不追加别的待办。${keep}` }];
}
