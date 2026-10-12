import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {validateBackupIdentity,signingFiles} from './signing-identity.mjs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
function fixture() {
  const files=Object.fromEntries(signingFiles.map(name=>[name,Buffer.from('synthetic test bytes: '+name).toString('base64')]));
  const hashes=Object.fromEntries(signingFiles.map(name=>[name,createHash('sha256').update(Buffer.from(files[name],'base64')).digest('hex')]));
  return {schema:1,metadata:{schema:1,alias:'plan-cabin-release',androidPackage:'cn.zijun701.plan.app',files:hashes},files,credentials:{windows:Buffer.alloc(32,1).toString('base64'),android:Buffer.alloc(32,2).toString('base64')}};
}
test('all exact signing filenames, hashes and password formats are required',()=>{
  const p=fixture();assert.equal(validateBackupIdentity(p),p);
  for(const change of [p=>delete p.files['android-release.p12'],p=>p.files['../credentials.txt']='abc',p=>p.credentials.android='weak',p=>p.metadata.androidPackage='cn.zijun701.plan.mobile',p=>p.files['windows-updater.key']='YWJj']) {
    const broken=fixture();change(broken);assert.throws(()=>validateBackupIdentity(broken));
  }
});
test('private backup codec round trips Unicode passphrases and flushes large payloads',()=>{
  const payload=fixture();payload.files['windows-updater.key']=Buffer.alloc(60000,3).toString('base64');payload.metadata.files['windows-updater.key']=createHash('sha256').update(Buffer.alloc(60000,3)).digest('hex');
  const password='仅供单元测试的中文备份口令，不是生产秘密';
  const run=(args,request)=>spawnSync(process.execPath,[fileURLToPath(new URL('./signing-backup.mjs',import.meta.url)),...args],{input:JSON.stringify(request),encoding:'utf8',maxBuffer:2*1024*1024});
  const sealed=run([], {payload,password});assert.equal(sealed.status,0,sealed.stderr);
  const restored=run(['open'],{archive:JSON.parse(sealed.stdout),password});assert.equal(restored.status,0,restored.stderr);assert.deepEqual(JSON.parse(restored.stdout),payload);
  const wrong=run(['open'],{archive:JSON.parse(sealed.stdout),password:'incorrect test passphrase'});assert.notEqual(wrong.status,0);assert.equal(wrong.stdout,'');
});
