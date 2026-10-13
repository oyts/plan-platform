import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign,createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {verifyUpdaterSignature,validateApkEvidence,PUBLIC_ANDROID_PACKAGE} from './formal-release-support.mjs';
test('Android CI builds and tests without publishing private APK artifacts',()=>{
  const workflow=readFileSync(new URL('../.github/workflows/android.yml',import.meta.url),'utf8');
  assert.ok(workflow.includes('scripts/android.ps1 -Action build'));
  assert.ok(!workflow.includes('actions/upload-artifact'));
});
function fixture(prehash=false) {
  const key=generateKeyPairSync('ed25519'), keyId=Buffer.alloc(8,7), content=Buffer.from('release artifact fixture');
  const rawPub=key.publicKey.export({type:'spki',format:'der'}).subarray(-32);
  const packet=Buffer.concat([Buffer.from(prehash?'ED':'Ed'),keyId,sign(null,prehash?createHash('blake2b512').update(content).digest():content,key.privateKey)]);
  const comment='timestamp:test version:0.1.4';
  const global=sign(null,Buffer.concat([packet.subarray(10),Buffer.from(comment)]),key.privateKey);
  const pub=Buffer.from('untrusted comment: public\n'+Buffer.concat([Buffer.from('Ed'),keyId,rawPub]).toString('base64')+'\n').toString('base64');
  const signature=Buffer.from('untrusted comment: signature\n'+packet.toString('base64')+'\ntrusted comment: '+comment+'\n'+global.toString('base64')+'\n').toString('base64');
  return {pub,signature,content};
}
test('real Ed and ED signatures verify artifact and trusted comment',()=>{
  for(const prehash of [false,true]) { const f=fixture(prehash); assert.equal(verifyUpdaterSignature(f.content,f.pub,f.signature).algorithm,prehash?'ED':'Ed'); }
});
test('wrong payload, wrong key, malformed signature and changed trusted comment are rejected',()=>{
  const a=fixture(),b=fixture();
  assert.throws(()=>verifyUpdaterSignature(Buffer.from('tampered'),a.pub,a.signature));
  assert.throws(()=>verifyUpdaterSignature(a.content,b.pub,a.signature));
  assert.throws(()=>verifyUpdaterSignature(a.content,a.pub,'invalid'));
  const changed=Buffer.from(Buffer.from(a.signature,'base64').toString().replace('version:0.1.4','version:9.9.9')).toString('base64');
  assert.throws(()=>verifyUpdaterSignature(a.content,a.pub,changed));
});
test('formal APK must have the exact new identity, release flag and pinned certificate',()=>{
  const cert='a'.repeat(64), good=`package: name='${PUBLIC_ANDROID_PACKAGE}' versionCode='8' versionName='0.1.4'`;
  const signature=`Signer #1 certificate DN: CN=PlanCabin Release\nSigner #1 certificate SHA-256 digest: ${cert}`;
  assert.equal(validateApkEvidence(good,signature,'0.1.4',8,cert).packageName,PUBLIC_ANDROID_PACKAGE);
  assert.throws(()=>validateApkEvidence(good.replace(PUBLIC_ANDROID_PACKAGE,'cn.zijun701.plan.mobile'),signature,'0.1.4',8,cert));
  assert.throws(()=>validateApkEvidence(good+'\napplication-debuggable',signature,'0.1.4',8,cert));
  assert.throws(()=>validateApkEvidence(good,signature.replace('PlanCabin Release','Android Debug'),'0.1.4',8,cert));
  assert.throws(()=>validateApkEvidence(good,signature,'0.1.4',8,'b'.repeat(64)));
  assert.throws(()=>validateApkEvidence(good,signature,'0.1.4',9,cert));
});
test('Gradle keeps legacy and regression identities isolated from formal release',()=>{
  const gradle=readFileSync(new URL('../web/android/app/build.gradle',import.meta.url),'utf8');
  assert.match(gradle,/publicRelease \? "cn\.zijun701\.plan\.app"/);
  assert.match(gradle,/cn\.zijun701\.plan\.mobile\.regression/);
  assert.match(gradle,/Regression and public release identities cannot be combined/);
  assert.match(gradle,/Formal identity cannot produce a debug APK/);
  assert.match(gradle,/Formal release cannot include lab trust or test version overrides/);
});
