import { reactive } from 'vue';
import { currentQueueOwner, serverScope } from '@/api/client';
import { freshWorkspace, readWorkspace, rememberWorkspace, workspaceDestination, workspaceKey, type WorkspaceMode } from './workspaceModel';
import { readWorkspaceRecord, writeWorkspaceRecord } from './workspaceStorage';
export const workspace = reactive(freshWorkspace());
let boundKey = '';
let loading: Promise<void> | null = null;
let generation = 0;
function bind() {
  const owner = currentQueueOwner();
  const key = owner ? workspaceKey(serverScope(), owner) : '';
  if (key !== boundKey) {
    boundKey = key;
    generation++; loading = null;
    try { Object.assign(workspace, owner ? readWorkspace(localStorage, serverScope(), owner) : freshWorkspace()); }
    catch { Object.assign(workspace, freshWorkspace()); }
  }
}
async function bindLoaded() {
  bind();
  if (!boundKey) return;
  if (!loading) {
    const key = boundKey; const version = generation;
    const scope = serverScope(); const owner = currentQueueOwner()!;
    loading = readWorkspaceRecord(key).then(serialized => {
      if (key !== boundKey || version !== generation || serialized === undefined) return;
      Object.assign(workspace, readWorkspace({ getItem: () => serialized }, scope, owner));
    });
  }
  await loading;
}
async function persist() {
  if (!boundKey) return;
  const key = boundKey; const serialized = JSON.stringify({ version: 1, ...workspace });
  try { localStorage.setItem(key, serialized); } catch { /* IndexedDB remains available when localStorage is refused. */ }
  await writeWorkspaceRecord(key, serialized);
}
export function bindWorkspaceContext() { bind(); }
export async function observeWorkspacePath(fullPath: string) {
  bind(); const key = boundKey; const version = generation;
  await bindLoaded();
  if (key && key === boundKey && version === generation) { Object.assign(workspace, rememberWorkspace(workspace, fullPath)); await persist(); }
}
export async function workspaceEntry() { await bindLoaded(); return workspaceDestination(workspace); }
export async function workspaceSwitchTarget(mode: WorkspaceMode) { await bindLoaded(); return workspaceDestination(workspace, mode); }
export function resetWorkspaceContext() { boundKey = ''; loading = null; generation++; Object.assign(workspace, freshWorkspace()); }
