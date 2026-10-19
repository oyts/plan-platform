import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,readdirSync,rmSync,existsSync} from 'node:fs';
import {join,dirname,resolve,basename} from 'node:path';
import {tmpdir} from 'node:os';
import {windowsPublication,verifyBackupCopy,sha256,releaseOrigin} from './windows-public-release.mjs';
import {prepareWindowsPublicRelease} from './prepare-windows-public-release.mjs';

function fixture() {
  const key=generateKeyPairSync('ed25519'),id=Buffer.alloc(8,7),artifact=Buffer.from('synthetic signed installer fixture');
  const trustedComment='timestamp:test\tfile:Plan Cabin_0.1.4_x64-setup.exe\tversion:0.1.4';
  const signatureBytes=sign(null,artifact,key.privateKey);
  const packet=Buffer.concat([Buffer.from('Ed'),id,signatureBytes]);
  const publicKey=Buffer.from('untrusted comment: public\n'+Buffer.concat([Buffer.from('Ed'),id,key.publicKey.export({type:'spki',format:'der'}).subarray(-32)]).toString('base64')+'\n').toString('base64');
  const signature=Buffer.from('untrusted comment: signature\n'+packet.toString('base64')+'\ntrusted comment: '+trustedComment+'\n'+sign(null,Buffer.concat([signatureBytes,Buffer.from(trustedComment)]),key.privateKey).toString('base64')+'\n').toString('base64');
  const original=Buffer.from('synthetic encrypted backup fixture - not real signing material');
  const evidence={schema:1,restoreCryptographicProof:true,originalSigningFilesUnchanged:true,passphraseCustodyConfirmed:true,backupSha256:sha256(original),backupBytes:original.length};
  const candidate={schemaVersion:1,version:'0.1.4',status:'LOCAL_SIGNED_CANDIDATE',publicRegistrationEnabled:false,windows:{file:'Plan Cabin_0.1.4_x64-setup.exe',bytes:artifact.length,sha256:sha256(artifact),updaterSignatureVerified:true,keyId:id.toString('hex'),algorithm:'Ed',trustedComment},android:{file:'must-not-publish.apk'}};
  return {artifact,publicKey,signature,original,evidence,candidate,expectedVersion:'0.1.4',backupProof:verifyBackupCopy(evidence,original,Buffer.from(original)),windowsAcceptanceConfirmed:true,oldCloudPairCleanupConfirmed:true,createdUtc:'2026-10-13T00:00:00.000Z'};
}
test('Windows-only publication keeps optional policy, Android and registration closed',()=>{
  const p=windowsPublication(fixture());
  assert.equal(p.update.minimumSupportedVersion,'0.0.0');
  assert.equal(p.update.platforms['windows-x86_64'].url,releaseOrigin+'/client-updates/windows/releases/0.1.4/Plan%20Cabin_0.1.4_x64-setup.exe');
  assert.equal(p.downloads.android,null);assert.equal(p.downloads.windows.sha256,p.plan.windows.sha256);
  assert.equal(p.plan.publicRegistrationEnabled,false);assert.equal(p.plan.publicReleaseApproved,false);assert.equal(p.plan.status,'PREPARED_NOT_PUBLISHED');
});
test('no unsigned, renamed, stale-version or changed installer candidate can stage',()=>{
  for(const change of [f=>f.candidate.status='DEBUG',f=>f.candidate.windows.file='../other.exe',f=>f.artifact=Buffer.from('changed'),f=>f.candidate.windows.bytes++,f=>f.candidate.version='0.1.3',f=>f.candidate.windows.updaterSignatureVerified=false,f=>f.candidate.publicRegistrationEnabled=true]) {
    const f=fixture();change(f);assert.throws(()=>windowsPublication(f));
  }
});
test('actual updater cryptography, pinned key and trusted version are all checked',()=>{
  for(const change of [f=>f.publicKey=fixture().publicKey,f=>f.signature='invalid',f=>f.candidate.windows.keyId='0'.repeat(16),f=>f.candidate.windows.trustedComment='version:9.9.9']) {
    const f=fixture();change(f);assert.throws(()=>windowsPublication(f));
  }
});
test('Windows acceptance and separated old-pair custody require explicit confirmation',()=>{
  for(const field of ['windowsAcceptanceConfirmed','oldCloudPairCleanupConfirmed']) {const f=fixture();f[field]=false;assert.throws(()=>windowsPublication(f));}
  const f=fixture();f.backupProof.independentCopyHashVerified=false;assert.throws(()=>windowsPublication(f));
});
test('cloud copy checksum cannot substitute for missing restore or password custody proof',()=>{
  const f=fixture();
  for(const field of ['restoreCryptographicProof','originalSigningFilesUnchanged','passphraseCustodyConfirmed']) {assert.throws(()=>verifyBackupCopy({...f.evidence,[field]:false},f.original,f.original));}
  assert.throws(()=>verifyBackupCopy(f.evidence,f.original,Buffer.from('wrong copy')));
  assert.throws(()=>verifyBackupCopy({...f.evidence,backupBytes:f.original.length+1},f.original,f.original));
});
function sandbox(run) {
  const directory=mkdtempSync(join(tmpdir(),'plan-windows-public-test-'));
  try {return run(directory);} finally {
    assert.equal(dirname(resolve(directory)),resolve(tmpdir()));assert.match(basename(directory),/^plan-windows-public-test-/);
    rmSync(directory,{recursive:true,force:true});
  }
}
function seed(root) {
  const f=fixture(),candidateDirectory=join(root,'production-artifacts/client-release/0.1.4/accepted'),signingBackupDirectory=join(root,'.desktop-tools/signing-backups/private/run'),cloudBackupCopy=join(root,'downloaded/PlanCabin.plan-signing-backup');
  for(const path of ['web','.release-signing','downloaded','production-artifacts/client-release/0.1.4/accepted/windows','production-artifacts/client-release/0.1.4/accepted/metadata','.desktop-tools/signing-backups/private/run'])mkdirSync(join(root,path),{recursive:true});
  writeFileSync(join(root,'web/package.json'),JSON.stringify({version:'0.1.4'}));writeFileSync(join(root,'.release-signing/windows-updater.key.pub'),f.publicKey);
  writeFileSync(join(candidateDirectory,'metadata/candidate.json'),JSON.stringify(f.candidate));
  writeFileSync(join(candidateDirectory,'windows',f.candidate.windows.file),f.artifact);writeFileSync(join(candidateDirectory,'windows',f.candidate.windows.file+'.sig'),f.signature);
  writeFileSync(join(signingBackupDirectory,'backup-check.json'),JSON.stringify(f.evidence));writeFileSync(join(signingBackupDirectory,'PlanCabin.plan-signing-backup'),f.original);writeFileSync(cloudBackupCopy,f.original);
  return {f,options:{candidateDirectory,signingBackupDirectory,cloudBackupCopy,windowsAcceptanceConfirmed:true,oldCloudPairCleanupConfirmed:true,createdUtc:f.createdUtc}};
}
test('immutable staged tree contains Windows public files only, no keys, APK or backup',()=>sandbox(root=>{
  const {f,options}=seed(root),result=prepareWindowsPublicRelease(root,options),second=prepareWindowsPublicRelease(root,options);
  assert.notEqual(result.output,second.output);
  const paths=readdirSync(result.output,{recursive:true}).map(String).filter(p=>/\.(exe|sig|json|key|apk|plan-signing-backup)$/.test(p));
  assert.equal(paths.length,6);assert.equal(paths.some(p=>/\.(apk|key|plan-signing-backup)$/.test(p)),false);
  assert.equal(sha256(readFileSync(join(result.output,'public/downloads/0.1.4',f.candidate.windows.file))),f.candidate.windows.sha256);
  assert.equal(sha256(readFileSync(join(options.candidateDirectory,'windows',f.candidate.windows.file))),f.candidate.windows.sha256);
  assert.equal(JSON.parse(readFileSync(join(result.output,'public/downloads/manifest.json'))).android,null);
}));
test('same original backup path, unrelated password file and outside candidate are refused before output',()=>sandbox(root=>{
  const {options}=seed(root);
  for(const change of [{cloudBackupCopy:join(options.signingBackupDirectory,'PlanCabin.plan-signing-backup')},{cloudBackupCopy:join(root,'downloaded/password.txt')},{candidateDirectory:join(root,'downloaded')},{windowsAcceptanceConfirmed:false}]) assert.throws(()=>prepareWindowsPublicRelease(root,{...options,...change}));
  assert.equal(existsSync(join(root,'production-artifacts/windows-public-release')),false);
}));
test('optional release notes reach the update feed without changing artifact bytes',()=>sandbox(root=>{
  const {options,f}=seed(root),notes='邮箱验证与运行保障；可选更新，安装前仍需确认。';
  const result=prepareWindowsPublicRelease(root,{...options,notes});
  const feed=JSON.parse(readFileSync(join(result.output,'public/client-updates/windows/latest.json')));
  assert.equal(feed.notes,notes);assert.equal(result.plan.windows.sha256,f.candidate.windows.sha256);
}));
