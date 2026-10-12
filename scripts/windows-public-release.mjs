import {createHash} from 'node:crypto';
import {verifyUpdaterSignature} from './formal-release-support.mjs';

export const releaseOrigin='https://plan.zijun701.cn';
export const sha256=bytes=>createHash('sha256').update(bytes).digest('hex');
const versionPattern=/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

export function verifyBackupCopy(evidence,original,copy) {
  if(evidence?.schema!==1||evidence.restoreCryptographicProof!==true||evidence.originalSigningFilesUnchanged!==true||evidence.passphraseCustodyConfirmed!==true||
    !/^[a-f0-9]{64}$/.test(evidence.backupSha256||'')||!Number.isSafeInteger(evidence.backupBytes)||evidence.backupBytes<1||evidence.backupBytes>2*1024*1024) throw new Error('Signing backup acceptance is incomplete.');
  if(original.length!==evidence.backupBytes||copy.length!==evidence.backupBytes||sha256(original)!==evidence.backupSha256||sha256(copy)!==evidence.backupSha256) throw new Error('Independent cloud backup copy does not match the accepted original.');
  return {restoreCryptographicProof:true,passphraseCustodyConfirmed:true,independentCopyHashVerified:true,backupSha256:evidence.backupSha256};
}

export function windowsPublication({candidate,artifact,signature,publicKey,expectedVersion,backupProof,windowsAcceptanceConfirmed,oldCloudPairCleanupConfirmed,createdUtc,notes='计划舱正式 Windows 客户端；更新安装由用户确认。'}) {
  if(!versionPattern.test(expectedVersion||'')||candidate?.schemaVersion!==1||candidate.status!=='LOCAL_SIGNED_CANDIDATE'||candidate.version!==expectedVersion||candidate.publicRegistrationEnabled!==false) throw new Error('Expected a verified, version-consistent formal candidate.');
  if(windowsAcceptanceConfirmed!==true||oldCloudPairCleanupConfirmed!==true||backupProof?.independentCopyHashVerified!==true||backupProof?.restoreCryptographicProof!==true||backupProof?.passphraseCustodyConfirmed!==true) throw new Error('Formal Windows acceptance and separated backup custody must be confirmed.');
  const file=`Plan Cabin_${expectedVersion}_x64-setup.exe`,record=candidate.windows;
  if(record?.file!==file||record.updaterSignatureVerified!==true||record.bytes!==artifact.length||record.sha256!==sha256(artifact)) throw new Error('Windows artifact differs from the accepted candidate.');
  const verification=verifyUpdaterSignature(artifact,publicKey,signature);
  if(record.keyId!==verification.keyId||record.algorithm!==verification.algorithm||record.trustedComment!==verification.trustedComment||!verification.trustedComment.includes(`version:${expectedVersion}`)) throw new Error('Windows signature identity/version differs from candidate evidence.');
  if(typeof notes!=='string'||notes.length>4000||typeof createdUtc!=='string'||new Date(createdUtc).toISOString()!==createdUtc) throw new Error('Invalid release notes or publication timestamp.');
  const encoded=encodeURIComponent(file);
  const updatePath=`/client-updates/windows/releases/${expectedVersion}/${encoded}`;
  const downloadPath=`/downloads/${expectedVersion}/${encoded}`;
  const update={version:expectedVersion,minimumSupportedVersion:'0.0.0',notes,pub_date:createdUtc,platforms:{'windows-x86_64':{signature:signature.trim(),url:releaseOrigin+updatePath}}};
  const downloads={schemaVersion:1,windows:{version:expectedVersion,url:downloadPath,sha256:record.sha256,bytes:record.bytes},android:null};
  const plan={schemaVersion:1,status:'PREPARED_NOT_PUBLISHED',version:expectedVersion,createdUtc,origin:releaseOrigin,
    windows:{file,bytes:record.bytes,sha256:record.sha256,keyId:verification.keyId,signatureVerified:true,installationAcceptanceConfirmed:true,minimumSupportedVersion:'0.0.0',authenticode:'Not provided; updater signature is not publisher certification'},
    backup:{...backupProof,oldCloudPairCleanupConfirmed:true,cleanupEvidence:'Maintainer reported; cloud account was not accessed'},
    androidPublished:false,publicRegistrationEnabled:false,publicReleaseApproved:false,
    remaining:['Maintainer approval for public Windows upload and activation','HTTPS file and cache checks','Formal Windows update-check acceptance'],
    legacyTestIdentity:'Old test-key 0.1.3 requires manual migration; this is not an automatic signing-key rotation'};
  return {file,update,downloads,plan};
}
