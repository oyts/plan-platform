import type { Mainline, MainlineItem } from './types';
export function nextRouteItem(line: Mainline): MainlineItem | undefined {
  const available = [...line.items].filter(item => item.unlocked && item.status !== 'COMPLETED').sort((a, b) => a.sortOrder - b.sortOrder);
  return available.find(item => item.status === 'RUNNING') ?? available.find(item => item.status === 'PAUSED') ?? available[0];
}
export function routeSummary(lines: Mainline[]) {
  const items = lines.flatMap(line => line.items);
  return { routes: lines.length, completed: items.filter(item => item.status === 'COMPLETED').length, total: items.length, seconds: items.reduce((sum, item) => sum + Math.max(0, item.accumulatedSeconds), 0) };
}
export const routeDuration = (seconds: number) => `${Math.floor(seconds / 3600)} 小时 ${Math.floor(seconds % 3600 / 60)} 分`;
export function routeStatus(status: string) { return ({ PENDING: '待开始', RUNNING: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', PARTIAL: '部分完成', SKIPPED: '已跳过', DEFERRED: '已延期' } as Record<string, string>)[status] ?? status; }
