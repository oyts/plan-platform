import { createHash, createPublicKey, verify, X509Certificate } from 'node:crypto';
export const PUBLIC_ANDROID_PACKAGE = 'cn.zijun701.plan.app';
export const LEGACY_ANDROID_PACKAGE = 'cn.zijun701.plan.mobile';
const canonicalBase64 = value => {
  if (typeof value !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(value) || value.length>8192) throw new Error('Invalid signing packet encoding.');
  const bytes=Buffer.from(value,'base64'); if(bytes.toString('base64')!==value) throw new Error('Noncanonical signing packet encoding.'); return bytes;
};
export function verifyUpdaterSignature(artifact, publicText, signatureText) {
  const pubLines=canonicalBase64(publicText.trim()).toString('utf8').trimEnd().split(/\r?\n/);
  const sigLines=canonicalBase64(signatureText.trim()).toString('utf8').trimEnd().split(/\r?\n/);
  if(pubLines.length!==2||sigLines.length!==4||!pubLines[0].startsWith('untrusted comment: ')||!sigLines[0].startsWith('untrusted comment: ')||!sigLines[2].startsWith('trusted comment: ')) throw new Error('Malformed Minisign packet.');
  const pub=canonicalBase64(pubLines[1]), sig=canonicalBase64(sigLines[1]), global=canonicalBase64(sigLines[3]);
  if(pub.length!==42||sig.length!==74||global.length!==64||pub.subarray(0,2).toString()!=='Ed'||!sig.subarray(2,10).equals(pub.subarray(2,10))) throw new Error('Signature key identity mismatch.');
  const algorithm=sig.subarray(0,2).toString();
  if(!['Ed','ED'].includes(algorithm)) throw new Error('Unsupported signature algorithm.');
  const key=createPublicKey({key:Buffer.concat([Buffer.from('302a300506032b6570032100','hex'),pub.subarray(10)]),type:'spki',format:'der'});
  const message=algorithm==='ED'?createHash('blake2b512').update(artifact).digest():artifact;
  const trustedComment=sigLines[2].slice('trusted comment: '.length);
  if(!verify(null,message,key,sig.subarray(10))||!verify(null,Buffer.concat([sig.subarray(10),Buffer.from(trustedComment)]),key,global)) throw new Error('Updater signature cryptographic verification failed.');
  return {keyId:pub.subarray(2,10).toString('hex'),algorithm,trustedComment};
}
export function certificateDetails(der) {
  const certificate=new X509Certificate(der);
  if(/Android Debug/i.test(certificate.subject)) throw new Error('Debug certificate is not a public identity.');
  if(new Date(certificate.validTo)<=new Date()) throw new Error('Expired release certificate.');
  const jwk=certificate.publicKey.export({format:'jwk'});
  if(jwk.kty!=='RSA'||Buffer.from(jwk.n,'base64url').length<384) throw new Error('Expected a long-term RSA key of at least 3072 bits.');
  return {subject:certificate.subject,validFrom:certificate.validFrom,validTo:certificate.validTo,certificateMD5:createHash('md5').update(certificate.raw).digest('hex').toUpperCase(),certificateSHA256:createHash('sha256').update(certificate.raw).digest('hex'),publicKeyModulusHex:Buffer.from(jwk.n,'base64url').toString('hex').toUpperCase(),publicKeyPem:certificate.publicKey.export({type:'spki',format:'pem'})};
}
export function validateApkEvidence(badging, signatureOutput, expectedVersion, expectedCode, expectedCertificateSha256) {
  const meta=badging.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m);
  if(!meta||meta[1]!==PUBLIC_ANDROID_PACKAGE||Number(meta[2])!==expectedCode||meta[3]!==expectedVersion) throw new Error('Formal APK package/version mismatch.');
  if(/^application-debuggable/m.test(badging)||/certificate DN:.*Android Debug/i.test(signatureOutput)) throw new Error('Debug APK/certificate cannot be a formal release.');
  const digests=[...signatureOutput.matchAll(/Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})/g)].map(match=>match[1].toLowerCase());
  if(digests.length!==1||digests[0]!==expectedCertificateSha256.toLowerCase()) throw new Error('APK signer differs from the preserved long-term certificate.');
  return {packageName:meta[1],version:meta[3],versionCode:Number(meta[2]),certificateSHA256:digests[0]};
}
