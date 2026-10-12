import {createHash} from 'node:crypto';
export const signingFiles=['android-release.der','android-release.p12','windows-updater.key','windows-updater.key.pub'];
export function validateBackupIdentity(payload) {
  const exact=(value,names)=>value&&typeof value==='object'&&!Array.isArray(value)&&JSON.stringify(Object.keys(value).sort())===JSON.stringify([...names].sort());
  if(!payload||payload.schema!==1||payload.metadata?.schema!==1||payload.metadata.androidPackage!=='cn.zijun701.plan.app'||payload.metadata.alias!=='plan-cabin-release') throw new Error('Unexpected signing backup identity.');
  if(!exact(payload.files,signingFiles)||!exact(payload.metadata.files,signingFiles)||!exact(payload.credentials,['android','windows'])) throw new Error('Incomplete or unsafe signing backup entries.');
  for(const name of signingFiles) {
    const value=payload.files[name],hash=payload.metadata.files[name];
    if(typeof value!=='string'||value.length>128*1024||typeof hash!=='string'||!/^[a-f0-9]{64}$/.test(hash)) throw new Error('Invalid signing file or hash.');
    const bytes=Buffer.from(value,'base64');
    if(!bytes.length||bytes.toString('base64')!==value||createHash('sha256').update(bytes).digest('hex')!==hash) throw new Error('Signing backup file hash mismatch.');
  }
  for(const value of Object.values(payload.credentials)) {
    if(typeof value!=='string'||Buffer.from(value,'base64').length!==32||Buffer.from(value,'base64').toString('base64')!==value) throw new Error('Unexpected signing password format.');
  }
  return payload;
}
