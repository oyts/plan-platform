import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdir,mkdtemp,readFile,rm,access} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {join,resolve,sep} from 'node:path';

const root=fileURLToPath(new URL('../',import.meta.url));
const allowed=join(root,'acceptance-results/roadmap-pc');
await mkdir(allowed,{recursive:true});
const run=await mkdtemp(join(allowed,'network-guard-'));
const invoke=(...args)=>spawnSync(process.execPath,[join(root,'scripts/roadmap-pc-network.mjs'),...args],{cwd:root,encoding:'utf8',windowsHide:true});
test.after(async()=>{
  // Remove only this test's freshly allocated directory, never an acceptance run.
  assert.ok(resolve(run).startsWith(resolve(allowed)+sep+'network-guard-'));
  await rm(run,{recursive:true});
});

test('rejects an unknown command before writing control',async()=>{
  const result=invoke('delete',run);
  assert.notEqual(result.status,0);
  assert.match(result.stderr,/Use serve\|online\|offline\|stop/);
  await assert.rejects(access(join(run,'network-switch.json')));
});
test('rejects a directory outside PC evidence',()=>{
  const result=invoke('offline',join(root,'scripts'));
  assert.notEqual(result.status,0);
  assert.match(result.stderr,/Control path must stay/);
});
test('rejects the evidence root itself',()=>{
  const result=invoke('stop',allowed);
  assert.notEqual(result.status,0);
  assert.match(result.stderr,/Control path must stay/);
});
for(const mode of ['offline','online','stop'])test(`${mode} writes only the requested synthetic control`,async()=>{
  const result=invoke(mode,run);
  assert.equal(result.status,0,result.stderr);
  assert.deepEqual(JSON.parse(await readFile(join(run,'network-switch.json'),'utf8')),{mode});
});
