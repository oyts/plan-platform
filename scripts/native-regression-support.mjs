import net from 'node:net';
import { createHash } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';

export const PORTS = Object.freeze({ api: 43134, desktopCdp: 43135, androidCdp: 43136, windowsProxy: 43137, androidProxy: 43138 });
export const ANDROID_PACKAGE = 'cn.zijun701.plan.mobile.regression';
export const DESKTOP_IDENTIFIER = 'cn.zijun701.plan.desktop.regression';

export function selectDevice(listing, serial) {
  const devices = listing.split(/\r?\n/).filter(line => /^\S+\s+device(?:\s|$)/.test(line)).map(line => line.split(/\s+/)[0]);
  if (serial && devices.includes(serial)) return serial;
  if (!serial && devices.length === 1) return devices[0];
  throw new Error('Connect and authorize exactly one phone, or supply --serial. No device was changed.');
}
export function validateManifest(manifest) {
  if (manifest.schema !== 1 || manifest.androidPackage !== ANDROID_PACKAGE || manifest.desktopIdentifier !== DESKTOP_IDENTIFIER ||
      !/^PlanCabin-\d+\.\d+\.\d+-debug\.apk$/.test(manifest.apkName) ||
      !/^[a-f0-9]{64}$/i.test(manifest.sourceSha256) || !/^[a-f0-9]{64}$/i.test(manifest.apiSha256) ||
      !/^[a-f0-9]{64}$/i.test(manifest.apkSha256) || !/^[a-f0-9]{64}$/i.test(manifest.desktopSha256)) {
    throw new Error('Invalid regression build manifest; rebuild isolated clients.');
  }
  return manifest;
}
export async function sourceFingerprint(root) {
  const files = ['web/package.json', 'web/package-lock.json', 'web/src-tauri/Cargo.toml', 'web/src-tauri/Cargo.lock', 'web/src-tauri/build.rs', 'web/src-tauri/tauri.conf.json', 'web/src-tauri/tauri.regression.conf.json', 'web/src-tauri/tauri.stable-trial.conf.json', 'web/android/app/build.gradle', 'web/android/app/src/main/AndroidManifest.xml'];
  const visit = async relative => {
    for (const entry of await readdir(join(root, relative), { withFileTypes: true })) {
      const path = `${relative}/${entry.name}`;
      if (entry.isDirectory()) await visit(path);
      else if (entry.isFile()) files.push(path);
    }
  };
  for (const directory of ['web/src', 'web/src-tauri/src', 'web/android/app/src/main/java']) await visit(directory);
  const hash = createHash('sha256');
  for (const path of files.sort()) { hash.update(path + '\0'); hash.update(await readFile(join(root, path))); hash.update('\0'); }
  return hash.digest('hex');
}
export async function verifyHash(path, expected) {
  const actual = createHash('sha256').update(await readFile(path)).digest('hex');
  if (actual.toLowerCase() !== expected.toLowerCase()) throw new Error('Regression artifact checksum mismatch; rebuild before running.');
}
export async function assertPortsFree(ports) {
  const held = [];
  try {
    for (const port of ports) {
      const server = net.createServer();
      await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
      held.push(server);
    }
  } finally { await Promise.all(held.map(server => new Promise(resolve => server.close(resolve)))); }
}
// A real TCP outage affects native Rust/Java requests too. Browser setOffline/route
// interception would not exercise those bridges and is deliberately not used.
export async function createFaultProxy(port, targetPort) {
  let online = true;
  const sockets = new Set();
  const server = net.createServer(client => {
    if (!online) { client.destroy(); return; }
    const upstream = net.connect(targetPort, '127.0.0.1');
    for (const socket of [client, upstream]) { sockets.add(socket); socket.on('close', () => sockets.delete(socket)); socket.on('error', () => { client.destroy(); upstream.destroy(); }); }
    client.pipe(upstream); upstream.pipe(client);
    client.on('close', () => upstream.destroy()); upstream.on('close', () => client.destroy());
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
  return {
    port: server.address().port,
    setOnline(value) { online = value; if (!online) for (const socket of sockets) socket.destroy(); },
    async close() { for (const socket of sockets) socket.destroy(); await new Promise(resolve => server.close(resolve)); },
  };
}
export function xmlEscape(value) { return String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' }[char])); }
export function reportHtml(report) {
  const title = report.title ?? 'PC + Android 原生回归';
  const scope = report.scope ?? '独立包、随机测试账号、内存数据库；不修改正式客户端。CDP 驱动真实 WebView，业务请求未模拟。系统安装、分享选择器、硬件权限与跨版本升级仍须专项验收。';
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>${xmlEscape(title)}</title><style>body{font:16px system-ui;margin:32px;max-width:1000px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:12px;text-align:left}.PASS{color:green}.FAIL{color:#b42318}pre{white-space:pre-wrap}</style><h1>${xmlEscape(title)}</h1><p>${xmlEscape(report.startedAt)} · ${xmlEscape(report.platform)} · ${xmlEscape(report.status)}</p><p>${xmlEscape(scope)}</p><table><tr><th>场景</th><th>结果</th><th>耗时</th><th>截图</th></tr>${report.cases.map(row => `<tr><td>${xmlEscape(row.name)}</td><td class="${row.status}">${row.status}${row.error ? `<pre>${xmlEscape(row.error)}</pre>` : ''}</td><td>${row.durationMs} ms</td><td>${(row.screenshots ?? []).map(file => `<a href="${xmlEscape(file)}">${xmlEscape(file)}</a>`).join('<br>')}</td></tr>`).join('')}</table>${report.error ? `<h2>执行失败</h2><pre>${xmlEscape(report.error)}</pre>` : ''}<h2>环境</h2><pre>${xmlEscape(JSON.stringify(report.environment, null, 2))}</pre></html>`;
}
export function junitXml(report) {
  const caseFailures = report.cases.filter(row => row.status === 'FAIL').length;
  const outsideFailure = report.cleanupError || (!caseFailures && report.error);
  const extra = outsideFailure ? 1 : 0;
  return `<?xml version="1.0" encoding="UTF-8"?><testsuite name="native-clients" tests="${report.cases.length + extra}" failures="${caseFailures + extra}">${report.cases.map(row => `<testcase name="${xmlEscape(row.name)}" time="${row.durationMs / 1000}">${row.status === 'FAIL' ? `<failure message="${xmlEscape(row.error)}"/>` : ''}</testcase>`).join('')}${outsideFailure ? `<testcase name="preflight-or-cleanup"><failure message="${xmlEscape(outsideFailure)}"/></testcase>` : ''}</testsuite>`;
}
