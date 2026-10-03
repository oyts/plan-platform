import { beforeEach, describe, expect, it, vi } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
const identity = vi.hoisted(() => ({scope:'server-a', owner:'alice' as string | null}));
vi.mock('@/api/client', () => ({ serverScope: () => identity.scope, currentQueueOwner: () => identity.owner }));
import { workspace, workspaceEntry, workspaceSwitchTarget, observeWorkspacePath, resetWorkspaceContext } from './workspace';
import { readWorkspaceRecord, writeWorkspaceRecord } from './workspaceStorage';
import { workspaceKey } from './workspaceModel';
beforeEach(() => {
  identity.scope = 'server-a'; identity.owner = 'alice'; resetWorkspaceContext();
  vi.stubGlobal('indexedDB', new IDBFactory());
  const rows = new Map<string,string>();
  vi.stubGlobal('localStorage', {getItem:(key:string)=>rows.get(key) ?? null, setItem:(key:string,value:string)=>rows.set(key,value)});
});
describe('workspace restoration and account changes', () => {
  it('restores committed state even when localStorage has the previous mode', async () => {
    localStorage.setItem(workspaceKey('server-a','alice'), JSON.stringify({version:1,mode:'plan',planPath:'/plans',routePath:'/routes'}));
    await writeWorkspaceRecord(workspaceKey('server-a','alice'), JSON.stringify({version:1,mode:'route',planPath:'/schedule',routePath:'/mainlines?line=guitar'}));
    expect(await workspaceEntry()).toBe('/mainlines?line=guitar'); expect(workspace.mode).toBe('route');
    expect(await workspaceSwitchTarget('plan')).toBe('/schedule');
  });
  it('commits both positions before navigation resolves and recovers after memory reset', async () => {
    await observeWorkspacePath('/plans/one'); await observeWorkspacePath('/mainlines?line=guitar');
    expect(JSON.parse((await readWorkspaceRecord(workspaceKey('server-a','alice')))!)).toMatchObject({mode:'route',planPath:'/plans/one'});
    resetWorkspaceContext(); expect(await workspaceEntry()).toBe('/mainlines?line=guitar');
    await observeWorkspacePath('/sync'); expect(await workspaceEntry()).toBe('/mainlines?line=guitar');
  });
  it('does not expose another account or server position and preserves the original preference', async () => {
    await observeWorkspacePath('/mainlines?line=guitar'); identity.owner = 'bob';
    expect(await workspaceEntry()).toBe('/'); identity.owner = 'alice'; identity.scope = 'server-b';
    expect(await workspaceEntry()).toBe('/'); identity.scope = 'server-a';
    expect(await workspaceEntry()).toBe('/mainlines?line=guitar');
  });
  it('discards a pending restoration when the user signs out', async () => {
    await observeWorkspacePath('/mainlines?line=guitar'); resetWorkspaceContext();
    const pending = workspaceEntry(); identity.owner = null; resetWorkspaceContext(); await pending;
    expect(workspace.mode).toBe('plan'); expect(await workspaceEntry()).toBe('/');
  });
});
