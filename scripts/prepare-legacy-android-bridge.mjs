import {readFileSync,writeFileSync,mkdirSync,mkdtempSync,copyFileSync,realpathSync,unlinkSync,rmdirSync} from 'node:fs';
import {join,resolve,relative,isAbsolute} from 'node:path';
import {tmpdir} from 'node:os';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {privatePublication,sha256,CERTIFICATE} from './invited-android-release.mjs';
import {validateApkEvidence,verifyEmbeddedAndroidUpdateSource} from './formal-release-support.mjs';
import {bridgeManifest,bridgeConfig,bridgePaths} from './legacy-android-bridge.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const [candidateArg,baseArg,hoursArg='24']=process.argv.slice(2);
const production=realpathSync(join(root,'production-artifacts'));
function within(path){const actual=realpathSync(resolve(path));const part=relative(production,actual);assert.ok(part&&!part.startsWith('..')&&!isAbsolute(part));return actual;}
const candidate=within(candidateArg),baseFile=within(baseArg),version=JSON.parse(readFileSync(join(root,'web/package.json'),'utf8')).version;
const data=JSON.parse(readFileSync(join(candidate,'metadata/candidate.json'),'utf8'));
const apk=readFileSync(join(candidate,'android',`PlanCabin-${version}-release.apk`));
const validated=privatePublication(data,apk,version,'旧版兼容升级：安装后使用登录保护的更新通道。自动下载仍由用户选择，安装需系统确认。',1);
// Recheck actual signed bytes rather than trusting candidate metadata alone.
const sdk=join(root,'.desktop-tools/android-sdk/build-tools/36.0.0');
const jdk='E:/biancheng/JDK21/bin';
const temporary=mkdtempSync(join(tmpdir(),'plan-legacy-apk-'));
let dexFiles=[];
try {
  writeFileSync(join(temporary,'client.apk'),apk,{flag:'wx'});
  const badging=execFileSync(join(sdk,'aapt.exe'),['dump','badging','client.apk'],{cwd:temporary,encoding:'utf8'});
  const signatures=execFileSync(join(jdk,'java.exe'),['-jar',join(sdk,'lib/apksigner.jar'),'verify','--verbose','--print-certs','client.apk'],{cwd:temporary,encoding:'utf8'});
  validateApkEvidence(badging,signatures,version,validated.versionCode,CERTIFICATE);
  dexFiles=execFileSync(join(jdk,'jar.exe'),['tf','client.apk'],{cwd:temporary,encoding:'utf8'}).split(/\r?\n/).filter(f=>/^classes\d*\.dex$/.test(f));
  assert.ok(dexFiles.length);
  execFileSync(join(jdk,'jar.exe'),['xf','client.apk',...dexFiles],{cwd:temporary});
  verifyEmbeddedAndroidUpdateSource(dexFiles.map(f=>readFileSync(join(temporary,f))));
} finally {
  for(const f of ['client.apk',...dexFiles])unlinkSync(join(temporary,f));
  rmdirSync(temporary);
}
const hours=Number(hoursArg);assert.ok(Number.isInteger(hours)&&hours>=1&&hours<=72);
const expires=Math.floor(Date.now()/1000)+hours*3600;
const stages=join(production,'legacy-android');mkdirSync(stages,{recursive:true});
const output=mkdtempSync(join(stages,'bridge-')),run=output.split(/[\\/]/).at(-1);
const manifest=bridgeManifest(validated,run,expires),paths=bridgePaths(run,version);
const base=readFileSync(baseFile,'utf8').replaceAll('\r\n','\n');
const config=bridgeConfig(base,run,manifest,expires);
const testBase='server {\n  listen __PORT__;\n'+
  '  location = /client-updates/android/latest.json { return 404; }\n'+
  '  location / { return 404; }\n}\n';
const expiryTest='worker_processes 1;\npid /tmp/nginx.pid;\nevents { worker_connections 32; }\nhttp {\n'+
  bridgeConfig(testBase.replace('__PORT__','18080'),run,manifest,expires)+
  bridgeConfig(testBase.replace('__PORT__','18081'),run,manifest,Math.floor(Date.now()/1000)-60)+'}\n';
const plan={scope:'TEMPORARY_UNAUTHENTICATED_LEGACY_ANDROID_MIGRATION',run,version,versionCode:validated.versionCode,expires,expiresAtUtc:manifest.migrationExpiresAt,expiresAtShanghai:new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',dateStyle:'short',timeStyle:'medium'}).format(new Date(expires*1000)),hours,baseConfigSha256:sha256(Buffer.from(base)),activeConfigSha256:sha256(Buffer.from(config)),apkSha256:sha256(apk),apkBytes:apk.length,apkUri:paths.uri,apkFile:paths.file,publicWebsiteAndroid:false,publicRegistration:false};
writeFileSync(join(output,'bridge-plan.json'),JSON.stringify(plan,null,2)+'\n',{flag:'wx'});
writeFileSync(join(output,'latest.json'),JSON.stringify(manifest,null,2)+'\n',{flag:'wx'});
writeFileSync(join(output,'bridge-nginx.conf'),config,{flag:'wx'});
writeFileSync(join(output,'nginx-expiry-test.conf'),expiryTest,{flag:'wx'});
copyFileSync(baseFile,join(output,'nginx.expected-before.conf'));
writeFileSync(join(output,paths.file),apk,{flag:'wx'});
const script=readFileSync(join(root,'scripts/publish-legacy-android-bridge.sh'),'utf8').replaceAll('__RUN__',run).replaceAll('__BASE_HASH__',plan.baseConfigSha256).replaceAll('__ACTIVE_HASH__',plan.activeConfigSha256).replaceAll('__APK_FILE__',paths.file).replaceAll('__APK_HASH__',plan.apkSha256).replaceAll('__EXPIRES__',String(expires));
writeFileSync(join(output,'publish.sh'),script,{flag:'wx'});
const files=['bridge-plan.json','latest.json','bridge-nginx.conf','nginx-expiry-test.conf','nginx.expected-before.conf',paths.file,'publish.sh'];
writeFileSync(join(output,'upload.sha256'),files.map(f=>sha256(readFileSync(join(output,f)))+'  '+f).join('\n')+'\n',{flag:'wx'});
execFileSync('tar',['-czf',join(output,'upload.tar.gz'),'-C',output,...files,'upload.sha256']);
console.log(JSON.stringify({...plan,directory:output,archiveSha256:sha256(readFileSync(join(output,'upload.tar.gz')))},null,2));
