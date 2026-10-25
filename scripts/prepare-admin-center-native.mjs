// Evidence/login preparation only. Does not launch, install or drive a client.
import {readFile,writeFile,mkdir,realpath} from 'node:fs/promises';import {resolve,relative} from 'node:path';import {createHash} from 'node:crypto';import {sourceFingerprint} from './native-regression-support.mjs';
const root=process.cwd(),base=await realpath(resolve('acceptance-results/admin-center')),folder=await realpath(resolve(process.argv[2]??''));
if(!/^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z$/.test(relative(base,folder)))throw Error('Expected retained isolated fixture directory');
const data=JSON.parse(await readFile(resolve(folder,'synthetic-login.json'),'utf8')),out=resolve('acceptance-results/admin-center/native');await mkdir(out,{recursive:true});
const sha=async path=>createHash('sha256').update(await readFile(path)).digest('hex');
const evidence={schema:1,sourceSha256:await sourceFingerprint(root),apiSha256:await sha('backend/target-admin/plan-platform-api.jar'),exeSha256:await sha('.desktop-tools/native-regression/plan-regression.exe'),server:'http://127.0.0.1:43180',fixture:relative(root,folder),windowsIdentifier:'cn.zijun701.plan.desktop.regression',createdAt:new Date().toISOString()};
await writeFile(resolve(out,'build.json'),JSON.stringify(evidence,null,2));
await writeFile(resolve(out,'synthetic-login.txt'),`Only independent regression client, server ${evidence.server}\nEmail: ${data.admin.email}\nPassword: ${data.admin.password}\nNever use a production account.\n`,{mode:0o600});
console.log('Native evidence and owner-only synthetic login prepared: '+out);
