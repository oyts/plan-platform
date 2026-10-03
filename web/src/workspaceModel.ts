export type WorkspaceMode = 'plan' | 'route';
export interface WorkspacePreference { mode: WorkspaceMode; planPath: string; routePath: string }
export const freshWorkspace = (): WorkspacePreference => ({ mode: 'plan', planPath: '/', routePath: '/routes' });
export function pathWorkspace(fullPath: string): WorkspaceMode | null {
  const path = fullPath.split(/[?#]/)[0];
  if (['/routes', '/mainlines', '/route-progress', '/route-records'].includes(path)) return 'route';
  if (['/', '/schedule', '/plans', '/stats', '/reviews', '/records', '/exports'].includes(path) || /^\/plans\/[a-zA-Z0-9_-]+(?:\/studio)?$/.test(path)) return 'plan';
  return null;
}
function validPath(path: unknown, mode: WorkspaceMode): path is string {
  return typeof path === 'string' && path.length < 1024 && pathWorkspace(path) === mode && !path.includes('#');
}
export function workspaceKey(scope: string, owner: string) { return `plan-platform:workspace:${encodeURIComponent(scope)}:${encodeURIComponent(owner)}`; }
export function readWorkspace(storage: Pick<Storage, 'getItem'>, scope: string, owner: string): WorkspacePreference {
  const fallback = freshWorkspace();
  try {
    const data = JSON.parse(storage.getItem(workspaceKey(scope, owner)) ?? 'null');
    if (!data || data.version !== 1) return fallback;
    return { mode: data.mode === 'route' ? 'route' : 'plan', planPath: validPath(data.planPath, 'plan') ? data.planPath : '/', routePath: validPath(data.routePath, 'route') ? data.routePath : '/routes' };
  } catch { return fallback; }
}
export function rememberWorkspace(state: WorkspacePreference, fullPath: string): WorkspacePreference {
  const mode = pathWorkspace(fullPath);
  if (!mode || !validPath(fullPath, mode)) return state;
  return { ...state, mode, [mode === 'plan' ? 'planPath' : 'routePath']: fullPath };
}
export const workspaceHome = (mode: WorkspaceMode) => mode === 'plan' ? '/' : '/routes';
export const workspaceDestination = (state: WorkspacePreference, mode = state.mode) => mode === 'plan' ? state.planPath : state.routePath;
export const planNavigation = [
  { path: '/', label: '今日', icon: 'today' }, { path: '/schedule', label: '日程', icon: 'schedule' }, { path: '/plans', label: '计划', icon: 'plans' },
] as const;
export const routeNavigation = [
  { path: '/routes', label: '继续', icon: 'today' }, { path: '/mainlines', label: '路线', icon: 'route' }, { path: '/route-progress', label: '进展', icon: 'progress' },
] as const;
