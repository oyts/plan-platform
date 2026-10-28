import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {inspectApk} from './update-lab.mjs';
const root=resolve('client-update-artifacts/local/stage5-android'),out=resolve(root,'metadata.json');
if(existsSync(out))throw Error('Existing probe metadata retained; inspect before rebuilding.');
const report={schema:1,packageName:'cn.zijun701.plan.mobile.updateprobe',productionChanged:false,deviceTested:false};
for(const [role,version,code] of [['bootstrap','0.1.25',30],['target','0.1.26',31]]){
 const file=resolve(root,role,`PlanCabin-${version}-debug.apk`),body=readFileSync(file),meta=inspectApk(file,report.packageName);
 if(meta.version!==version||meta.versionCode!==code)throw Error('Actual probe version/code differs.');
 report[role]={file,version,versionCode:code,signer:meta.signer,sha256:createHash('sha256').update(body).digest('hex'),size:body.length};
}
if(report.bootstrap.signer!==report.target.signer)throw Error('Same probe signing identity required; never uninstall to bypass.');
writeFileSync(out,JSON.stringify(report,null,2),{flag:'wx'});console.log('Android update probe pair verified; not device acceptance.');
