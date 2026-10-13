import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,existsSync,rmSync} from 'node:fs';
import {join,dirname,basename,resolve} from 'node:path';
import {tmpdir} from 'node:os';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const shell=process.env.PLAN_TEST_BASH || (process.platform==='win32' ? null : '/bin/sh');
if(!shell) throw new Error('Set PLAN_TEST_BASH to Git Bash for the shell publication tests on Windows.');
const script=fileURLToPath(new URL('./retain-windows-releases.sh',import.meta.url));
function fixture(run) {
  const root=mkdtempSync(join(tmpdir(),'plan-retain-release-'));
  const old=join(root,'old'),next=join(root,'next');mkdirSync(old);mkdirSync(next);
  const add=(base,path,body)=>{const file=join(base,path);mkdirSync(dirname(file),{recursive:true});writeFileSync(file,body);};
  const execute=()=>execFileSync(shell,[script,old,next],{encoding:'utf8'});
  try { run({old,next,add,execute}); } finally {
    assert.equal(dirname(resolve(root)),resolve(tmpdir()));assert.match(basename(root),/^plan-retain-release-/);
    rmSync(root,{recursive:true,force:true});
  }
}
test('retains old Windows bytes without copying old feeds or APK metadata',()=>fixture(({old,next,add,execute})=>{
  const exe='Plan Cabin_0.1.4_x64-setup.exe';
  add(old,`downloads/0.1.4/${exe}`,'original installer');
  add(old,`client-updates/windows/releases/0.1.4/${exe}`,'original installer');
  add(old,`client-updates/windows/releases/0.1.4/${exe}.sig`,'signature');
  add(old,'downloads/manifest.json','old feed');add(next,'downloads/manifest.json','new feed');
  add(old,'private/candidate.json','private fixture');
  execute();
  assert.equal(readFileSync(join(next,`downloads/0.1.4/${exe}`),'utf8'),'original installer');
  assert.equal(readFileSync(join(next,`client-updates/windows/releases/0.1.4/${exe}.sig`),'utf8'),'signature');
  assert.equal(readFileSync(join(next,'downloads/manifest.json'),'utf8'),'new feed');
  assert.equal(existsSync(join(next,'private')),false);
}));
test('rejects a different binary at an existing immutable version',()=>fixture(({old,next,add,execute})=>{
  const path='downloads/0.1.4/Plan Cabin_0.1.4_x64-setup.exe';add(old,path,'original');add(next,path,'changed');
  assert.throws(execute);assert.equal(readFileSync(join(next,path),'utf8'),'changed');
}));
test('accepts an identical existing version without overwriting',()=>fixture(({old,next,add,execute})=>{
  const path='downloads/0.1.4/Plan Cabin_0.1.4_x64-setup.exe';add(old,path,'same');add(next,path,'same');execute();
  assert.equal(readFileSync(join(next,path),'utf8'),'same');
}));
test('refuses APK or malformed version entries in immutable Windows trees',()=>{
  for(const path of ['downloads/0.1.4/private.apk','downloads/not-a-version/Plan Cabin_not-a-version_x64-setup.exe']) fixture(({old,add,execute})=>{
    add(old,path,'not an allowed installer');assert.throws(execute);
  });
});
