import type { Todo } from './types';
import type { OfflineOperation } from './offlineQueue';

export function localDate(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export function shiftDate(value: string, days: number): string {
  const date = new Date(`${value}T12:00:00`); date.setDate(date.getDate() + days); return localDate(date);
}
export function projectTodos(data: unknown, operations: OfflineOperation[], params?: unknown): unknown {
  if (!Array.isArray(data)) return data;
  const rows = structuredClone(data) as Todo[];
  for (const op of operations) {
    if (!op.data || typeof op.data !== 'object') continue;
    const payload = op.data as Partial<Todo>;
    const item = rows.find(row => op.url === `/todos/${row.id}`);
    if (op.method === 'PUT' && item) {
      item.localState = op.status;
      if (op.status === 'PENDING') for (const key of ['title', 'note', 'dueDate', 'done', 'deleted'] as const) {
        if (key in payload) Object.assign(item, { [key]: payload[key] });
      }
    }
    if (op.method === 'POST' && op.url === '/todos' && op.status === 'PENDING') {
      rows.push({ id: `local:${op.id}`, title: payload.title?.trim() ?? '', note: payload.note?.trim() ?? '', dueDate: payload.dueDate!, done: false, deleted: false, version: 0, updatedAt: op.createdAt, localOnly: true, localState: 'PENDING' });
    }
  }
  const query = (params ?? {}) as { from?: string; to?: string; deleted?: boolean | string };
  const deleted = query.deleted === true || query.deleted === 'true';
  return rows.filter(row => row.deleted === deleted && (!query.from || row.dueDate >= query.from) && (!query.to || row.dueDate <= query.to));
}
