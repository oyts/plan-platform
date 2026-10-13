import {readFileSync,writeFileSync,mkdirSync,mkdtempSync,realpathSync,statSync,copyFileSync,unlinkSync,rmdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve,relative,sep,isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {privatePublication,CERTIFICATE} from './invited-android-release.mjs';
import {validateApkEvidence,verifyEmbeddedAndroidUpdateSource} from './formal-release-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const [candidateArg,notesArg,minimumArg='1']=process.argv.slice(2);
if(!candidateArg||!notesArg) throw new Error('Supply verified candidate directory and release notes text file.');
const production=realpathSync(join(root,'production-artifacts'));
const within=path=>{const actual=realpathSync(path),part=relative(production,actual);if(!part||part==='..'||part.startsWith('..'+sep)||isAbsolute(part))throw new Error('Input must be within production-artifacts.');return actual;};
const candidateRoot=within(candidateArg);
const metadataFile=within(join(candidateRoot,'metadata/candidate.json'));
if(statSync(metadataFile).size>128*1024)throw new Error('Unexpected metadata size.');
const candidate=JSON.parse(readFileSync(metadataFile,'utf8'));
const version=JSON.parse(readFileSync(join(root,'web/package.json'),'utf8')).version;
// Never trust a filename from candidate metadata as a path.
const apk=within(join(candidateRoot,'android',`PlanCabin-${version}-release.apk`));
if(statSync(apk).size>256*1024*1024) throw new Error('APK too large.');
const bytes=readFileSync(apk),notes=readFileSync(notesArg,'utf8');
const manifest=privatePublication(candidate,bytes,version,notes,Number(minimumArg));
const sdk=process.env.ANDROID_HOME||join(root,'.desktop-tools/android-sdk');
const java=join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/java.exe');
// aapt on Windows does not reliably accept Unicode APK paths. The immutable
// candidate is copied to a fresh temporary directory for verification only.
const temporary=mkdtempSync(join(tmpdir(),'plan-private-apk-'));
let dexFiles=[];
try {
  copyFileSync(apk,join(temporary,'client.apk'));
  const badging=execFileSync(join(sdk,'build-tools/36.0.0/aapt.exe'),['dump','badging','client.apk'],{cwd:temporary,encoding:'utf8'});
  const signatures=execFileSync(java,['-jar',join(sdk,'build-tools/36.0.0/lib/apksigner.jar'),'verify','--verbose','--print-certs','client.apk'],{cwd:temporary,encoding:'utf8'});
  validateApkEvidence(badging,signatures,version,manifest.versionCode,CERTIFICATE);
  const jar=join(process.env.JAVA_HOME||'E:/biancheng/JDK21','bin/jar.exe');
  dexFiles=execFileSync(jar,['tf','client.apk'],{cwd:temporary,encoding:'utf8'}).split(/\r?\n/).filter(name=>/^classes\d*\.dex$/.test(name));
  if(!dexFiles.length)throw new Error('APK bytecode missing.');
  execFileSync(jar,['xf','client.apk',...dexFiles],{cwd:temporary});
  verifyEmbeddedAndroidUpdateSource(dexFiles.map(name=>readFileSync(join(temporary,name))));
} finally {for(const name of ['client.apk',...dexFiles])unlinkSync(join(temporary,name));rmdirSync(temporary);}
const stages=join(production,'invited-android',version);mkdirSync(stages,{recursive:true});
const output=mkdtempSync(join(stages,'candidate-'));
writeFileSync(join(output,`PlanCabin-${version}-release.apk`),bytes,{flag:'wx'});
for(const name of ['release.json','current.json'])writeFileSync(join(output,name),JSON.stringify(manifest,null,2)+'\n',{flag:'wx'});
writeFileSync(join(output,'publish.sh'),readFileSync(join(root,'scripts/publish-invited-android.sh')),{flag:'wx'});
console.log(`Private Android publication staged: ${output}`);
console.log('No public APK, website download, account creation, installation or server change.');
