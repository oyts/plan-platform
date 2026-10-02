export const WEEKDAYS = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
export function restDaySummary(days: readonly number[] | undefined): string {
  if (days === undefined) return '休息日配置需升级服务器后确认';
  return days.length ? `每周 ${[...days].sort((a, b) => a - b).map(day => WEEKDAYS[day - 1]).join('、')} 休息` : '未设置固定休息日';
}
export function isPlanRestDay(date: string, days: readonly number[] | undefined): boolean {
  const weekday = new Date(`${date}T00:00:00`).getDay() || 7;
  return days?.includes(weekday) ?? false;
}
