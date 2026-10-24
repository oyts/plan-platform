import {spawn} from 'node:child_process';
import {mkdir,readFile,realpath,stat} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {join,delimiter,resolve,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertPortsFree} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));await assertPortsFree([43174]);
const args=process.argv.slice(2),base=await realpath(join(root,'acceptance-results/ai-long'));
if(args.length&&!(args.length===2&&args[0]==='--resume'))throw Error('Use --resume with an existing isolated fixture directory');
const out=args.length?await realpath(resolve(root,args[1])):join(base,new Date().toISOString().replace(/[:.]/g,'-'));
if(args.length){const name=relative(base,out);if(!/^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z$/.test(name)||!(await stat(join(out,'synthetic-db.mv.db'))).isFile())throw Error('Only a retained timestamped synthetic database may be resumed');}
else await mkdir(out,{recursive:false});
const target=join(root,'backend/target-ai-long'),dependencies=(await readFile(join(target,'classpath.txt'),'utf8')).trim();
const cp=[join(target,'test-classes'),join(target,'classes'),dependencies].join(delimiter),log=createWriteStream(join(out,args.length?'fixture-resume-'+Date.now()+'.log':'fixture.log'),{flags:'wx'});
const child=spawn(join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe'),['-Duser.timezone=Asia/Shanghai','-cp',cp,'com.oyts.planplatform.ai.AiLongPlanningFixture',out],{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(log);child.stderr.pipe(log);let stopped=false;function stop(){if(stopped)return;stopped=true;clearTimeout(ttl);if(child.exitCode===null)child.kill();}
const ttl=setTimeout(stop,7200_000);for(const signal of ['SIGINT','SIGTERM'])process.on(signal,stop);process.stdin.setEncoding('utf8');process.stdin.on('data',s=>{if(s.trim()==='stop')stop();});
child.on('exit',code=>{clearTimeout(ttl);log.end();process.stdin.pause();process.exitCode=code??0;});console.log('Synthetic fixture directory: '+out);
