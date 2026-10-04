import type { Mainline, MainlineItem } from '@/types';
export function routeGroups(line: Mainline, query = '', unfinishedOnly = false) {
  const needle = query.trim().toLocaleLowerCase();
  const matches = (item: MainlineItem) => (!unfinishedOnly || item.status !== 'COMPLETED') && (!needle || (item.title + ' ' + item.description).toLocaleLowerCase().includes(needle));
  const stages = [...(line.stages ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const known = new Set(stages.map(s => s.id));
  return [...stages.map(stage => ({ id: stage.id, name: stage.name, description: stage.description, items: line.items.filter(i => i.stageId === stage.id) })),
    { id: 'ungrouped', name: stages.length ? '未分组' : '路线步骤', description: '', items: line.items.filter(i => !i.stageId || !known.has(i.stageId)) }]
    .map(group => ({ ...group, completed: group.items.filter(i => i.status === 'COMPLETED').length, total: group.items.length, items: group.items.filter(matches).sort((a, b) => a.sortOrder - b.sortOrder) }));
}
