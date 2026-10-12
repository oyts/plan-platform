import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, statSync, mkdtempSync, copyFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import {certificateDetails,validateApkEvidence,LEGACY_ANDROID_PACKAGE} from './formal-release-support.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const [apkArg, downloadUrl, ...notes] = process.argv.slice(2);
if (!apkArg || !downloadUrl) throw new Error('Usage: node scripts/android-release.mjs <verified-signed.apk> <https-download-url> [notes]');
const url = new URL(downloadUrl);
if (url.protocol !== 'https:' || url.username || url.password || url.hash) throw new Error('APK URL must be HTTPS without credentials.');
const apk = resolve(apkArg);
if (!apk.endsWith('.apk')) throw new Error('Expected a signed APK. Verify with apksigner before generating a manifest.');
const gradle = readFileSync(resolve(root,'web/android/app/build.gradle'),'utf8');
const version = gradle.match(/versionName[^\r\n]*"([0-9.]+)"/)?.[1], versionCode = Number(gradle.match(/versionCode[^\r\n]*'([0-9]+)'/)?.[1]);
const sdk = process.env.ANDROID_HOME || resolve(root,'.desktop-tools/android-sdk');
const tools = resolve(sdk, 'build-tools/36.0.0');
// aapt on Windows cannot read a Unicode APK path. Inspect a byte-identical private temporary copy.
const inspectionDir = mkdtempSync(resolve(tmpdir(), 'plan-apk-inspection-'));
const inspectionApk = resolve(inspectionDir, 'client.apk');
let badging,signatureOutput;
try {
  copyFileSync(apk, inspectionApk);
  badging = execFileSync(resolve(tools,'aapt.exe'), ['dump','badging',inspectionApk], { encoding:'utf8' });
  const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME,'bin/java.exe') : 'java';
  signatureOutput = execFileSync(java, ['-jar',resolve(tools,'lib/apksigner.jar'),'verify','--verbose','--print-certs',inspectionApk], { encoding:'utf8' });
} finally {
  if (statSync(inspectionDir).isDirectory()) { try { unlinkSync(inspectionApk); } catch (error) { if (error.code !== 'ENOENT') throw error; } rmdirSync(inspectionDir); }
}
const metadata = badging.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m);
if (process.env.PLAN_RELEASE_FOR_TESTING === '1') {
  if (!metadata || metadata[1] !== LEGACY_ANDROID_PACKAGE || Number(metadata[2]) !== versionCode || metadata[3] !== version) throw new Error('Test APK package/version differs from current source.');
} else {
  const certificate=certificateDetails(readFileSync(resolve(root,'.release-signing/android-release.der')));
  validateApkEvidence(badging,signatureOutput,version,versionCode,certificate.certificateSHA256);
}
const minimum = Number(process.env.PLAN_ANDROID_MINIMUM_VERSION_CODE || '1');
if (!version || !Number.isSafeInteger(versionCode) || versionCode < 1 || !Number.isSafeInteger(minimum) || minimum < 1 || minimum > versionCode) throw new Error('Invalid Android version/minimum policy.');
const size = statSync(apk).size;
if (size < 1 || size > 256 * 1024 * 1024) throw new Error('APK exceeds updater size limit.');
const manifest = { version, versionCode, minimumSupportedVersionCode: minimum, notes: notes.join(' ') || '计划舱手机客户端更新', url: url.href, size, sha256: createHash('sha256').update(readFileSync(apk)).digest('hex') };
const destination = resolve(root,'android-artifacts/latest.json');
writeFileSync(destination, JSON.stringify(manifest,null,2)+'\n');
console.log(`Android manifest ready: ${destination}`);
