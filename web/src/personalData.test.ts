import { describe,it,expect } from 'vitest';
import { readPrivateBackup,MAX_BACKUP_BYTES } from './personalData';
describe('private file selection does not submit data',()=>{
  it.each([1,2,3])('recognizes schema %i without restoring or transmitting it',schemaVersion=>{expect(readPrivateBackup(JSON.stringify({format:'plan-cabin-private-backup',schemaVersion}),100)).toMatchObject({schemaVersion});});
  it('accepts a known header and leaves complete validation to the server preview',()=>{
    const payload={format:'plan-cabin-private-backup',schemaVersion:1,records:{plans:[]}};
    expect(readPrivateBackup('\uFEFF'+JSON.stringify(payload),200)).toEqual(payload);
  });
  it('rejects oversized, malformed, unsupported and legacy files before upload',()=>{
    expect(()=>readPrivateBackup('{}',MAX_BACKUP_BYTES+1)).toThrow('5 MiB');
    expect(()=>readPrivateBackup('{bad',4)).toThrow('可读取');
    expect(()=>readPrivateBackup('{"schemaVersion":6}',20)).toThrow('旧版迁移');
    expect(()=>readPrivateBackup('null',4)).toThrow('schemaVersion 1');
  });
});
