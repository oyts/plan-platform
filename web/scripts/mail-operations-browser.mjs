// Direct child processes avoid npm/cmd leaving test servers behind on Windows.
// Mocked API UI checks only; does not claim backend or native-device acceptance.
import {spawn} from 'node:child_process';
import {createServer} from 'node:net';
import {once} from 'node:events';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const guard=createServer();await new Promise((resolve,reject)=>{guard.once('error',reject);guard.listen(43150,'127.0.0.1',resolve);});await new Promise(resolve=>guard.close(resolve));
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','43150','--strictPort'],{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});
let output='';server.stdout.on('data',chunk=>output=(output+chunk).slice(-4000));server.stderr.on('data',chunk=>output=(output+chunk).slice(-4000));
let run;
try{
  let ready=false;
  for(let attempt=0;attempt<50;attempt++){
    if(server.exitCode!==null)throw new Error('Owned preview exited: '+output);
    try{const response=await fetch('http://127.0.0.1:43150/settings',{signal:AbortSignal.timeout(1000)});if(response.ok){ready=true;break;}}catch{}
    await new Promise(resolve=>setTimeout(resolve,200));
  }
  if(!ready)throw new Error('Owned preview did not start. No unrelated service was stopped.');
  run=spawn(process.execPath,['node_modules/@playwright/test/cli.js','test','--config=playwright.mail-operations.config.ts',...process.argv.slice(2)],{cwd:root,windowsHide:true,stdio:'inherit'});
  const [code]=await once(run,'exit');process.exitCode=code??1;
}finally{
  if(run&&run.exitCode===null)run.kill();
  if(server.exitCode===null){server.kill();await once(server,'exit');}
}
