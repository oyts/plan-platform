import { describe, expect, it } from 'vitest';
import { parseProductDownloads } from './productDownloads';
const origin='https://plan.example.cn';
const release={version:'0.1.4',url:'/downloads/plan.apk',sha256:'a'.repeat(64),bytes:1024};
describe('official installer manifest', () => {
  it('supports an unpublished state without fake links',()=>{ expect(parseProductDownloads({schemaVersion:1,windows:null,android:null},origin)).toEqual({windows:null,android:null}); });
  it('resolves only verified same-site installer metadata',()=>{ expect(parseProductDownloads({schemaVersion:1,android:release},origin).android?.url).toBe(`${origin}/downloads/plan.apk`); });
  it.each(['javascript:alert(1)','https://evil.test/downloads/plan.apk','/other/plan.apk','/downloads/plan.exe','/downloads/plan.apk?token=secret','//evil.test/plan.apk'])('rejects unsafe or wrong package URL %s',url=>{ expect(parseProductDownloads({schemaVersion:1,android:{...release,url}},origin).android).toBeNull(); });
  it('rejects absent integrity metadata and unsupported schema',()=>{ expect(parseProductDownloads({schemaVersion:1,android:{...release,sha256:''}},origin).android).toBeNull(); expect(()=>parseProductDownloads({schemaVersion:2},origin)).toThrow(); });
});
