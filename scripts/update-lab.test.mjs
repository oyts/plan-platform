import test from 'node:test';
import assert from 'node:assert/strict';
import { manifestFor, origin } from './update-lab.mjs';
const entry = { version: '0.1.2', versionCode: 6, windows: { file: 'Plan Cabin_0.1.2_x64-setup.exe', signature: 'signed' }, android: { file: 'PlanCabin-0.1.2-debug.apk', size: 1024, sha256: 'a'.repeat(64) } };
test('optional update does not force the bootstrap', () => {
  assert.equal(manifestFor('windows', entry, 'optional').minimumSupportedVersion, '0.0.0');
  assert.equal(manifestFor('android', entry, 'optional').minimumSupportedVersionCode, 1);
});
test('required policy only raises the minimum to the target', () => {
  assert.equal(manifestFor('windows', entry, 'required').minimumSupportedVersion, entry.version);
  assert.equal(manifestFor('android', entry, 'required').minimumSupportedVersionCode, entry.versionCode);
});
test('corruption scenarios alter checksums/signatures without mutating original data', () => {
  assert.equal(manifestFor('android', entry, 'bad-hash').sha256, '0'.repeat(64));
  assert.notEqual(manifestFor('windows', entry, 'bad-signature').platforms['windows-x86_64'].signature, 'signed');
  assert.equal(entry.android.sha256, 'a'.repeat(64));
});
test('lab artifact URLs stay on loopback and cannot include path injection', () => {
  const url = new URL(manifestFor('windows', entry, 'optional').platforms['windows-x86_64'].url);
  assert.equal(url.origin, origin); assert.match(url.pathname, /Plan%20Cabin/);
  assert.throws(() => manifestFor('android', entry, 'unknown'));
});
