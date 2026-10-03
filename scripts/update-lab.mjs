import { createServer, request } from 'node:https';
import { createSecureContext } from 'node:tls';
import { createHash, randomBytes, X509Certificate } from 'node:crypto';
import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync, statSync, createReadStream, copyFileSync, mkdtempSync, unlinkSync, rmdirSync } from 'node:fs';
import { resolve, basename } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';

export const projectRoot = fileURLToPath(new URL('../', import.meta.url));
const privateRoot = resolve(projectRoot, '.desktop-tools/update-lab');
const outputRoot = resolve(projectRoot, 'client-update-artifacts/local');
const tlsRoot = resolve(privateRoot, 'tls');
const statePath = resolve(privateRoot, 'scenario.json');
const catalogPath = resolve(privateRoot, 'catalog.json');
export const origin = 'https://127.0.0.1:43133';
export const scenarios = ['idle', 'optional', 'required', 'bad-hash', 'bad-signature', 'invalid-artifact', 'interrupted'];
export function buildPlan(stage = 'basic') {
  if (stage === 'basic') return { bootstrap: { directory: 'bootstrap', version: '0.1.1', versionCode: 5 }, target: { directory: 'target', version: '0.1.2', versionCode: 6 } };
  if (stage === 'safety') return { bootstrap: { directory: 'target', version: '0.1.2', versionCode: 6 }, target: { directory: 'safety-target', version: '0.1.3', versionCode: 7 } };
  throw new Error('Unknown lab stage.');
}
const readJson = path => JSON.parse(readFileSync(path, 'utf8'));
const saveJson = (path, value) => { const temp = path + '.tmp'; writeFileSync(temp, JSON.stringify(value, null, 2) + '\n'); renameSync(temp, path); };
const hash = path => createHash('sha256').update(readFileSync(path)).digest('hex');
const javaHome = process.env.JAVA_HOME || 'E:/biancheng/JDK21';
const keytool = resolve(javaHome, 'bin/keytool.exe');
function key(args) { execFileSync(keytool, args, { stdio: ['ignore', 'pipe', 'pipe'] }); }
export function setScenario(scenario) {
  if (!scenarios.includes(scenario)) throw new Error('Unknown lab scenario.');
  saveJson(statePath, { scenario });
}

export function prepareTls() {
  mkdirSync(tlsRoot, { recursive: true });
  const cert = resolve(tlsRoot, 'ca.pem');
  if (existsSync(cert)) {
    const existing = new X509Certificate(readFileSync(cert));
    if (Date.parse(existing.validTo) < Date.now() + 86400000) throw new Error('Lab certificate expired or expiring. Prepare a new lab identity and rebuild both test clients; do not bypass TLS.');
    // An interrupted keytool run must not be mistaken for a complete TLS identity.
    const leaf = new X509Certificate(readFileSync(resolve(tlsRoot, 'server.pem')));
    if (!leaf.verify(existing.publicKey) || leaf.checkIP('127.0.0.1') !== '127.0.0.1') throw new Error('Incomplete or invalid lab certificate chain.');
    createSecureContext({ pfx: readFileSync(resolve(tlsRoot, 'server.p12')), passphrase: readFileSync(resolve(tlsRoot, 'password.txt'), 'utf8') });
    return cert;
  }
  // Files are outside the served directory and Git ignored. No certificate-store import.
  const passwordFile = resolve(tlsRoot, 'password.txt');
  writeFileSync(passwordFile, randomBytes(32).toString('hex'), { mode: 0o600, flag: 'wx' });
  const rootKey = resolve(tlsRoot, 'ca.p12'), serverKey = resolve(tlsRoot, 'server.p12');
  const rootArgs = ['-keystore', rootKey, '-storepass:file', passwordFile];
  const serverArgs = ['-keystore', serverKey, '-storepass:file', passwordFile];
  key(['-genkeypair', ...rootArgs, '-storetype', 'PKCS12', '-alias', 'ca', '-keyalg', 'RSA', '-keysize', '3072', '-dname', 'CN=Plan Cabin Local Update Lab CA', '-validity', '30', '-ext', 'BC=ca:true', '-ext', 'KU=keyCertSign,cRLSign']);
  key(['-exportcert', ...rootArgs, '-alias', 'ca', '-rfc', '-file', cert]);
  key(['-genkeypair', ...serverArgs, '-storetype', 'PKCS12', '-alias', 'server', '-keyalg', 'RSA', '-keysize', '2048', '-dname', 'CN=127.0.0.1', '-validity', '30']);
  const csr = resolve(tlsRoot, 'server.csr'), leaf = resolve(tlsRoot, 'server.pem');
  key(['-certreq', ...serverArgs, '-alias', 'server', '-file', csr]);
  key(['-gencert', ...rootArgs, '-alias', 'ca', '-infile', csr, '-outfile', leaf, '-rfc', '-validity', '30', '-ext', 'SAN=ip:127.0.0.1', '-ext', 'BC=ca:false', '-ext', 'KU=digitalSignature,keyEncipherment', '-ext', 'EKU=serverAuth']);
  key(['-importcert', ...serverArgs, '-alias', 'ca', '-file', cert, '-noprompt']);
  key(['-importcert', ...serverArgs, '-alias', 'server', '-file', leaf, '-noprompt']);
  return cert;
}

