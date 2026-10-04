import type { OfflineOperation } from '@/offlineQueue';

export interface LocalRecord { localState?: OfflineOperation['status']; localOnly?: boolean }
export function projectPending(data: unknown, url: string, operations: OfflineOperation[]): unknown {
  const copy = structuredClone(data);
  const edits = operations.filter((row) => row.method === 'PUT' || row.method === 'PATCH');
  function visit(value: unknown): void {
    if (Array.isArray(value)) { value.forEach(visit); return; }
    if (!value || typeof value !== 'object') return;
    const item = value as Record<string, unknown>;
    const kind = 'progress' in item && 'plannedMinutes' in item ? 'tasks' : 'unlocked' in item && 'plannedMinutes' in item ? 'mainlines/items' : 'items' in item && 'completed' in item ? 'mainlines' : 'taskCount' in item ? 'plans' : 'overall' in item && 'color' in item ? 'tags' : null;
    if (kind && typeof item.id === 'string') {
      for (const row of edits.filter((row) => row.url === `/${kind}/${item.id}`)) {
        item.localState = row.status;
        // A conflict shows the server snapshot, not a silent local overwrite.
        if (row.status !== 'PENDING' || !row.data || typeof row.data !== 'object') continue;
        const allowed = kind === 'tasks' ? ['tagId','date','startTime','endTime','plannedMinutes','category','title','description','acceptance','resourceUrl','required','sortOrder'] : kind === 'mainlines/items' ? ['title','description','acceptance','plannedMinutes','sortOrder'] : kind === 'mainlines' ? ['name','description','color','executionMode'] : kind === 'plans' ? ['name','description','startDate','endDate','restDays'] : ['name','color'];
        for (const key of allowed) if (key in row.data) item[key] = (row.data as Record<string, unknown>)[key];
        if (kind === 'mainlines/items' && 'routing' in row.data) {
          const routing = (row.data as Record<string, unknown>).routing;
          if (routing && typeof routing === 'object') {
            for (const key of ['stageId','dependencyMode','prerequisiteIds']) if (key in routing) item[key] = (routing as Record<string,unknown>)[key];
          }
        }
      }
    }
    Object.values(item).forEach(visit);
  }
  visit(copy);
  if (Array.isArray(copy)) {
    for (const row of operations.filter((row) => row.method === 'POST' && row.status === 'PENDING' && row.url === url)) {
      if (!row.data || typeof row.data !== 'object') continue;
      if (url === '/plans') copy.push({ ...row.data, id: `local:${row.id}`, taskCount: 0, completedCount: 0, localOnly: true, localState: 'PENDING' });
      if (/^\/plans\/[^/]+\/tasks$/.test(url)) copy.push({ ...row.data, id: `local:${row.id}`, planId: url.split('/')[2], tagName: '本地草稿', localOnly: true, localState: 'PENDING', progress: { status: 'PENDING', accumulatedSeconds: 0, acceptanceChecks: [], note: '', version: 0 } });
      if (/^\/plans\/[^/]+\/tags$/.test(url)) copy.push({ ...row.data, id: `local:${row.id}`, overall: false, localOnly: true, localState: 'PENDING' });
    }
  }
  return copy;
}
