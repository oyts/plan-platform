import{readFile}from'node:fs/promises';import{resolve,relative,isAbsolute}from'node:path';
export default async function(){
 const base=resolve('../acceptance-results/core-freeze'),out=resolve(process.env.PLAN_CORE_RUN_DIRECTORY??''),part=relative(base,out);if(!part||part.startsWith('..')||isAbsolute(part))throw Error('Isolated fixture run required');
 for(const role of process.env.PLAN_CORE_BROWSER_KIND==='ui'?['preview']:['api','preview']){
  const c=JSON.parse(await readFile(resolve(out,role+'-control.json'),'utf8'));if(c.schema!==1||c.host!=='127.0.0.1'||!Number.isInteger(c.port)||c.port<1024||c.port>65535||!/^[a-f0-9]{64}$/.test(c.nonce))throw Error('Invalid fixture lifecycle control');
  const r=await fetch(`http://127.0.0.1:${c.port}/stop`,{method:'POST',headers:{'X-Plan-Control':c.nonce},signal:AbortSignal.timeout(5000)});if(r.status!==204)throw Error('Fixture stop refused');
 }
}
