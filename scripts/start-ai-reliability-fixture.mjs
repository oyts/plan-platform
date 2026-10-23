// Test-classpath-only provider and isolated H2. Never loads production credentials.
import {spawn} from 'node:child_process';
import {mkdir,readFile} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {join,delimiter} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertPortsFree} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));await assertPortsFree([43171]);
const out=join(root,'acceptance-results/ai-reliability',new Date().toISOString().replace(/[:.]/g,'-'));await mkdir(out,{recursive:true});
const target=join(root,'backend/target-ai-reliability'),dependencies=(await readFile(join(target,'classpath.txt'),'utf8')).trim();
const cp=[join(target,'test-classes'),join(target,'classes'),dependencies].join(delimiter);
const log=createWriteStream(join(out,'fixture.log'),{flags:'wx'});
const child=spawn(join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe'),['-Duser.timezone=Asia/Shanghai','-cp',cp,'com.oyts.planplatform.ai.AiReliabilityFixture',out],{cwd:root,windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(log);child.stderr.pipe(log);
let stopped=false;function stop(){if(stopped)return;stopped=true;clearTimeout(ttl);if(child.exitCode===null)child.kill();}
const ttl=setTimeout(stop,3600_000);process.on('SIGINT',stop);process.on('SIGTERM',stop);process.stdin.setEncoding('utf8');process.stdin.on('data',s=>{if(s.trim()==='stop')stop();});
child.on('exit',code=>{clearTimeout(ttl);log.end();process.stdin.pause();process.exitCode=code??0;});
console.log('Synthetic fixture directory: '+out);
