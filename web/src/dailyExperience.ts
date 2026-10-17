import type { PageResponse } from '@/types';
export interface DailyCounts { today: number; overdue: number; upcoming: number }
export interface DailySummary { date: string; through: string; plan: DailyCounts; todo: DailyCounts; activeRoutes: number }
export interface DailyItem { kind: 'PLAN' | 'TODO'; id: string; parentId: string; title: string; parentName: string; dueDate: string; status: string }
export interface DailyRoute { id: string; name: string; total: number; completed: number }
export interface Reminders { enabled: boolean; includePlans: boolean; includeTodos: boolean; showOverdue: boolean; lookAheadDays: number; version: number | null }
export interface WeeklyReview { weekStart: string; wins: string; blockers: string; nextFocus: string; version: number | null; updatedAt: string | null }
export interface WeekSummary { start: string; end: string; calculatedAt: string; planScheduled: number; planCompleted: number; todoScheduled: number; todoCompleted: number; routeCompleted: number; recordedPlanSeconds: number; openPlansThroughEnd: number; openTodosThroughEnd: number; legacyCompletedTodosUntimed: number }
export type ItemPage = PageResponse<DailyItem>;
export const reminderDefaults = (): Reminders => ({enabled:false,includePlans:true,includeTodos:true,showOverdue:true,lookAheadDays:3,version:null});
export function mondayOf(value: string): string {
  const date = new Date(`${value}T12:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(date.getTime()) || date.toISOString().slice(0,10)!==value) throw new Error('请选择有效日期。');
  date.setUTCDate(date.getUTCDate() - (date.getUTCDay()+6)%7);
  return date.toISOString().slice(0,10);
}
export function reminderCount(summary: DailySummary, settings: Reminders): number {
  if (!settings.enabled) return 0;
  return ([settings.includePlans ? summary.plan : null, settings.includeTodos ? summary.todo : null])
    .reduce((total, row) => total + (row ? row.today+row.upcoming+(settings.showOverdue ? row.overdue : 0) : 0),0);
}
export function dailyItemDestination(item: DailyItem) {
  return item.kind==='PLAN' ? {path:'/schedule',query:{plan:item.parentId,date:item.dueDate,task:item.id}}
    : {path:'/todos',query:{date:item.dueDate,focus:item.id}};
}
export function recordedTime(seconds: number) { const minutes = Math.floor(Math.abs(seconds)/60); return `${seconds<0?'-':''}${Math.floor(minutes/60)} 小时 ${minutes%60} 分钟`; }
export function reminderDismissKey(scope: string, owner: string) { return `plan-reminder-dismiss:${encodeURIComponent(scope)}:${encodeURIComponent(owner)}`; }
