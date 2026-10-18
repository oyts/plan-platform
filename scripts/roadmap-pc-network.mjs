// Owns only the PC regression TCP proxy. File control survives closed stdin;
// no OS network settings or formal client process is modified.
import {readFile,writeFile,realpath} from 'node:fs/promises';
import {resolve,join,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertPortsFree,createFaultProxy,PORTS} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const [command,directory]=process.argv.slice(2);
if(!['serve','online','offline','stop'].includes(command)||!directory)throw new Error('Use serve|online|offline|stop with an existing synthetic PC run directory.');
const dir=await realpath(resolve(directory)),allowed=await realpath(join(root,'acceptance-results/roadmap-pc'));
if(!dir.startsWith(allowed+sep))throw new Error('Control path must stay in the isolated PC evidence directory.');
const statePath=join(dir,'network-switch.json');
if(command!=='serve'){await writeFile(statePath,JSON.stringify({mode:command}));console.log(`Synthetic proxy command: ${command}`);}
else{
 const healthy=await fetch(`http://127.0.0.1:${PORTS.api}/api/health`).then(r=>r.json());
 if(healthy.service!=='plan-platform-api'||healthy.version!=='0.1.15'||healthy.ok!==true)throw new Error('Expected isolated candidate API.');
 const fixture=JSON.parse(await readFile(join(dir,'private-fixture.json'),'utf8'));
 if(fixture.port!==PORTS.api||fixture.proxyPort!==PORTS.windowsProxy)throw new Error('Incorrect fixture ports.');
 await assertPortsFree([PORTS.windowsProxy]);
 const proxy=await createFaultProxy(PORTS.windowsProxy,PORTS.api);
 await writeFile(statePath,JSON.stringify({mode:'online'}));let previous='online',stopped=false;
 async function stop(){if(stopped)return;stopped=true;clearInterval(timer);clearTimeout(ttl);await proxy.close();}
 const timer=setInterval(async()=>{try{const text=await readFile(statePath,'utf8');if(text.length>100)throw new Error('Invalid control');const {mode}=JSON.parse(text);if(!['online','offline','stop'].includes(mode))throw new Error('Invalid mode');if(mode==='stop')await stop();else if(previous!==mode){proxy.setOnline(mode==='online');previous=mode;console.log(`Synthetic proxy ${mode.toUpperCase()}`);}}catch{proxy.setOnline(false);previous='offline';}},200);
 const ttl=setTimeout(()=>void stop(),3*3600_000);
 process.on('SIGINT',()=>void stop());process.on('SIGTERM',()=>void stop());
 console.log('PC synthetic proxy ready; real Java API retained.');
}
