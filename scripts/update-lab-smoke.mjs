import { request } from 'node:https';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import { projectRoot, origin, setScenario } from './update-lab.mjs';

const ca = readFileSync(resolve(projectRoot, '.desktop-tools/update-lab/tls/ca.pem'));
function get(path, options = {}) {
  return new Promise((accept, reject) => {
    const req = request(origin + path, { ca, timeout: 5000, ...options }, res => {
      const chunks = []; let total = 0;
      res.on('data', chunk => { total += chunk.length; if (total > 10 * 1024 * 1024) { res.destroy(new Error('Response exceeds lab smoke-test limit.')); return; } chunks.push(chunk); });
      res.on('aborted', () => reject(new Error('Response interrupted.')));
      res.on('error', reject); res.on('end', () => accept({ status: res.statusCode, body: Buffer.concat(chunks) }));
    });
    req.on('error', reject); req.on('timeout', () => req.destroy(new Error('Timeout.'))); req.end();
  });
}
try {
  assert.equal((await get('/health')).status, 200);
  await assert.rejects(get('/health', { ca: undefined }));
  await assert.rejects(get('/health', { servername: 'localhost' }));
  console.log('PASS: embedded CA works; untrusted CA and wrong hostname are rejected.');
  for (const path of ['/tls/password.txt', '/tls/server.p12', '/../.desktop-tools/update-lab/tls/password.txt', '/%2e%2e/tls/password.txt']) assert.equal((await get(path)).status, 404);
  console.log('PASS: private files and traversal paths are not served.');
  setScenario('idle'); assert.equal((await get('/windows/latest.json')).status, 204); assert.equal((await get('/android/latest.json')).status, 204);
  setScenario('optional');
  const desktop = JSON.parse((await get('/windows/latest.json')).body), android = JSON.parse((await get('/android/latest.json')).body);
  assert.equal(desktop.minimumSupportedVersion, '0.0.0'); assert.equal(android.minimumSupportedVersionCode, 1);
  for (const [platform, url] of [['windows', desktop.platforms['windows-x86_64'].url], ['android', android.url]]) {
    const response = await get(new URL(url).pathname); assert.equal(response.status, 200);
    if (platform === 'android') { assert.equal(response.body.length, android.size); assert.equal(createHash('sha256').update(response.body).digest('hex'), android.sha256); }
  }
  setScenario('required');
  assert.equal(JSON.parse((await get('/windows/latest.json')).body).minimumSupportedVersion, '0.1.2');
  assert.equal(JSON.parse((await get('/android/latest.json')).body).minimumSupportedVersionCode, 6);
  setScenario('bad-hash'); assert.equal(JSON.parse((await get('/android/latest.json')).body).sha256, '0'.repeat(64));
  setScenario('bad-signature'); assert.notEqual(JSON.parse((await get('/windows/latest.json')).body).platforms['windows-x86_64'].signature, desktop.platforms['windows-x86_64'].signature);
  setScenario('interrupted'); await assert.rejects(get(new URL(android.url).pathname));
  console.log('PASS: idle, optional, required, bad hash/signature and interrupted transfer scenarios.');
} finally { setScenario('idle'); }
