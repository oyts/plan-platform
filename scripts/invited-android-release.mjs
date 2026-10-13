import {createHash} from 'node:crypto';
export const PACKAGE='cn.zijun701.plan.app';
export const CERTIFICATE='4528eeee02c43df6a4429481c99f6c39d459c82d9ca1c538f316e2ff37c68e9d';
export const sha256=bytes=>createHash('sha256').update(bytes).digest('hex');
export function privatePublication(candidate,apk,version,notes,minimum=1) {
  const data=candidate.android;
  if(candidate.status!=='LOCAL_SIGNED_CANDIDATE'||candidate.version!==version||!/^\d+\.\d+\.\d+$/.test(version)
      ||data?.version!==version||data.packageName!==PACKAGE||data.certificateSHA256!==CERTIFICATE
      ||data.debuggable!==false||data.cleartextTraffic!==false||data.file!==`PlanCabin-${version}-release.apk`
      ||!Number.isInteger(data.versionCode)||data.versionCode<10
      ||data.bytes!==apk.length||apk.length<1||apk.length>256*1024*1024||data.sha256!==sha256(apk))
    throw new Error('Expected unchanged, verified formal Android candidate.');
  if(!Number.isInteger(minimum)||minimum<1||minimum>data.versionCode) throw new Error('Invalid minimum supported Android code.');
  if(typeof notes!=='string'||!notes.trim()||notes.length>4000) throw new Error('Supply truthful release notes.');
  return {version,versionCode:data.versionCode,minimumSupportedVersionCode:minimum,sha256:data.sha256,size:apk.length,notes,packageName:PACKAGE};
}
