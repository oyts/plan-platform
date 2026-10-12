import test from 'node:test';
import assert from 'node:assert/strict';
import {sealBackup,openBackup} from './signing-backup-support.mjs';
test('portable backup restores exact keys and passwords with a separate passphrase',()=>{
  const payload={files:{key:'fixture'},credentials:{android:'fixture-secret'}},password='a separate strong test passphrase';
  const sealed=sealBackup(payload,password);
  assert.deepEqual(openBackup(sealed,password),payload);
  assert(!JSON.stringify(sealed).includes('fixture-secret'));
  assert.notDeepEqual(sealBackup(payload,password),sealed);
});
test('wrong passphrase and modified ciphertext/tag/header are rejected',()=>{
  const password='a separate strong test passphrase',sealed=sealBackup({key:'fixture'},password);
  assert.throws(()=>openBackup(sealed,'different passphrase'));
  for(const field of ['data','tag','iv','salt']) {
    const bytes=Buffer.from(sealed[field],'base64');bytes[0]^=1;
    assert.throws(()=>openBackup({...sealed,[field]:bytes.toString('base64')},password));
  }
  assert.throws(()=>openBackup({...sealed,schema:2},password));
  assert.throws(()=>sealBackup({},'short'));
});
