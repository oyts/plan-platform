// Execute the real shell logic with isolated files and fake system commands.
// Never uses sudo, host Nginx, a real database, credentials or the network.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdtemp, mkdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { spawnSync } from 'node:child_process';
const source = await readFile(new URL('./switch-public-entry.sh', import.meta.url), 'utf8');
const git = spawnSync('git', ['--exec-path'], {encoding:'utf8',windowsHide:true});
const bash = process.platform === 'win32'
  ? resolve(git.stdout.trim(), '../../../bin/bash.exe') : '/bin/sh';
const shellPath = value => value.replaceAll('\\','/').replace(/^([A-Za-z]):/,(_,drive)=>`/${drive.toLowerCase()}`);
const original = 'server { proxy_pass http://127.0.0.1:43127; }\n';
const curlStub = `#!/bin/sh
resolved=false
for arg in "$@"; do
  case "$arg" in --resolve) resolved=true;; http*) url="$arg";; esac
done
case "$url" in
  http*/actuator/health) printf '%s' '{"status":"UP"}';;
  http*/api/auth/config) printf '%s' '{"registrationEnabled":false,"todoEnabled":true}';;
  http://*/api/health) printf '%s' '{"service":"plan-platform-api","version":"0.1.4"}';;
  https://*/api/health)
    count=0; test ! -f "$CUTOVER_TEST_ROOT/count" || count=$(cat "$CUTOVER_TEST_ROOT/count")
    count=$((count+1)); echo "$count" > "$CUTOVER_TEST_ROOT/count"
    if [ "$TEST_MODE" = fail ] || { [ "$TEST_MODE" = delayed ] && [ "$count" -le 2 ]; } || { [ "$TEST_MODE" = public-fail ] && [ "$resolved" = false ]; }; then
      printf '%s' '{"service":"plan-console-sync","version":"1.10.1"}'
    else printf '%s' '{"service":"plan-platform-api","version":"0.1.4"}'; fi;;
  https://*/) printf '%s' '<html><div id="app"></div></html>';;
  *) exit 1;;
esac
`;
async function scenario(mode, existing) {
  const root = await mkdtemp(join(tmpdir(), 'plan-cutover-test-'));
  assert.ok(resolve(root).startsWith(resolve(tmpdir()) + '/'.replace('/', process.platform==='win32'?'\\':'/')));
  const config=join(root,'nginx.conf'), backup=join(root,'nginx.before'), bin=join(root,'bin');
  try {
    await mkdir(bin);
    await writeFile(config,original);
    if(existing) await writeFile(backup,existing==='same'?original:'Different rollback config\n');
    for(const [name,body] of Object.entries({
      id:'#!/bin/sh\nprintf 0\n', curl:curlStub,
      nginx:'#!/bin/sh\nexit 0\n',
      systemctl:'#!/bin/sh\necho reload >> "$CUTOVER_TEST_ROOT/reloads"\n',
      sleep:'#!/bin/sh\nexit 0\n'
    })) await writeFile(join(bin,name),body,{mode:0o755});
    const copy = `export PATH='${shellPath(bin)}':"$PATH"\n` + source
      .replace('config=/etc/nginx/conf.d/plan-console-public.conf',`config='${shellPath(config)}'`)
      .replace('backup=/etc/nginx/conf.d/plan-console-public.conf.before-plan-platform',`backup='${shellPath(backup)}'`);
    const file=join(root,'switch.sh'); await writeFile(file,copy);
    const result=spawnSync(bash,[shellPath(file)],{encoding:'utf8',windowsHide:true,timeout:15000,
      env:{...process.env,CUTOVER_TEST_ROOT:shellPath(root),TEST_MODE:mode}});
    if(result.error) throw result.error;
    return {status:result.status,output:result.stdout+result.stderr,config:await readFile(config,'utf8'),
      backup:await readFile(backup,'utf8').catch(()=>null)};
  } finally {
    // Only this mkdtemp run's generated fixtures are removed, not repository data.
    if(dirname(root) !== resolve(tmpdir()) || !root.split(/[\\/]/).at(-1).startsWith('plan-cutover-test-')) throw new Error('Unsafe fixture cleanup path');
    await rm(root,{recursive:true,force:true});
  }
}
test('delayed Nginx worker replacement is retried instead of rolled back',async()=>{
  const r=await scenario('delayed'); assert.equal(r.status,0,r.output);
  assert.match(r.output,/Waiting for Nginx HTTPS entry/); assert.match(r.config,/:43140/);
  assert.equal(r.backup,original); assert.doesNotMatch(r.output,/Previous entry restored/);
});
test('a previous identical rollback backup can be safely reused',async()=>{
  const r=await scenario('ready','same'); assert.equal(r.status,0,r.output);
  assert.match(r.output,/Reusing/); assert.equal(r.backup,original);
});
test('a differing rollback backup is preserved and switch is refused',async()=>{
  const r=await scenario('ready','different'); assert.equal(r.status,1,r.output);
  assert.equal(r.config,original); assert.equal(r.backup,'Different rollback config\n');
});
test('persistent health mismatch restores the original entry and explains why',async()=>{
  const r=await scenario('fail'); assert.equal(r.status,1,r.output);
  assert.equal(r.config,original); assert.match(r.output,/Cutover failed: HTTPS health still/);
  assert.match(r.output,/Previous entry restored/);
});
test('loopback success alone is insufficient if the public domain is wrong',async()=>{
  const r=await scenario('public-fail'); assert.equal(r.status,1,r.output);
  assert.equal(r.config,original); assert.match(r.output,/Public cutover failed/);
});
