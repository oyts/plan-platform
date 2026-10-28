// Local Windows updater + genuine TCP outage fixture. No Windows UI automation.
import {createServer} from 'node:https';
import {readFileSync,writeFileSync,createReadStream,mkdirSync,existsSync} from 'node:fs';
import {resolve,basename} from 'node:path';
import {createHash,X509Certificate} from 'node:crypto';
import {createFaultProxy,assertPortsFree} from './native-regression-support.mjs';
import {verifyUpdaterSignature} from './formal-release-support.mjs';
import {tamperWindowsSignature} from './update-lab.mjs';
const base=resolve('client-update-artifacts/local/stage5-pc-isolated'),tls=resolve('.desktop-tools/update-lab/tls');
const out=resolve('acceptance-results/update-experience/pc-lab',new Date().toISOString().replace(/[:.]/g,'-'));
const controlFile=resolve('.desktop-tools/update-experience-probe/control.json');
const allowed=['idle','optional','required','bad-signature','unavailable','online','offline','stop'];
if(process.argv[2]==='--control'){
 const command=process.argv[3];if(process.argv.length!==4||!allowed.includes(command)||!existsSync(controlFile))throw Error('Expected an allowed command for the running isolated probe.');
 const value=JSON.parse(readFileSync(controlFile,'utf8'));value.command=command;value.id=Date.now();writeFileSync(controlFile,JSON.stringify(value));console.log('Isolated probe command queued: '+command);process.exit(0);
}
if(process.argv.length!==2)throw Error('Usage: update-experience-probe.mjs [--control allowed-command]');
const ca=new X509Certificate(readFileSync(resolve(tls,'ca.pem')));
if(Date.parse(ca.validTo)<Date.now()+86400000)throw Error('Lab CA expired/expiring. Never disable TLS verification.');
const pub=readFileSync(resolve('.desktop-tools/test-signing/desktop-test.key.pub'),'utf8');
const entries={};
for(const [role,version] of [['bootstrap','0.1.24'],['target','0.1.25']]){
 const file=resolve(base,role,`Plan Cabin Update Probe_${version}_x64-setup.exe`),body=readFileSync(file),sig=readFileSync(file+'.sig','utf8').trim();
 verifyUpdaterSignature(body,pub,sig);entries[role]={version,file,sig,size:body.length,sha256:createHash('sha256').update(body).digest('hex')};
}
await assertPortsFree([43133,43187]);mkdirSync(out,{recursive:true});
let scenario='idle',online=true,stopping=false;
const report={schema:1,status:'RUNNING',startedAt:new Date().toISOString(),productionChanged:false,nativeApplicationDriven:false,
 identity:'cn.zijun701.plan.desktop.updateprobe',bootstrap:entries.bootstrap,target:entries.target,requests:[],commands:[]};
// Signature is public but unnecessary in evidence. Never capture API bodies or headers.
for(const e of [report.bootstrap,report.target])delete e.sig;
const signature=readFileSync(entries.target.file+'.sig','utf8').trim();
function save(){writeFileSync(resolve(out,'transport-report.json'),JSON.stringify(report,null,2));}
const gate=await createFaultProxy(43187,43180);
const server=createServer({pfx:readFileSync(resolve(tls,'server.p12')),passphrase:readFileSync(resolve(tls,'password.txt'),'utf8'),minVersion:'TLSv1.2'},(req,res)=>{
 res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
 const path=req.url?.split('?')[0];
 const kind=path==='/windows/latest.json'?'manifest':path==='/health'?'health':path===`/artifacts/${encodeURIComponent(basename(entries.target.file))}`?'artifact':'unknown';
 report.requests.push({at:new Date().toISOString(),kind,scenario});if(report.requests.length>500)report.requests.shift();save();
 if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);res.end();return;}
 if(kind==='health'){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({service:'stage5-isolated-update-probe',scenario,online}));return;}
 if(kind==='manifest'){
  if(scenario==='idle'){res.writeHead(204);res.end();return;}
  if(scenario==='unavailable'){res.writeHead(503);res.end();return;}
  res.writeHead(200,{'Content-Type':'application/json'});res.end(req.method==='HEAD'?undefined:JSON.stringify({version:entries.target.version,minimumSupportedVersion:scenario==='required'?entries.target.version:'0.0.0',notes:'本机隔离更新验收：仅合成数据，不是正式发布。',pub_date:'2026-10-28T00:00:00Z',platforms:{'windows-x86_64':{url:`https://127.0.0.1:43133/artifacts/${encodeURIComponent(basename(entries.target.file))}`,signature:scenario==='bad-signature'?tamperWindowsSignature(signature):signature}}}));return;
 }
 if(kind==='artifact'){
  const e=entries.target;if(createHash('sha256').update(readFileSync(e.file)).digest('hex')!==e.sha256){res.writeHead(409);res.end();return;}
  res.writeHead(200,{'Content-Type':'application/octet-stream','Content-Length':e.size});if(req.method==='HEAD'){res.end();return;}
  createReadStream(e.file).pipe(res);return;
 }
 res.writeHead(404);res.end();
});
await new Promise((ok,bad)=>{server.once('error',bad);server.listen(43133,'127.0.0.1',ok)});save();
mkdirSync(resolve(controlFile,'..'),{recursive:true});writeFileSync(controlFile,JSON.stringify({id:0,command:'idle'}),{mode:0o600});let lastId=0;
const polling=setInterval(()=>{try{const value=JSON.parse(readFileSync(controlFile,'utf8'));if(value.id===lastId)return;if(!Number.isSafeInteger(value.id)||!allowed.includes(value.command))return;lastId=value.id;const cmd=value.command;if(cmd==='stop'){void stop();return;}if(['online','offline'].includes(cmd)){online=cmd==='online';gate.setOnline(online);}else scenario=cmd;report.commands.push({at:new Date().toISOString(),command:cmd});save();console.log(JSON.stringify({scenario,online}));}catch{/* Atomic caller retry; never accept arbitrary input. */}},250);
async function stop(){if(stopping)return;stopping=true;clearTimeout(ttl);clearInterval(polling);await gate.close();await new Promise(ok=>server.close(ok));report.status='STOPPED';save();}
const ttl=setTimeout(()=>void stop(),7200000);for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>void stop());
console.log('Isolated update probe: '+out+'; API gate43187, HTTPS43133. Use --control with an allowed command.');
