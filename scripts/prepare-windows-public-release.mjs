import {readFileSync,writeFileSync,mkdirSync,mkdtempSync,realpathSync,statSync,existsSync} from 'node:fs';
import {join,resolve,relative,isAbsolute,sep} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {verifyBackupCopy,windowsPublication,sha256} from './windows-public-release.mjs';

function within(parent,child) {
  const path=relative(parent,child);
  if(!path||path==='..'||path.startsWith('..'+sep)||isAbsolute(path)) throw new Error('Release path escapes its expected root.');
  return child;
}
function safeFile(parent,path,maxBytes) {
  const actual=within(realpathSync(parent),realpathSync(path));
  const stat=statSync(actual);
  if(!stat.isFile()||stat.size<1||stat.size>maxBytes) throw new Error('Unexpected release input type or size.');
  return readFileSync(actual);
}
function safeDirectories(parent,segments) {
  let directory=parent;
  for(const segment of segments) {
    const next=within(parent,join(directory,segment));
    if(!existsSync(next)) mkdirSync(next);
    directory=within(parent,realpathSync(next));
    if(!statSync(directory).isDirectory()) throw new Error('Unexpected output directory type.');
  }
  return directory;
}
export function prepareWindowsPublicRelease(root,{candidateDirectory,signingBackupDirectory,cloudBackupCopy,windowsAcceptanceConfirmed,oldCloudPairCleanupConfirmed,createdUtc=new Date().toISOString(),notes}) {
  if(windowsAcceptanceConfirmed!==true||oldCloudPairCleanupConfirmed!==true) throw new Error('Confirm formal Windows acceptance and old cloud backup pair cleanup first.');
  const production=realpathSync(join(root,'production-artifacts'));
  const candidateRoot=within(production,realpathSync(candidateDirectory));
  const candidate=JSON.parse(safeFile(candidateRoot,join(candidateRoot,'metadata/candidate.json'),128*1024).toString('utf8'));
  const expectedVersion=JSON.parse(readFileSync(join(root,'web/package.json'),'utf8')).version;
  if(!/^\d+\.\d+\.\d+$/.test(expectedVersion)||candidate.version!==expectedVersion||candidate.windows?.file!==`Plan Cabin_${expectedVersion}_x64-setup.exe`) throw new Error('Candidate filename or version differs from source.');
  const installer=join(candidateRoot,'windows',candidate.windows.file);
  const artifact=safeFile(candidateRoot,installer,512*1024*1024);
  const signature=safeFile(candidateRoot,installer+'.sig',8192).toString('utf8');
  const backupRoot=realpathSync(join(root,'.desktop-tools/signing-backups/private'));
  const backupDirectory=within(backupRoot,realpathSync(signingBackupDirectory));
  const evidence=JSON.parse(safeFile(backupDirectory,join(backupDirectory,'backup-check.json'),64*1024).toString('utf8'));
  const originalPath=realpathSync(join(backupDirectory,'PlanCabin.plan-signing-backup'));
  if(!cloudBackupCopy.endsWith('.plan-signing-backup')) throw new Error('Supply the downloaded encrypted backup, never a password file.');
  const independentPath=realpathSync(cloudBackupCopy);
  if(originalPath.toLowerCase()===independentPath.toLowerCase()||statSync(independentPath).size>2*1024*1024||!statSync(independentPath).isFile()) throw new Error('Supply a separate downloaded cloud backup copy.');
  const backupProof=verifyBackupCopy(evidence,safeFile(backupDirectory,originalPath,2*1024*1024),readFileSync(independentPath));
  const publicKey=readFileSync(join(root,'.release-signing/windows-updater.key.pub'),'utf8');
  const publication=windowsPublication({candidate,artifact,signature,publicKey,expectedVersion,backupProof,windowsAcceptanceConfirmed,oldCloudPairCleanupConfirmed,createdUtc,notes});
  // All checks precede output creation. Never rebuild, resign or replace a candidate.
  const runs=safeDirectories(production,['windows-public-release',expectedVersion]);
  const output=mkdtempSync(join(runs,createdUtc.replace(/[-:.]/g,'')+'-'));
  const files=[
    ['public/client-updates/windows/releases/'+expectedVersion+'/'+publication.file,artifact],
    ['public/client-updates/windows/releases/'+expectedVersion+'/'+publication.file+'.sig',Buffer.from(signature,'utf8')],
    ['public/downloads/'+expectedVersion+'/'+publication.file,artifact],
  ];
  for(const [destination,bytes] of files) {
    const path=join(output,destination);mkdirSync(resolve(path,'..'),{recursive:true});writeFileSync(path,bytes,{flag:'wx'});
    if(sha256(readFileSync(path))!==sha256(bytes)) throw new Error('Staged file copy hash mismatch. Nothing was published.');
  }
  for(const [path,data] of [['public/client-updates/windows/latest.json',publication.update],['public/downloads/manifest.json',publication.downloads],['metadata/release-plan.json',publication.plan]]) {
    const full=join(output,path);mkdirSync(resolve(full,'..'),{recursive:true});writeFileSync(full,JSON.stringify(data,null,2)+'\n',{flag:'wx'});
  }
  return {output,plan:publication.plan};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  const [candidateDirectory,signingBackupDirectory,cloudBackupCopy,...flags]=process.argv.slice(2);
  if(!candidateDirectory||!signingBackupDirectory||!cloudBackupCopy||flags.some(flag=>!['--windows-accepted','--old-cloud-pair-cleaned'].includes(flag))) throw new Error('Supply verified candidate, signing backup directory, downloaded cloud copy and explicit acceptance confirmations.');
  const result=prepareWindowsPublicRelease(fileURLToPath(new URL('../',import.meta.url)),{candidateDirectory,signingBackupDirectory,cloudBackupCopy,windowsAcceptanceConfirmed:flags.includes('--windows-accepted'),oldCloudPairCleanupConfirmed:flags.includes('--old-cloud-pair-cleaned')});
  console.log(`Windows publication prepared locally: ${result.output}`);
  console.log('Optional Windows only; Android and public registration remain disabled. No upload, installation or server change.');
}
