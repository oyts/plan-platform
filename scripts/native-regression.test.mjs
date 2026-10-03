import { test } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { PORTS, selectDevice, validateManifest, createFaultProxy, assertPortsFree, reportHtml, junitXml } from './native-regression-support.mjs';

test('only exactly one authorized phone or an explicit authorized serial is selected', () => {
  assert.equal(selectDevice('List of devices attached\nabc\tdevice\n'), 'abc');
  assert.throws(() => selectDevice('abc\tunauthorized\n'));
  assert.throws(() => selectDevice('a\tdevice\nb\tdevice\n'));
  assert.equal(selectDevice('a\tdevice\nb\tdevice\n', 'b'), 'b');
  assert.throws(() => selectDevice('a\tdevice\n', 'c'));
});
test('manifest refuses main package, traversal and malformed checksums', () => {
  const manifest = { schema: 1, sourceSha256: 'c'.repeat(64), apiSha256: 'd'.repeat(64), androidPackage: 'cn.zijun701.plan.mobile.regression', desktopIdentifier: 'cn.zijun701.plan.desktop.regression', apkName: 'PlanCabin-0.1.1-debug.apk', apkSha256: 'a'.repeat(64), desktopSha256: 'b'.repeat(64) };
  assert.equal(validateManifest(manifest), manifest);
  for (const change of [{ androidPackage: 'cn.zijun701.plan.mobile' }, { desktopIdentifier: 'cn.zijun701.plan.desktop' }, { apkName: '../app.apk' }, { apkSha256: 'bad' }, { sourceSha256: null }, { apiSha256: null }, { schema: 2 }]) assert.throws(() => validateManifest({ ...manifest, ...change }));
});
test('ports are unique and never reuse acceptance, business or update services', () => {
  assert.equal(new Set(Object.values(PORTS)).size, 5);
  for (const port of Object.values(PORTS)) assert.ok(![43128, 43129, 43130, 43131, 43132, 43133].includes(port));
});
test('port preflight refuses an occupied listener without stopping it', async () => {
  const server = net.createServer(); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await assert.rejects(assertPortsFree([server.address().port])); assert.ok(server.listening); }
  finally { await new Promise(resolve => server.close(resolve)); }
});
test('fault proxy forwards real TCP bytes, interrupts native requests and recovers', async () => {
  const upstream = net.createServer(socket => socket.on('data', data => socket.end(data)));
  await new Promise(resolve => upstream.listen(0, '127.0.0.1', resolve));
  const proxy = await createFaultProxy(0, upstream.address().port);
  // createFaultProxy exposes its allocated address only for unit-test port 0.
  const exchange = () => new Promise((resolve, reject) => {
    const socket = net.connect(proxy.port, '127.0.0.1'); let data = '';
    socket.on('connect', () => socket.write('bridge')); socket.on('data', chunk => { data += chunk; });
    socket.on('error', error => error.code === 'ECONNRESET' ? resolve(data) : reject(error)); socket.on('close', () => resolve(data));
  });
  try { assert.equal(await exchange(), 'bridge'); proxy.setOnline(false); assert.equal(await exchange(), ''); proxy.setOnline(true); assert.equal(await exchange(), 'bridge'); }
  finally { await proxy.close(); await new Promise(resolve => upstream.close(resolve)); }
});
test('HTML and JUnit escape error strings and count preflight as a failure', () => {
  const report = { startedAt: 'now', platform: 'both', status: 'FAIL', environment: {}, error: 'missing <phone>', cases: [{ name: '<script>', status: 'FAIL', durationMs: 10, error: 'bad & "x"', screenshots: [] }] };
  assert.ok(!reportHtml(report).includes('<script>')); assert.ok(reportHtml(report).includes('&lt;phone&gt;'));
  const xml = junitXml(report); assert.ok(xml.includes('tests="1" failures="1"')); assert.ok(xml.includes('bad &amp; &quot;x&quot;'));
  assert.ok(junitXml({ ...report, cases: [] }).includes('tests="1" failures="1"'));
  assert.ok(junitXml({ ...report, cleanupError: 'cleanup' }).includes('tests="2" failures="2"'));
});
