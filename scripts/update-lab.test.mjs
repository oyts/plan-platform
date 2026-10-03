import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign, verify } from 'node:crypto';
import { buildPlan, manifestFor, origin, tamperWindowsSignature } from './update-lab.mjs';
const keys = generateKeyPairSync('ed25519'), message = Buffer.from('update test fixture');
const packet = Buffer.concat([Buffer.from('Ed'), Buffer.alloc(8), sign(null, message, keys.privateKey)]);
const trustedComment = 'test fixture';
const globalSignature = sign(null, Buffer.concat([packet.subarray(10), Buffer.from(trustedComment)]), keys.privateKey);
const signature = Buffer.from(`untrusted comment: test\n${packet.toString('base64')}\ntrusted comment: ${trustedComment}\n${globalSignature.toString('base64')}\n`).toString('base64');
const entry = { version: '0.1.2', versionCode: 6, windows: { file: 'Plan Cabin_0.1.2_x64-setup.exe', signature }, android: { file: 'PlanCabin-0.1.2-debug.apk', size: 1024, sha256: 'a'.repeat(64) } };
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
  assert.notEqual(manifestFor('windows', entry, 'bad-signature').platforms['windows-x86_64'].signature, signature);
  assert.equal(entry.android.sha256, 'a'.repeat(64));
});
test('lab artifact URLs stay on loopback and cannot include path injection', () => {
  const url = new URL(manifestFor('windows', entry, 'optional').platforms['windows-x86_64'].url);
  assert.equal(url.origin, origin); assert.match(url.pathname, /Plan%20Cabin/);
  assert.throws(() => manifestFor('android', entry, 'unknown'));
});
test('safety stage preserves the accepted baseline and only raises the target', () => {
  const basic = buildPlan('basic'), safety = buildPlan('safety');
  assert.deepEqual(safety.bootstrap, basic.target);
  assert.equal(safety.target.version, '0.1.3'); assert.equal(safety.target.versionCode, 7);
  assert.notEqual(safety.target.directory, basic.target.directory);
  assert.throws(() => buildPlan('../../outside'));
});
test('combined corruption is optional and fails integrity on both platforms', () => {
  const windows = manifestFor('windows', entry, 'invalid-artifact'), android = manifestFor('android', entry, 'invalid-artifact');
  assert.equal(windows.minimumSupportedVersion, '0.0.0'); assert.equal(android.minimumSupportedVersionCode, 1);
  assert.notEqual(windows.platforms['windows-x86_64'].signature, entry.windows.signature);
  assert.equal(android.sha256, '0'.repeat(64));
});
test('tampered signature retains Minisign encoding but fails cryptographic verification', () => {
  const before = Buffer.from(signature, 'base64').toString('utf8').split('\n');
  const after = Buffer.from(tamperWindowsSignature(signature), 'base64').toString('utf8').split('\n');
  const corrupted = Buffer.from(after[1], 'base64');
  assert.equal(corrupted.length, 74);
  assert.deepEqual(corrupted.subarray(0, 10), packet.subarray(0, 10));
  for (const index of [0, 2, 3, 4]) assert.equal(after[index], before[index]);
  assert.equal(verify(null, message, keys.publicKey, packet.subarray(10)), true);
  assert.equal(verify(null, message, keys.publicKey, corrupted.subarray(10)), false);
  assert.throws(() => tamperWindowsSignature(Buffer.from('malformed').toString('base64')));
});
