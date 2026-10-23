// Test-classpath-only provider and isolated H2. Never loads production credentials.
import {spawn} from 'node:child_process';
import {mkdir,readFile,realpath,stat} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {join,delimiter,resolve,relative,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertPortsFree} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));await assertPortsFree([43171]);
const args=process.argv.slice(2),stamp=new Date().toISOString().replace(/[:.]/g,'-');
if(args.length && (args.length!==2||args[0]!=='--resume'))throw Error('Use --resume with one retained synthetic directory.');
const allowed=join(root,'acceptance-results/ai-reliability');let out;
if(args.length){
  out=await realpath(resolve(root,args[1]));const base=await realpath(allowed),part=relative(base,out);
  if(!part||part.startsWith('..')||isAbsolute(part)||!/^\d{4}-\d{2}-\d{2}T[\d-]+Z$/.test(part)||(await stat(join(out,'synthetic-db.mv.db'))).isFile()!==true)throw Error('A retained synthetic H2 fixture directory is required.');
}else{out=join(allowed,stamp);await mkdir(out,{recursive:true});}
const target=join(root,'backend/target-ai-reliability'),dependencies=(await readFile(join(target,'classpath.txt'),'utf8')).trim();
const cp=[join(target,'test-classes'),join(target,'classes'),dependencies].join(delimiter);
const log=createWriteStream(join(out,args.length?`fixture-resume-${stamp}.log`:'fixture.log'),{flags:'wx'});
const child=spawn(join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe'),['-Duser.timezone=Asia/Shanghai','-cp',cp,'com.oyts.planplatform.ai.AiReliabilityFixture',out],{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(log);child.stderr.pipe(log);
let stopped=false;function stop(){if(stopped)return;stopped=true;clearTimeout(ttl);if(child.exitCode===null)child.kill();}
const ttl=setTimeout(stop,3600_000);process.on('SIGINT',stop);process.on('SIGTERM',stop);process.stdin.setEncoding('utf8');process.stdin.on('data',s=>{if(s.trim()==='stop')stop();});
child.on('exit',code=>{clearTimeout(ttl);log.end();process.stdin.pause();process.exitCode=code??0;});
console.log('Synthetic fixture directory: '+out);