function inspectApk(apk) {
  const tools = resolve(process.env.ANDROID_HOME || resolve(projectRoot, '.desktop-tools/android-sdk'), 'build-tools/36.0.0');
  const temp = mkdtempSync(resolve(tmpdir(), 'plan-lab-apk-')), copy = resolve(temp, 'client.apk');
  let badging;
  try { copyFileSync(apk, copy); badging = execFileSync(resolve(tools, 'aapt.exe'), ['dump', 'badging', copy], { encoding: 'utf8' }); }
  finally { if (existsSync(copy)) unlinkSync(copy); rmdirSync(temp); }
  const match = badging.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m);
  if (!match || match[1] !== 'cn.zijun701.plan.mobile') throw new Error('Wrong APK identity.');
  const signed = execFileSync(resolve(javaHome, 'bin/java.exe'), ['-jar', resolve(tools, 'lib/apksigner.jar'), 'verify', '--print-certs', apk], { encoding: 'utf8' });
  const signer = signed.match(/Signer #1 certificate SHA-256 digest: ([a-f0-9]+)/i)?.[1];
  if (!signer) throw new Error('Cannot verify APK signer.');
  return { version: match[3], versionCode: Number(match[2]), signer };
}

export function registerArtifacts(stage = 'basic') {
  const entries = {};
  for (const [role, { directory, version, versionCode }] of Object.entries(buildPlan(stage))) {
    const dir = resolve(outputRoot, directory);
    const installer = resolve(dir, `Plan Cabin_${version}_x64-setup.exe`), apk = resolve(dir, `PlanCabin-${version}-debug.apk`);
    const signature = readFileSync(installer + '.sig', 'utf8').trim();
    if (!/^[A-Za-z0-9+/=]+$/.test(signature)) throw new Error('Invalid Windows update signature.');
    const metadata = inspectApk(apk);
    if (metadata.version !== version || metadata.versionCode !== versionCode) throw new Error('Actual APK version differs from lab role.');
    entries[role] = { version, versionCode, signer: metadata.signer, windows: { file: installer, signature, sha256: hash(installer), size: statSync(installer).size }, android: { file: apk, sha256: hash(apk), size: statSync(apk).size } };
  }
  if (entries.bootstrap.signer !== entries.target.signer) throw new Error('Bootstrap and target APK signers differ. Do not uninstall to bypass.');
  saveJson(catalogPath, entries);
  if (!existsSync(statePath)) saveJson(statePath, { scenario: 'idle' });
  return entries;
}

export function tamperWindowsSignature(encoded) {
  const lines = Buffer.from(encoded, 'base64').toString('utf8').split(/\r?\n/);
  const packet = Buffer.from(lines[1] || '', 'base64');
  const globalSignature = Buffer.from(lines[3] || '', 'base64');
  if (packet.length !== 74 || !['ED', 'Ed'].includes(packet.subarray(0, 2).toString('ascii')) ||
      !lines[2]?.startsWith('trusted comment: ') || globalSignature.length !== 64) {
    throw new Error('Cannot create a structurally valid tampered Minisign signature.');
  }
  // Keep the algorithm, key ID, comments and encoded lengths intact. Corrupt one
  // signature byte so this exercises verification, not just format decoding.
  packet[10] ^= 1;
  lines[1] = packet.toString('base64');
  return Buffer.from(lines.join('\n')).toString('base64');
}

