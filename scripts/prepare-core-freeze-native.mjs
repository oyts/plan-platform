// Record exact independent debug bytes, never install or alter a formal identity.
import {readFile,mkdir,copyFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {sourceFingerprint} from './native-regression-support.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
if(process.argv.length!==2)throw Error('No device, signing or destination overrides allowed');
const version=JSON.parse(await readFile(join(root,'web/package.json'),'utf8')).version;
if(version!=='0.1.26')throw Error('Review candidate scope for a later version');
const out=join(root,'acceptance-results/core-freeze/native-candidate-0126-r2');
const exe=join(root,'.desktop-tools/native-regression/plan-regression.exe'),apk=join(out,'android/PlanCabin-0.1.26-debug.apk'),api=join(root,'backend/target-core-freeze/plan-platform-api.jar');
const sha=async p=>createHash('sha256').update(await readFile(p)).digest('hex');
await mkdir(join(out,'windows'),{recursive:true});
const target=join(out,'windows/plan-regression.exe');
try{await copyFile(exe,target,1);}catch(e){if(e.code!=='EEXIST'||await sha(exe)!==await sha(target))throw e;}
const manifest={schema:1,version,androidCode:31,debugOnly:true,formalPublicationAllowed:false,desktopIdentifier:'cn.zijun701.plan.desktop.regression',androidPackage:'cn.zijun701.plan.mobile.regression',sourceSha256:await sourceFingerprint(root),apiSha256:await sha(api),desktopSha256:await sha(target),apkSha256:await sha(apk),deviceTested:false,createdAt:new Date().toISOString()};
await writeFile(join(out,'candidate.json'),JSON.stringify(manifest,null,2)+'\n',{flag:'wx'});
console.log('Independent candidate recorded; no UI launch, installation or formal publication.');
