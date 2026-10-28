// Fixed core-freeze suites. No production access, physical-device or signing action.
import {spawn,execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync,createWriteStream,existsSync} from 'node:fs';
import {resolve,join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {sourceFingerprint} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const scope=JSON.parse(readFileSync(join(root,'scripts/core-freeze-scope.json'),'utf8'));
const action=process.argv[2];if(!['backend','frontend','build','browser','browser-followup','browser-reliability','browser-long','browser-long-followup','browser-admin','browser-admin-followup','browser-metrics','browser-ui'].includes(action)||process.argv.length!==3)throw Error('Use an explicit fixed core-freeze suite; no device/production actions');
if(scope.version!==JSON.parse(readFileSync(join(root,'web/package.json'),'utf8')).version||scope.backendTarget!=='target-core-freeze')throw Error('Review scope/version before running');
for(const test of scope.backend)if(!/^[A-Za-z0-9]+Test$/.test(test))throw Error('Invalid test class');
for(const file of scope.frontend)if(!/^src\/[A-Za-z0-9_./]+\.test\.ts$/.test(file)||file.includes('..')||!existsSync(join(root,'web',file)))throw Error('Invalid frontend selection');
const out=join(root,'acceptance-results/core-freeze',new Date().toISOString().replace(/[:.]/g,'-')+'-'+action);mkdirSync(out,{recursive:true});
const report={schema:1,action,status:'RUNNING',version:scope.version,sourceCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),dirtyWorktree:!!execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim(),sharedClientSourceSha256:await sourceFingerprint(root),startedAt:new Date().toISOString(),productionWrites:false,deviceOperations:false,paidAiCalls:0,realEmails:0};
const env={...process.env,JAVA_HOME:process.env.JAVA_HOME||'E:/biancheng/JDK21'};
// Never inherit production connection/provider/mail settings into a test JVM.
for(const key of Object.keys(env))if(/^(AI_|DEEPSEEK_|SMTP_|MAIL_|DB_|MYSQL_|SPRING_DATASOURCE_|SPRING_MAIL_|ANDROID_|TAURI_SIGNING_)/.test(key))delete env[key];
env.PATH=dirname(process.execPath)+';'+join(env.JAVA_HOME,'bin')+';'+(env.PATH||'');
env.PLAYWRIGHT_BROWSERS_PATH=join(root,'.desktop-tools/playwright');
async function command(file,args,cwd,label){
 const log=createWriteStream(join(out,label+'.log'),{flags:'wx'});console.log('Starting fixed '+label+' suite; evidence '+out);
 const code=await new Promise((ok,bad)=>{const child=spawn(file,args,{cwd,env,windowsHide:true,stdio:['ignore','pipe','pipe']});child.stdout.pipe(log,{end:false});child.stderr.pipe(log,{end:false});child.on('error',bad);child.on('exit',c=>ok(c));});log.end();if(code!==0)throw Error(label+' exited '+code+'; retained log, no retry or changed assertions');
}
try{
 if(action==='backend'){
  const maven=process.env.PLAN_CORE_MAVEN||'E:/biancheng/maven/apache-maven-3.9.4-bin/apache-maven-3.9.4/bin/mvn.cmd';if(!existsSync(maven))throw Error('Set PLAN_CORE_MAVEN to an installed Maven3.9 executable');env.PLAN_CORE_MAVEN=maven;
  const args=['-B','-f','backend/pom.xml','-Dplan.build.directory=target-core-freeze','-Dtest='+scope.backend.join(','),'-Dspring.test.context.cache.maxSize=3','-DargLine=-Xmx1024m','package','dependency:build-classpath','-Dmdep.outputFile=target-core-freeze/classpath.txt'];
  await command('powershell.exe',['-NoProfile','-Command',"& $env:PLAN_CORE_MAVEN @("+args.map(a=>"'"+a+"'").join(',')+"); exit $LASTEXITCODE"],root,'backend');
  report.selectedClasses=scope.backend;report.surefireDirectory='backend/target-core-freeze/surefire-reports';
 }
 if(action==='frontend'){
  await command(process.execPath,['node_modules/vitest/vitest.mjs','run',...scope.frontend,'src/weeklyDraft.test.ts','--reporter=json','--outputFile='+join(out,'vitest.json')],join(root,'web'),'frontend');
  const result=JSON.parse(readFileSync(join(out,'vitest.json'),'utf8'));if(!result.success||result.numFailedTests)throw Error('Frontend result is not a pass');report.testsPassed=result.numPassedTests;report.testsFailed=result.numFailedTests;
 }
 if(action==='build'){
  await command(process.execPath,['node_modules/vue-tsc/bin/vue-tsc.js','-b'],join(root,'web'),'types');
  await command(process.execPath,['node_modules/vite/bin/vite.js','build','--config','vite.config.ts'],join(root,'web'),'web-build');
 }
 if(action==='browser'||action.startsWith('browser-')){
  if(!existsSync(join(root,'backend/target-core-freeze/plan-platform-api.jar')))throw Error('Build the isolated core-freeze API first');
  env.PLAN_CORE_BROWSER_OUTPUT=join(out,'artifacts');env.PLAN_CORE_RUN_DIRECTORY=out;env.PLAYWRIGHT_JSON_OUTPUT_FILE=join(out,'playwright.json');if(action==='browser-followup')env.PLAN_CORE_FOLLOWUP='1';
  const kind=action==='browser-long-followup'?'long':action==='browser-admin-followup'?'admin':action.slice('browser-'.length),specialized=['reliability','long','admin','metrics','ui'].includes(kind);if(specialized)env.PLAN_CORE_BROWSER_KIND=kind;if(action==='browser-long-followup')env.PLAN_CORE_LONG_FOLLOWUP='1';if(kind==='admin')env.PLAN_FEEDBACK_API='http://127.0.0.1:43180/api';if(action==='browser-admin-followup')env.PLAN_CORE_FEEDBACK_FOLLOWUP='1';
  await command(process.execPath,['node_modules/@playwright/test/cli.js','test','--config='+(specialized?'playwright.core-specialized.config.ts':'playwright.core-freeze.config.ts'),'--reporter=json'],join(root,'web'),'browser');
  const result=JSON.parse(readFileSync(join(out,'playwright.json'),'utf8'));if(result.stats.unexpected||result.errors?.length)throw Error('Browser report contains failures');report.stats=result.stats;
 }
 report.status='PASS';
}catch(e){report.status='FAIL';report.error=e.message;console.error(e.message);process.exitCode=1;}
finally{report.finishedAt=new Date().toISOString();writeFileSync(join(out,'report.json'),JSON.stringify(report,null,2),{flag:'wx'});console.log('Core-freeze result '+report.status+': '+out);}