export function manifestFor(platform, entry, scenario, serverOrigin = origin) {
  if (!scenarios.includes(scenario)) throw new Error('Unknown lab scenario.');
  const artifact = entry[platform];
  const url = `${serverOrigin}/artifacts/${platform}/${encodeURIComponent(basename(artifact.file))}`;
  const notes = '本地更新链路验收包：账号、计划与离线队列应保留。非正式发布。';
  if (platform === 'windows') return { version: entry.version, minimumSupportedVersion: scenario === 'required' ? entry.version : '0.0.0', notes, pub_date: '2026-10-03T00:00:00Z', platforms: { 'windows-x86_64': { url, signature: ['bad-signature', 'invalid-artifact'].includes(scenario) ? tamperWindowsSignature(artifact.signature) : artifact.signature } } };
  return { version: entry.version, versionCode: entry.versionCode, minimumSupportedVersionCode: scenario === 'required' ? entry.versionCode : 1, notes, url, size: artifact.size, sha256: ['bad-hash', 'invalid-artifact'].includes(scenario) ? '0'.repeat(64) : artifact.sha256 };
}

export function startServer({ port = 43133, catalog = readJson(catalogPath), scenarioProvider = () => readJson(statePath).scenario } = {}) {
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('Invalid local lab port.');
  const server = createServer({ pfx: readFileSync(resolve(tlsRoot, 'server.p12')), passphrase: readFileSync(resolve(tlsRoot, 'password.txt'), 'utf8'), minVersion: 'TLSv1.2' }, (req, res) => {
    res.setHeader('Cache-Control', 'no-store'); res.setHeader('X-Content-Type-Options', 'nosniff');
    if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405); res.end(); return; }
    // Exact allowlist, never resolve untrusted URL paths into filesystem paths.
    const path = req.url?.split('?')[0];
    let scenario;
    try { scenario = scenarioProvider(); if (!scenarios.includes(scenario)) throw new Error('Invalid scenario.'); }
    catch { res.writeHead(503); res.end(); return; }
    if (path === '/health') { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ service: 'plan-client-update-lab', scenario, baselineVersion: catalog.bootstrap.version, targetVersion: catalog.target.version })); return; }
    for (const platform of ['windows', 'android']) {
      if (path === `/${platform}/latest.json`) {
        if (scenario === 'idle') { res.writeHead(204); res.end(); return; }
        res.writeHead(200, { 'Content-Type': 'application/json' });
        const serverOrigin = `https://127.0.0.1:${server.address().port}`;
        res.end(req.method === 'HEAD' ? undefined : JSON.stringify(manifestFor(platform, catalog.target, scenario, serverOrigin))); return;
      }
      const artifact = catalog.target[platform];
      if (path === `/artifacts/${platform}/${encodeURIComponent(basename(artifact.file))}`) {
        res.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': artifact.size });
        if (req.method === 'HEAD') { res.end(); return; }
        if (scenario === 'interrupted') { const stream = createReadStream(artifact.file, { end: 1023 }); stream.pipe(res, { end: false }); stream.on('end', () => res.destroy()); }
        else createReadStream(artifact.file).pipe(res);
        return;
      }
    }
    res.writeHead(404); res.end();
  });
  server.headersTimeout = 10000; server.requestTimeout = 30000;
  server.listen(port, '127.0.0.1', () => console.log(`Local HTTPS update lab ready on https://127.0.0.1:${server.address().port}; no business API or cloud changes.`));
  return server;
}

async function probe() {
  const ca = readFileSync(resolve(tlsRoot, 'ca.pem'));
  await new Promise((resolvePromise, reject) => {
    const req = request(`${origin}/health`, { ca, timeout: 5000 }, res => {
      let body = ''; res.on('data', chunk => { body += chunk; }); res.on('end', () => {
        if (res.statusCode !== 200 || JSON.parse(body).service !== 'plan-client-update-lab') { reject(new Error('Unexpected lab service.')); return; }
        console.log(body); resolvePromise();
      });
    });
    req.on('error', reject); req.on('timeout', () => req.destroy(new Error('Lab probe timed out.'))); req.end();
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [action, scenario] = process.argv.slice(2);
  if (action === 'prepare') console.log(`Private TLS identity ready; public CA: ${prepareTls()}`);
  else if (action === 'plan') console.log(JSON.stringify(buildPlan(scenario)));
  else if (action === 'register') { registerArtifacts(scenario); console.log('Verified both APK identities and prepared local-only update catalog.'); }
  else if (action === 'scenario') { setScenario(scenario); console.log(`Local update scenario: ${scenario}`); }
  else if (action === 'serve') startServer();
  else if (action === 'probe') await probe();
  else throw new Error('Usage: update-lab.mjs prepare|plan <basic|safety>|register <basic|safety>|serve|probe|scenario <idle|optional|required|bad-hash|bad-signature|invalid-artifact|interrupted>');
}
