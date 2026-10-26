// Loopback-only lifecycle channel for synthetic test helpers. Never bundled in apps.
import {createServer} from 'node:http';import {randomBytes} from 'node:crypto';import {writeFile} from 'node:fs/promises';
export async function fixtureControl(file,stop){
 const nonce=randomBytes(32).toString('hex');let requested=false;
 const server=createServer((req,res)=>{if(req.method!=='POST'||req.url!=='/stop'||req.headers['x-plan-control']!==nonce){res.writeHead(404);res.end();return;}res.writeHead(204,{'Cache-Control':'no-store'});res.end();if(!requested){requested=true;setTimeout(stop,50);}});
 await new Promise((ok,fail)=>{server.once('error',fail);server.listen(0,'127.0.0.1',ok);});
 await writeFile(file,JSON.stringify({schema:1,host:'127.0.0.1',port:server.address().port,nonce}),{flag:'wx',mode:0o600});
 return {close(){server.close();}};
}
