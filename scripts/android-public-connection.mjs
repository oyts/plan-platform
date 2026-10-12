// Read-only HTTPS probe through the real regression APK's Java bridge.
// Never install/clear the main app, log in, save a server or relax TLS checks.
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, writeFile, open, unlink } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium, expect } from '../web/node_modules/@playwright/test/index.mjs';
import { ANDROID_PACKAGE, PORTS, assertPortsFree, selectDevice } from './native-regression-support.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const exec = promisify(execFile);
const adbPath = join(root, '.desktop-tools/android-sdk/platform-tools/adb.exe');
const base = join(root, 'acceptance-results/android-public');
const out = join(base, new Date().toISOString().replace(/[:.]/g, '-'));
const lockPath = join(root, 'acceptance-results/native/run.lock');
const report = { status: 'FAIL', origin: 'https://plan.zijun701.cn', scope: 'Real Android Java HTTPS config probe only; no cloud login or business writes.' };
let serial, browser, forwarded = false, launched = false, lock;
async function adb(...args) {
  return (await exec(adbPath, serial ? ['-s', serial, ...args] : args, { windowsHide: true, timeout: 45000, maxBuffer: 2*1024*1024 })).stdout.trim();
}
const mainVersion = async () => (await adb('shell', 'dumpsys', 'package', 'cn.zijun701.plan.mobile')).match(/versionCode=\d+[^\r\n]*|versionName=[^\r\n]+/g)?.join('\n') || 'not installed';
await mkdir(out, { recursive: true });
try {
  lock = await open(lockPath, 'wx'); await lock.writeFile(String(process.pid));
  serial = selectDevice(await adb('devices'), '');
  await assertPortsFree([PORTS.androidCdp]);
  if ((await adb('forward', '--list')).split(/\r?\n/).some(line => line.split(/\s+/)[1] === `tcp:${PORTS.androidCdp}`)) throw new Error('Existing ADB forward must not be replaced.');
  let running = '';
  try { running = await adb('shell', 'pidof', ANDROID_PACKAGE); } catch (error) { if (error.code !== 1) throw error; }
  if (running) throw new Error('Close the regression app before this isolated probe; existing app was not stopped.');
  report.mainVersionBefore = await mainVersion();
  report.usbReverseBefore = await adb('reverse', '--list');
  await adb('shell', 'am', 'start', '-W', '-n', `${ANDROID_PACKAGE}/cn.zijun701.plan.mobile.MainActivity`); launched = true;
  let socket;
  for (let i=0;i<30;i++) {
    const pid = await adb('shell', 'pidof', ANDROID_PACKAGE);
    if (!/^\d+$/.test(pid)) throw new Error('Regression process is not unique.');
    socket = (await adb('shell', 'cat', '/proc/net/unix')).split(/\r?\n/).map(line => line.split(/\s+/).at(-1)).find(name => name === `@webview_devtools_remote_${pid}`);
    if (socket) break;
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  if (!socket) throw new Error('Regression WebView debugger is unavailable.');
  await adb('forward', `tcp:${PORTS.androidCdp}`, `localabstract:${socket.slice(1)}`); forwarded = true;
  browser = await chromium.connectOverCDP(`http://127.0.0.1:${PORTS.androidCdp}`, { noDefaults: true });
  const pages = browser.contexts().flatMap(context => context.pages()).filter(page => /^https?:\/\/localhost\//.test(page.url()));
  if (pages.length !== 1) throw new Error('Expected one bundled regression page.');
  const page = pages[0]; page.setDefaultTimeout(45000);
  await expect(page.locator('html')).toHaveClass(/android-client/);
  if (!await page.evaluate(() => window.Capacitor?.isNativePlatform())) throw new Error('Native bridge missing.');
  const nativeInfo = () => page.evaluate(async () => (await window.Capacitor.Plugins.PlanNative.execute({ command: 'desktop_info', args: {} })).value);
  const before = await nativeInfo();
  if (before.version !== '0.1.4' || before.versionCode !== 8 || !before.debug) throw new Error('Expected the verified 0.1.4 build 8 regression APK.');
  await page.evaluate(() => { location.hash = '/desktop'; });
  await expect(page.getByRole('heading', { name: '手机客户端', exact: true })).toBeVisible();
  const input = page.getByLabel('服务器根地址');
  await expect(input).toHaveValue(before.serverUrl);
  await input.fill(report.origin);
  await page.getByRole('button', { name: '测试连接', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: /^连接成功，/ })).toBeVisible();
  const after = await nativeInfo();
  if (after.serverUrl !== before.serverUrl) throw new Error('Read-only probe unexpectedly changed saved configuration.');
  report.version = after.version; report.versionCode = after.versionCode;
  report.savedServerUnchanged = true;
  await page.screenshot({ path: join(out, 'https-connected.png') });
  report.status = 'PASS';
} catch (error) { report.error = error.message; process.exitCode = 1; }
finally {
  const cleanupErrors = [];
  const attempt = async action => { try { await action(); } catch (error) { cleanupErrors.push(error.message); } };
  // Attempt every owned-resource cleanup even if an earlier CDP/ADB operation
  // fails. No resource belonging to another run or the main app is removed.
  if (browser) await attempt(() => browser.close());
  if (forwarded) await attempt(() => adb('forward', '--remove', `tcp:${PORTS.androidCdp}`));
  if (launched) await attempt(() => adb('shell', 'am', 'force-stop', ANDROID_PACKAGE));
  if (report.mainVersionBefore !== undefined) {
    await attempt(async () => {
      report.mainVersionAfter = await mainVersion();
      if (report.mainVersionAfter !== report.mainVersionBefore) throw new Error('Main package version changed.');
      if (await adb('reverse', '--list') !== report.usbReverseBefore) throw new Error('USB reverse mappings changed.');
    });
  }
  if (cleanupErrors.length) { report.status = 'FAIL'; report.cleanupError = cleanupErrors.join('; '); process.exitCode = 1; }
  if (lock) { await lock.close(); await unlink(lockPath); }
  await writeFile(join(out, 'report.json'), JSON.stringify(report, null, 2));
  console.log(`[${report.status}] Android native HTTPS connection probe`);
  if (report.error || report.cleanupError) console.log(report.error || report.cleanupError);
  console.log(`Report: ${join(out, 'report.json')}`);
}
