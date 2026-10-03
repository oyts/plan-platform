import { IDBFactory } from 'fake-indexeddb';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { readWorkspaceRecord, writeWorkspaceRecord } from './workspaceStorage';
beforeEach(() => vi.stubGlobal('indexedDB', new IDBFactory()));
describe('durable workspace navigation', () => {
  it('commits before resolving and separates server/account keys', async () => {
    await writeWorkspaceRecord('server-a:alice', '{"mode":"route"}');
    expect(await readWorkspaceRecord('server-a:alice')).toBe('{"mode":"route"}');
    expect(await readWorkspaceRecord('server-a:bob')).toBeUndefined();
    expect(await readWorkspaceRecord('server-b:alice')).toBeUndefined();
  });
  it('replaces only the requested preference', async () => {
    await writeWorkspaceRecord('alice', 'old'); await writeWorkspaceRecord('bob', 'other');
    await writeWorkspaceRecord('alice', 'new');
    expect(await readWorkspaceRecord('alice')).toBe('new'); expect(await readWorkspaceRecord('bob')).toBe('other');
  });
  it('does not block navigation when persistent storage is refused', async () => {
    vi.stubGlobal('indexedDB', { open: () => { throw new Error('Storage refused'); } });
    expect(await readWorkspaceRecord('alice')).toBeUndefined();
    await expect(writeWorkspaceRecord('alice', 'value')).resolves.toBeUndefined();
  });
});
