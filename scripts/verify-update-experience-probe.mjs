// Run only against the explicitly started loopback probe in optional scenario.
import {request} from 'node:https';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {verifyUpdaterSignature} from './formal-release-support.mjs';
const ca=await readFile('.desktop-tools/update-lab/tls/ca.pem'),out=resolve('acceptance-results/update-experience/pc-lab/tls-guards-'+Date.now()+'.json');
const report={status:'FAIL',cases:[],nativeDeviceTested:false};
function get(path,options={}){return new Promise((ok,bad)=>{const req=request({hostname:'127.0.0.1',port:43133,path,ca,agent:false,timeout:5000,...options},res=>{let chunks=[];res.on('data',b=>chunks.push(b));res.on('end',()=>ok({status:res.statusCode,headers:res.headers,body:Buffer.concat(chunks)}));res.on('error',bad)});req.on('error',bad);req.on('timeout',()=>req.destroy(Error('Probe timeout')));req.end();});}
async function check(name,fn){const c={name,status:'FAIL'};report.cases.push(c);await fn();c.status='PASS';console.log('[PASS] '+name);}
try{
 await check('Trusted loopback HTTPS manifest and exact TEST signature',async()=>{
  const r=await get('/windows/latest.json');assert.equal(r.status,200);assert.equal(r.headers['cache-control'],'no-store');const m=JSON.parse(r.body);
  assert.equal(m.version,'0.1.25');assert.equal(m.minimumSupportedVersion,'0.0.0');const e=m.platforms['windows-x86_64'];assert.equal(new URL(e.url).origin,'https://127.0.0.1:43133');
  const file=await readFile('client-update-artifacts/local/stage5-pc-isolated/target/Plan Cabin Update Probe_0.1.25_x64-setup.exe');verifyUpdaterSignature(file,await readFile('.desktop-tools/test-signing/desktop-test.key.pub','utf8'),e.signature);
  const downloaded=await get(new URL(e.url).pathname);assert.equal(downloaded.status,200);assert.equal(createHash('sha256').update(downloaded.body).digest('hex'),createHash('sha256').update(file).digest('hex'));
 });
 await check('Untrusted CA refused',async()=>{await assert.rejects(()=>get('/health',{ca:undefined}));});
 await check('Wrong TLS hostname refused',async()=>{await assert.rejects(()=>get('/health',{servername:'localhost'}));});
 await check('No private files or path traversal exposed',async()=>{for(const p of ['/tls/password.txt','/.desktop-tools/update-lab/tls/server.p12','/artifacts/../ca.pem','/android/unknown.json'])assert.equal((await get(p)).status,404);});
 await check('Updater server refuses mutation',async()=>{assert.equal((await get('/windows/latest.json',{method:'POST'})).status,405);});
 await check('Real Rust transport outage gate reaches isolated API only',async()=>{const r=await fetch('http://127.0.0.1:43187/api/health');assert.equal(r.status,200);assert.equal((await r.json()).service,'plan-platform-api');});
 report.status='PASS';
}catch(e){report.error=e.message;process.exitCode=1;console.error('Stopped: '+e.message)}
finally{await mkdir(resolve(out,'..'),{recursive:true});await writeFile(out,JSON.stringify(report,null,2));console.log('Evidence: '+out);}
