// Owner-authorized GitHub release. Existing Git authentication stays in memory;
// never print/store credentials, invoke interactive login, or upload APK/private data.
import assert from 'node:assert/strict';
import {readFileSync,realpathSync} from 'node:fs';
import {resolve,relative,isAbsolute,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {sha256} from './windows-public-release.mjs';
import {verifyUpdaterSignature} from './formal-release-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const version=JSON.parse(readFileSync(join(root,'web/package.json'),'utf8')).version;
assert.match(version,/^\d+\.\d+\.\d+$/);
const [mode,stageArg]=process.argv.slice(2);
assert.ok(['--check','--publish'].includes(mode),'Use --check or --publish <accepted Windows stage>');
const repository='oyts/plan-platform',api=`https://api.github.com/repos/${repository}`;
let token;
try {
  let credential;
  try {
    credential=execFileSync('git',['-c','credential.interactive=false','credential','fill'],{
      cwd:root,input:`protocol=https\nhost=github.com\npath=${repository}.git\n\n`,encoding:'utf8',windowsHide:true,
      env:{...process.env,GIT_TERMINAL_PROMPT:'0',GCM_INTERACTIVE:'Never'},stdio:['pipe','pipe','pipe'],timeout:30000,
    });
    token=credential.split(/\r?\n/).find(line=>line.startsWith('password='))?.slice(9);
    credential=undefined;
  } catch { throw new Error('Existing owner Git authentication unavailable. No interactive login or publication was attempted.'); }
  assert.ok(token,'Existing Git credential did not supply GitHub authentication.');
  async function request(url,{method='GET',body,contentType='application/json'}={}) {
    const parsed=new URL(url);
    assert.ok(parsed.protocol==='https:'&&['api.github.com','uploads.github.com'].includes(parsed.hostname)&&!parsed.username&&!parsed.password);
    const response=await fetch(url,{method,redirect:'error',signal:AbortSignal.timeout(120000),
      headers:{Authorization:`Bearer ${token}`,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','Content-Type':contentType},
      body:body===undefined?undefined:Buffer.isBuffer(body)?body:JSON.stringify(body)});
    if(!response.ok&&response.status!==404) throw new Error(`GitHub ${method} request failed with HTTP ${response.status}. Credentials and response body were not logged.`);
    return response.status===404 ? null : response.json();
  }
  assert.equal((await request('https://api.github.com/user')).login.toLowerCase(),'oyts','Unexpected GitHub owner');
  const repo=await request(api);assert.equal(repo?.full_name?.toLowerCase(),repository);assert.equal(repo.private,false);assert.equal(repo.permissions?.push,true);
  // The tag endpoint does not reliably surface unpublished drafts. Find the
  // exact authorized tag in the owner's release list, including drafts.
  const releases=await request(`${api}/releases?per_page=100`);
  const matching=releases.filter(release=>release.tag_name===`v${version}`);
  assert.ok(matching.length<=1,'Multiple releases for this tag; review manually, never delete them automatically.');
  const existing=matching[0];
  if(mode==='--check') {
    console.log(JSON.stringify({repository,authenticatedOwner:'oyts',writeAccess:true,version,releaseExists:!!existing,draft:existing?.draft??null}));
  } else {
    assert.equal(execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim(),'','Commit verified source and acceptance first.');
    const head=execFileSync('git',['rev-parse',`v${version}^{commit}`],{cwd:root,encoding:'utf8'}).trim();
    // A release-tool fix may follow an immutable tag, but business source and
    // release metadata must still be identical to that verified tagged source.
    execFileSync('git',['diff','--quiet',`v${version}`,'HEAD','--','backend','web','.env.example','docker-compose.production.yml'],{cwd:root,stdio:['ignore','pipe','pipe']});
    const production=realpathSync(join(root,'production-artifacts')),stage=realpathSync(resolve(stageArg||'')),rel=relative(production,stage);
    assert.ok(rel&&!rel.startsWith('..')&&!isAbsolute(rel));
    const plan=JSON.parse(readFileSync(join(stage,'metadata/release-plan.json'),'utf8'));
    assert.equal(plan.version,version);assert.equal(plan.androidPublished,false);assert.equal(plan.windows.installationAcceptanceConfirmed,true);
    const file=plan.windows.file;assert.equal(file,`Plan Cabin_${version}_x64-setup.exe`);
    const exe=readFileSync(join(stage,'public/downloads',version,file));assert.equal(sha256(exe),plan.windows.sha256);assert.equal(exe.length,plan.windows.bytes);
    const sig=readFileSync(join(stage,'public/client-updates/windows/releases',version,file+'.sig'));
    verifyUpdaterSignature(exe,readFileSync(join(root,'.release-signing/windows-updater.key.pub'),'utf8'),sig.toString('utf8'));
    // GitHub normalizes spaces to periods. Use that explicit transport name;
    // the candidate, official download name, bytes and signature stay unchanged.
    const assetName=file.replaceAll(' ','.');
    const sums=Buffer.from(`${sha256(exe)}  ${assetName}\n${sha256(sig)}  ${assetName}.sig\n`);
    const assets=[[assetName,exe,'application/octet-stream'],[assetName+'.sig',sig,'text/plain'],['SHA256SUMS.txt',sums,'text/plain']];
    // Verify public release state before creating any GitHub asset. No GitHub
    // credential is attached to the product domain requests.
    const publicJson=async path=>{
      const response=await fetch('https://plan.zijun701.cn'+path,{redirect:'error',signal:AbortSignal.timeout(30000)});
      assert.equal(response.status,200,'Production release endpoint is not ready.');return response.json();
    };
    assert.equal((await publicJson('/api/health')).version,version);
    assert.equal((await publicJson('/api/auth/config')).registrationEnabled,false);
    const downloads=await publicJson('/downloads/manifest.json');assert.equal(downloads.android,null);assert.equal(downloads.windows?.sha256,plan.windows.sha256);
    assert.equal((await publicJson('/client-updates/windows/latest.json')).version,version);
    const notes=readFileSync(join(root,`docs/release-${version.replaceAll('.','')}.md`),'utf8');
    let release=existing;
    if(release) assert.equal(release.draft,true,'Published releases are immutable; do not replace existing assets.');
    else release=await request(`${api}/releases`,{method:'POST',body:{tag_name:`v${version}`,target_commitish:head,name:`计划舱 ${version}`,body:notes,draft:true,prerelease:false}});
    const current=await request(`${api}/releases/${release.id}/assets`);
    for(const asset of current) assert.ok(assets.some(([name,bytes])=>asset.name===name&&asset.size===bytes.length&&asset.digest===`sha256:${sha256(bytes)}`),'Unexpected draft asset; review manually.');
    const uploadBase=release.upload_url.replace(/\{.*$/,'');assert.equal(new URL(uploadBase).hostname,'uploads.github.com');
    for(const [name,bytes,contentType] of assets) {
      if(current.some(asset=>asset.name===name)) continue;
      const uploaded=await request(`${uploadBase}?name=${encodeURIComponent(name)}`,{method:'POST',body:bytes,contentType});
      assert.equal(uploaded.name,name);assert.equal(uploaded.size,bytes.length);assert.equal(uploaded.digest,`sha256:${sha256(bytes)}`);
    }
    release=await request(`${api}/releases/${release.id}`,{method:'PATCH',body:{body:notes,draft:false}});
    assert.equal(release.draft,false);
    console.log(JSON.stringify({url:release.html_url,version,assets:assets.map(([name])=>name),androidPublished:false,sourceCommit:head}));
  }
} finally { token=undefined; }
