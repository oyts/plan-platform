// Local synthetic fixture evidence only; no launch, install, authentication or production data.
import {readFile,writeFile,mkdir,realpath} from 'node:fs/promises';import {resolve,relative} from 'node:path';import {createHash} from 'node:crypto';import {sourceFingerprint} from './native-regression-support.mjs';
const root=process.cwd(),base=await realpath(resolve('acceptance-results/admin-center')),folder=await realpath(resolve(process.argv[2]??''));
if(!/^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z$/.test(relative(base,folder)))throw Error('Expected isolated synthetic fixture');
const data=JSON.parse(await readFile(resolve(folder,'synthetic-login.json'),'utf8')),out=resolve('acceptance-results/metrics/native');await mkdir(out,{recursive:true});
const sha=async path=>createHash('sha256').update(await readFile(path)).digest('hex');
const evidence={schema:1,sourceSha256:await sourceFingerprint(root),apiSha256:await sha('backend/target-metrics/plan-platform-api.jar'),exeSha256:await sha('.desktop-tools/native-regression/plan-regression.exe'),server:'http://127.0.0.1:43180',fixture:relative(root,folder),windowsIdentifier:'cn.zijun701.plan.desktop.regression',createdAt:new Date().toISOString()};
const version=JSON.parse(await readFile('web/package.json','utf8')).version;evidence.apkName=`PlanCabin-${version}-debug.apk`;evidence.apkSha256=await sha(resolve('.desktop-tools/native-regression',evidence.apkName));evidence.androidPackage='cn.zijun701.plan.mobile.regression';
await writeFile(resolve(out,'build.json'),JSON.stringify(evidence,null,2));
await writeFile(resolve(out,'synthetic-login.txt'),`Independent regression client only; server ${evidence.server}\nEmail: ${data.admin.email}\nPassword: ${data.admin.password}\nNever use a production account.\n`,{mode:0o600});
console.log('Synthetic native login and build hashes ready: '+out);
