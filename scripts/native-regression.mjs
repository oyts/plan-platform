import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile, writeFile, mkdir, open, unlink, copyFile } from 'node:fs/promises';
import { createWriteStream, existsSync } from 'node:fs';
import { randomUUID, createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { join, resolve } from 'node:path';
import { chromium, expect as baseExpect } from '../web/node_modules/@playwright/test/index.mjs';
import { PORTS, ANDROID_PACKAGE, selectDevice, validateManifest, verifyHash, sourceFingerprint, assertPortsFree, createFaultProxy, reportHtml, junitXml } from './native-regression-support.mjs';
import { exerciseLearning } from './learning-acceptance.mjs';
import { exerciseAndroidRoadmap } from './roadmap-android-acceptance.mjs';
import { exerciseAndroidAiWorkbench } from './ai-workbench-android-acceptance.mjs';
import {exerciseStableAndroid} from './stable-android-acceptance.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const artifactDir = join(root, '.desktop-tools/native-regression');
const exe = join(artifactDir, 'plan-regression.exe');
const adbPath = join(process.env.ANDROID_HOME || join(root, '.desktop-tools/android-sdk'), 'platform-tools/adb.exe');
const java = join(process.env.JAVA_HOME || '', 'bin/java.exe');
const exec = promisify(execFile);
const options = { platform: 'both', check: false, serial: '', roadmap: false, roadmapSection: 'all', aiWorkbench:false, stableTrial:false,stableFiles:false };
for (let i = 2; i < process.argv.length; i++) {
  const arg = process.argv[i];
  if (arg === '--platform') options.platform = process.argv[++i];
  else if (arg === '--serial') options.serial = process.argv[++i];
  else if (arg === '--check') options.check = true;
  else if (arg === '--roadmap') options.roadmap = true;
  else if (arg === '--ai-workbench') options.aiWorkbench = true;
  else if (arg === '--stable-trial') options.stableTrial = true;
  else if (arg === '--stable-files') {options.stableTrial=true;options.stableFiles=true;}
  else if (arg === '--roadmap-section') options.roadmapSection = process.argv[++i];
  else throw new Error(`Unknown argument: ${arg}`);
}
if (!['both', 'windows', 'android'].includes(options.platform)) throw new Error('Invalid --platform.');
if (options.roadmap && options.platform !== 'android') throw new Error('--roadmap is Android-only; it does not authorize Windows UI automation.');
if(options.aiWorkbench && (options.platform!=='android'||options.roadmap))throw new Error('--ai-workbench requires only --platform android.');
if(options.stableTrial&&(options.platform!=='android'||options.roadmap||options.aiWorkbench))throw new Error('--stable-trial requires only --platform android.');
if (!['all','help'].includes(options.roadmapSection) || (!options.roadmap && options.roadmapSection !== 'all')) throw new Error('Invalid --roadmap-section.');
const runId = new Date().toISOString().replace(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8);
const resultsRoot = join(root, 'acceptance-results/native');
const runDir = join(resultsRoot, runId);
const report = { startedAt: new Date().toISOString(), platform: options.platform, status: 'FAIL', environment: {}, cases: [] };
const clients = [];
const expect = baseExpect.configure({ timeout: 20_000 });
const PASSWORD = 'NativeRegression123!'; // Synthetic local account only; never used on a public server.
const secrets = [PASSWORD];
const bootstrapToken = randomUUID() + randomUUID();
const bootstrapEmail = `native-admin-${randomUUID()}@example.test`;
secrets.push(bootstrapToken);
const redact = value => secrets.reduce((text, secret) => text.replaceAll(secret, '[redacted]'), String(value));
let apiProcess, lock, apiLog;
let reverseOwned = false, forwardOwned = false, serial;
let formalAndroidBefore;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function adb(...args) {
  const { stdout } = await exec(adbPath, ...(serial ? [['-s', serial, ...args]] : [args]), { windowsHide: true, timeout: args[0] === 'install' ? 180_000 : 45_000, maxBuffer: 2 * 1024 * 1024 });
  return stdout.trim();
}
async function waitFor(check, label, timeout = 45_000) {
  const start = Date.now();
  while (Date.now() - start < timeout) { if (await check()) return; await delay(300); }
  throw new Error(`Timed out: ${label}`);
}
async function stopChild(child) {
  if (!child || child.exitCode !== null) return;
  child.kill();
  await waitFor(() => child.exitCode !== null || child.signalCode !== null, 'owned child process exit', 15_000);
}
async function api(path, data, token, method = data === undefined ? 'GET' : 'POST', status = 200) {
  const response = await fetch(`http://127.0.0.1:${PORTS.api}/api${path}`, {
    method, headers: { ...(data === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: data === undefined ? undefined : JSON.stringify(data), signal: AbortSignal.timeout(20_000),
  });
  expect(response.status, `${method} ${path}`).toBe(status);
  return status === 204 ? null : response.json();
}
async function account(name, admin = false) {
  const email = admin ? bootstrapEmail : `native-${randomUUID()}@example.test`;
  const data = await api('/auth/register', { email, password: PASSWORD, displayName: name, ...(admin ? { invitationToken: bootstrapToken } : {}) }, undefined, 'POST', 201);
  secrets.push(data.accessToken);
  if (admin) expect(data.user.role).toBe('ADMIN');
  return { email, token: data.accessToken, name };
}
async function route(client, path) {
  await client.page.evaluate(path => { location.hash = path; }, path);
  const heading = path.startsWith('/schedule') ? '执行日程' : path === '/plans' ? '我的计划' : path.startsWith('/plans/') ? '原生回归计划' : path === '/sync' ? '同步中心' : null;
  if (heading) await expect(client.page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
}
async function refresh(client, path) {
  await client.page.reload();
  // Native session bootstrap can redirect a reloaded protected route to the dashboard.
  // Wait for identity restoration before navigating, just as a user would.
  await expect(client.page.locator('.site-header .brand')).toBeVisible();
  await route(client, path);
}
async function attach(client) {
  if (client.kind === 'android') {
    const pid = await adb('shell', 'pidof', ANDROID_PACKAGE);
    if (!/^\d+$/.test(pid)) throw new Error('Regression Android process was not uniquely identified.');
    const sockets = await adb('shell', 'cat', '/proc/net/unix');
    const socket = sockets.split(/\r?\n/).map(line => line.split(/\s+/).at(-1)).find(name => name === `@webview_devtools_remote_${pid}`);
    if (!socket) throw new Error('Debug WebView socket missing for the regression package. Unlock phone and retry.');
    await adb('forward', `tcp:${PORTS.androidCdp}`, `localabstract:${socket.slice(1)}`);
    forwardOwned = true;
  }
  await waitFor(async () => {
    if (client.process?.exitCode !== null && client.process?.exitCode !== undefined) throw new Error('Regression EXE exited before WebView attached.');
    try { const response = await fetch(`http://127.0.0.1:${client.cdp}/json/list`, { signal: AbortSignal.timeout(1000) }); return (await response.json()).some(page => page.type === 'page'); } catch { return false; }
  }, `${client.kind} WebView debugger`);
  client.browser = await chromium.connectOverCDP(`http://127.0.0.1:${client.cdp}`, { timeout: 20_000, noDefaults: true });
  let candidates = [];
  await waitFor(async () => {
    const pages = client.browser.contexts().flatMap(context => context.pages());
    candidates = pages.filter(page => client.kind === 'windows' ? /^https?:\/\/tauri\.localhost\//.test(page.url()) : /^https?:\/\/localhost\//.test(page.url()));
    if (candidates.length > 1) throw new Error(`Expected one bundled ${client.kind} WebView, found ${candidates.length}.`);
    return candidates.length === 1;
  }, `${client.kind} bundled page navigation`);
  client.page = candidates[0]; client.page.setDefaultTimeout(20_000);
  await expect(client.page.locator('html')).toHaveClass(new RegExp(client.kind === 'windows' ? 'desktop-client' : 'android-client'));
  // Bridge identity is checked, not replaced. No page.route() or mocked invoke().
  if (client.kind === 'windows') expect(await client.page.evaluate(() => Boolean(window.__TAURI_INTERNALS__))).toBe(true);
  else expect(await client.page.evaluate(() => window.Capacitor?.isNativePlatform())).toBe(true);
}
async function launch(client) {
  if (client.kind === 'windows') {
    // Process-scoped WebView flags only: never modify registry/system browser settings.
    const env = { ...process.env };
    delete env.WEBVIEW2_BROWSER_EXECUTABLE_FOLDER; delete env.WEBVIEW2_USER_DATA_FOLDER;
    delete env.WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS; delete env.WEBVIEW2_PIPE_FOR_SCRIPT_DEBUGGER;
    client.process = spawn(exe, [], { cwd: artifactDir, windowsHide: false, env, stdio: 'ignore' });
    client.process.on('error', error => { client.launchError = error.message; });
  } else await adb('shell', 'am', 'start', '-W', '-n', `${ANDROID_PACKAGE}/cn.zijun701.plan.mobile.MainActivity`);
  await delay(1200);
  if (client.launchError) throw new Error(client.launchError);
  await attach(client);
}
async function restart(client) {
  if (client.browser) { await client.browser.close(); client.browser = null; }
  if (client.kind === 'windows') await stopChild(client.process);
  else {
    await adb('shell', 'am', 'force-stop', ANDROID_PACKAGE);
    if (forwardOwned) { await adb('forward', '--remove', `tcp:${PORTS.androidCdp}`); forwardOwned = false; }
  }
  await launch(client);
}
async function configure(client, user) {
  await route(client, '/desktop');
  await client.page.getByLabel('服务器根地址').fill(`http://127.0.0.1:${client.proxyPort}`);
  await client.page.getByRole('button', { name: '测试连接', exact: true }).click();
  await expect(client.page.getByRole('status').filter({ hasText: /^连接成功，/ })).toBeVisible();
  await client.page.getByRole('button', { name: '保存并重新连接', exact: true }).click();
  await expect(client.page.getByRole('heading', { name: '登录计划平台' })).toBeVisible();
  await login(client, user);
}
async function login(client, user) {
  await client.page.getByLabel('邮箱', { exact: true }).fill(user.email);
  await client.page.getByLabel('密码', { exact: true }).fill(PASSWORD);
  await client.page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(client.page.getByRole('heading', { name: '今日控制台' })).toBeVisible();
}
async function editTask(client, before, after) {
  const row = client.page.locator('.task-row').filter({ has: client.page.getByRole('heading', { name: before, exact: true }) });
  await row.getByRole('button', { name: '编辑', exact: true }).click();
  await client.page.getByLabel('任务标题', { exact: true }).fill(after);
  await client.page.getByRole('button', { name: '保存修改', exact: true }).click();
  await expect(client.page.getByRole('heading', { name: after, exact: true })).toBeVisible();
}
async function capture(row, suffix = '') {
  for (const client of clients) {
    if (!client.page || client.page.isClosed()) continue;
    try {
      if (await client.page.locator('input[type=password]').count()) continue;
      if (options.roadmap || options.aiWorkbench || options.stableTrial) {
        const activity = await adb('shell', 'dumpsys', 'activity', 'activities');
        if (!activity.split(/\r?\n/).some(line => /mResumedActivity|topResumedActivity/.test(line) && line.includes(`${ANDROID_PACKAGE}/`))) continue;
      }
    } catch { continue; } // Lost diagnostics must not mask the original assertion.
    const file = `${String(report.cases.length).padStart(2, '0')}-${client.kind}-${row.status.toLowerCase()}${suffix}.png`;
    try { await client.page.screenshot({ path: join(runDir, file), timeout: 5000 }); row.screenshots.push(`${runId}/${file}`); }
    catch {
      if (client.kind !== 'android') continue;
      // Some Android WebView versions do not support CDP captureScreenshot.
      // Capture the physical display only when our synthetic test app is foreground.
      try {
        const activity = await adb('shell', 'dumpsys', 'activity', 'activities');
        if (!activity.split(/\r?\n/).some(line => /mResumedActivity|topResumedActivity/.test(line) && line.includes(`${ANDROID_PACKAGE}/`))) continue;
        const { stdout } = await exec(adbPath, ['-s', serial, 'exec-out', 'screencap', '-p'], { windowsHide: true, encoding: 'buffer', maxBuffer: 8 * 1024 * 1024, timeout: 10_000 });
        await writeFile(join(runDir, file), stdout); row.screenshots.push(`${runId}/${file}`);
      } catch { /* A lost screenshot must not hide a failed assertion. */ }
    }
  }
}
async function scenario(name, action) {
  const started = Date.now(); const row = { name, status: 'PASS', durationMs: 0, screenshots: [] };
  report.cases.push(row);
  try {
    if (options.roadmap || options.aiWorkbench || options.stableTrial) {
      const activity = await adb('shell', 'dumpsys', 'activity', 'activities');
      if (!activity.split(/\r?\n/).some(line => /mResumedActivity|topResumedActivity/.test(line) && line.includes(`${ANDROID_PACKAGE}/`)))
        throw new Error('Independent Android test app is no longer foreground; UI automation stopped.');
    }
    await action();
  }
  catch (error) { row.status = 'FAIL'; row.error = redact(error.stack || error.message); throw error; }
  finally { row.durationMs = Date.now() - started; await capture(row); console.log(`[${row.status}] ${name} (${row.durationMs} ms)`); }
}
async function preflight() {
  const manifest = validateManifest(JSON.parse(await readFile(join(artifactDir, 'build.json'), 'utf8')));
  if (await sourceFingerprint(root) !== manifest.sourceSha256) throw new Error('Client source changed after building; run prepare-native-regression.bat first.');
  await verifyHash(join(root, 'backend/target-acceptance/plan-platform-api.jar'), manifest.apiSha256);
  await verifyHash(exe, manifest.desktopSha256);
  const apk = join(artifactDir, manifest.apkName); await verifyHash(apk, manifest.apkSha256);
  if (!existsSync(java)) throw new Error('Set JAVA_HOME to Java 21.');
  await assertPortsFree(Object.values(PORTS));
  if (options.platform !== 'windows') {
    serial = selectDevice(await adb('devices'), options.serial);
    const forwards = await adb('forward', '--list'); const reverses = await adb('reverse', '--list');
    if (forwards.split(/\r?\n/).some(line => line.split(/\s+/)[1] === `tcp:${PORTS.androidCdp}`) || reverses.includes(`tcp:${PORTS.androidProxy}`)) throw new Error('A regression ADB port is already mapped. No existing mapping was changed.');
    const badging = (await exec(join(process.env.ANDROID_HOME || join(root, '.desktop-tools/android-sdk'), 'build-tools/36.0.0/aapt.exe'), ['dump', 'badging', manifest.apkName], { cwd: artifactDir, windowsHide: true })).stdout;
    if (!badging.includes(`package: name='${ANDROID_PACKAGE}'`)) throw new Error('Refusing to install a non-regression APK.');
    formalAndroidBefore = await adb('shell', 'dumpsys', 'package', 'cn.zijun701.plan.app');
    formalAndroidBefore = formalAndroidBefore.match(/versionCode=\d+[^\r\n]*|versionName=[^\r\n]+/g)?.join('\n') || 'not installed';
    report.environment.android = { model: await adb('shell', 'getprop', 'ro.product.model'), release: await adb('shell', 'getprop', 'ro.build.version.release'), package: ANDROID_PACKAGE, installedMainVersionBefore: formalAndroidBefore };
  }
  report.environment.ports = PORTS;
  report.environment.artifacts = { desktopSha256: manifest.desktopSha256, apkSha256: manifest.apkSha256 };
  return apk;
}
async function startApi() {
  apiLog = createWriteStream(join(runDir, 'api.log'));
  const snapshot = join(runDir, 'api.jar');
  await copyFile(join(root, 'backend/target-acceptance/plan-platform-api.jar'), snapshot);
  const manifest = JSON.parse(await readFile(join(artifactDir, 'build.json'), 'utf8'));
  await verifyHash(snapshot, manifest.apiSha256);
  const args = ['-Duser.timezone=Asia/Shanghai', '-jar', snapshot,
    '--server.address=127.0.0.1', `--server.port=${PORTS.api}`, '--spring.profiles.active=acceptance',
    `--spring.datasource.url=jdbc:h2:mem:native_${randomUUID().replaceAll('-', '')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`,
    '--spring.datasource.driver-class-name=org.h2.Driver', '--spring.datasource.username=sa', '--spring.datasource.password=',
    '--app.security.registration-enabled=true', '--app.security.email-verification-enabled=false', '--app.security.invitation-enabled=false',
    '--app.security.refresh-cookie-secure=false', '--app.security.refresh-cookie-name=plan_refresh', '--app.security.max-sessions=40',
    '--app.security.password-reset-enabled=false', `--app.security.bootstrap-admin-email=${options.roadmap ? bootstrapEmail : ''}`, '--spring.h2.console.enabled=false',
    '--app.jwt.secret=native-regression-not-production-at-least-32-bytes', '--app.jwt.issuer=plan-platform-native-regression',
    '--app.ai.enabled=false', '--app.mail-from=', '--management.health.mail.enabled=false',
    '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration',
    '--app.operations.mail-alerts-enabled=false', '--app.operations.scheduler-enabled=false'];
  if (options.roadmap) args.push(
    `--app.bootstrap.token-hash=${createHash('sha256').update(bootstrapToken).digest('hex')}`,
    `--app.bootstrap.expires-at=${new Date(Date.now() + 3600_000).toISOString()}`,
    '--app.operations.enabled=true', '--app.governance.enabled=true');
  apiProcess = spawn(java, args, { cwd: join(root, 'backend'), windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  apiProcess.stdout.pipe(apiLog); apiProcess.stderr.pipe(apiLog);
  let startup = '';
  apiProcess.stdout.on('data', chunk => { startup = (startup + chunk).slice(-16_384); });
  await waitFor(async () => {
    if (apiProcess.exitCode !== null) throw new Error('Isolated API exited. See api.log.');
    if (!startup.includes(`Tomcat started on port ${PORTS.api}`)) return false;
    try { const response = await fetch(`http://127.0.0.1:${PORTS.api}/actuator/health`, { signal: AbortSignal.timeout(1000) }); return response.ok && (await response.json()).status === 'UP'; } catch { return false; }
  }, 'isolated memory API health', 90_000);
}
async function run() {
  const apk = await preflight();
  if (options.check) { console.log('Preflight passed; no app was launched or installed.'); return; }
  const requireStableForeground=async()=>{if(options.stableTrial){const activity=await adb('shell','dumpsys','activity','activities');if(!activity.split(/\r?\n/).some(line=>/mResumedActivity|topResumedActivity/.test(line)&&line.includes(`${ANDROID_PACKAGE}/`)))throw Error('Return to the independent Android test app before starting; unrelated app left untouched.');}};
  await requireStableForeground();
  await startApi();
  if (options.platform !== 'android') clients.push({ kind: 'windows', proxyPort: PORTS.windowsProxy, cdp: PORTS.desktopCdp, browser: null });
  if (options.platform !== 'windows') {
    let identicalInstalled = false;
    if (options.roadmap || options.stableTrial) {
      const location = (await adb('shell', 'pm', 'path', ANDROID_PACKAGE)).match(/^package:(\/data\/app\/[^\r\n]+\/base\.apk)$/m)?.[1];
      if (location) {
        const hash = (await adb('shell', 'sha256sum', location)).split(/\s+/)[0];
        identicalInstalled = hash.toLowerCase() === report.environment.artifacts.apkSha256.toLowerCase();
      }
    }
    if (!identicalInstalled) {
      const install = await adb('install', ...(options.stableTrial ? ['--no-streaming'] : []), '-r', apk);
      if (!install.includes('Success')) throw new Error('Regression APK installation failed; do not uninstall the main app.');
    }
    report.environment.android.installPerformed = !identicalInstalled;
    await adb('reverse', `tcp:${PORTS.androidProxy}`, `tcp:${PORTS.androidProxy}`); reverseOwned = true;
    clients.push({ kind: 'android', proxyPort: PORTS.androidProxy, cdp: PORTS.androidCdp, browser: null });
  }
  for (const client of clients) { client.proxy = await createFaultProxy(client.proxyPort, PORTS.api); await requireStableForeground();await launch(client); }
  const owner = await account('原生回归账号'); const other = await account('隔离回归账号');
  const first = clients[0]; const editor = clients.at(-1);
  if(options.stableTrial){
    report.title=options.stableFiles?'Android0.1.19系统文件两组补验':'Android0.1.19模板与试用收口定向验收';
    report.scope='独立真机debug包、真实Java桥、合成账号和隔离API。模板、教学、重开、JSON分享取消与系统文件选择后只读预检；不覆盖正式应用，不声称外部分享目的地或正式升级通过。';
    await exerciseStableAndroid({client:first,owner,api,expect,route,restart,configure,scenario,adb,runDir,filesOnly:options.stableFiles});
    report.status='PASS';return;
  }
  if(options.aiWorkbench){
    report.title='Android AI工作台定向验收';
    report.scope='独立真机包、真实Java桥、合成账号与隔离API；无模型调用、真实邮件或正式应用写入。';
    await exerciseAndroidAiWorkbench({client:first,owner,api,expect,route,restart,configure,scenario,adb});
    report.status='PASS';return;
  }
  if (options.roadmap) {
    report.title = 'Android 0.1.15 六阶段定向验收';
    report.scope = '独立 Android 真机包、真实 Java 网络桥、隔离内存 API 与合成账号。仅本轮新增功能；无付费 AI、真实邮件、生产写入。文件恢复使用合成文件注入，不代表系统文件选择器或正式升级已验收。';
    const admin = await account('合成治理管理员', true);
    report.environment.roadmapSection = options.roadmapSection;
    await exerciseAndroidRoadmap({ client: first, owner, other, admin, api, expect, route, restart, configure, scenario, section: options.roadmapSection });
    report.status = 'PASS';
    return;
  }
  let currentPlan, tasks;
  const date = (await api('/dashboard/today', undefined, owner.token)).date;
  const end = new Date(`${date}T12:00:00+08:00`); end.setUTCDate(end.getUTCDate() + 6);
  const endDate = end.toISOString().slice(0, 10);
  await scenario('真实原生桥接、服务器配置与双端登录', async () => {
    for (const client of clients) await configure(client, owner);
  });
  await scenario('四节教学实际操作、保存重开与真实业务隔离', async () => {
    for (const client of clients) await exerciseLearning(client, expect, route, async (target, path) => { await restart(target); await route(target, path); });
    expect(await api('/plans', undefined, owner.token)).toEqual([]);
    expect(await api('/mainlines', undefined, owner.token)).toEqual([]);
    expect(await api(`/todos?from=${date}&to=${endDate}`, undefined, owner.token)).toEqual([]);
  });
  await scenario('UI 创建计划和任务，其他客户端可见；默认不把周末当休息日', async () => {
    await route(first, '/plans');
    await first.page.getByLabel('计划名称', { exact: true }).fill('原生回归计划');
    await first.page.getByLabel('开始日期', { exact: true }).fill(date);
    await first.page.getByLabel('结束日期', { exact: true }).fill(endDate);
    await first.page.getByRole('button', { name: '保存计划', exact: true }).click();
    await expect(first.page.getByRole('heading', { name: '原生回归计划', exact: true })).toBeVisible();
    currentPlan = (await api('/plans', undefined, owner.token)).find(plan => plan.name === '原生回归计划');
    expect(currentPlan.restDays).toEqual([]);
    for (const title of ['任务 A', '任务 B']) {
      await first.page.getByRole('button', { name: '添加任务', exact: true }).click();
      await first.page.getByLabel('任务标题', { exact: true }).fill(title);
      await first.page.getByRole('button', { name: '保存任务', exact: true }).click();
      await expect(first.page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    }
    tasks = await api(`/plans/${currentPlan.id}/tasks`, undefined, owner.token);
    expect(tasks).toHaveLength(2);
    for (const client of clients) {
      await refresh(client, `/plans/${currentPlan.id}`);
      await expect(client.page.getByRole('heading', { name: '任务 A', exact: true })).toBeVisible();
      await route(client, `/schedule?plan=${currentPlan.id}`);
      await expect(client.page.locator('.rail-day')).toHaveCount(7);
      await expect(client.page.locator('.rail-day--rest')).toHaveCount(0);
    }
  });
  await scenario('休息日二次编辑互通：只有星期日休息，星期六为计划日', async () => {
    await route(editor, `/plans/${currentPlan.id}`);
    await editor.page.getByRole('button', { name: '编辑计划设置', exact: true }).click();
    await editor.page.getByLabel('星期日', { exact: true }).check();
    await editor.page.getByRole('button', { name: '保存计划设置', exact: true }).click();
    await expect(editor.page.getByRole('button', { name: '编辑计划设置', exact: true })).toBeEnabled();
    expect((await api('/plans', undefined, owner.token))[0].restDays).toEqual([7]);
    for (const client of clients) {
      await refresh(client, `/schedule?plan=${currentPlan.id}`);
      await expect(client.page.locator('.rail-day--rest')).toHaveCount(1);
      await expect(client.page.locator('.rail-day--rest')).toContainText('周日');
      await expect(client.page.locator('.rail-day').filter({ hasText: '周六' })).toContainText('计划日');
      await expect(client.page.getByRole('button', { name: /任务 A/ })).toBeVisible();
    }
  });
  await scenario('布局适配：手机底部导航与日程详情面板，PC 保留桌面导航', async () => {
    for (const client of clients) {
      const nav = client.page.getByRole('navigation', { name: '手机主导航' });
      if (client.kind === 'android') {
        await expect(nav).toBeVisible();
        expect(await client.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(1);
        await client.page.getByRole('button', { name: /任务 A/ }).click();
        const dialog = client.page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        await expect(dialog.getByLabel('执行状态')).toBeVisible();
        await capture(report.cases.at(-1), '-panel');
        await dialog.getByRole('button', { name: '关闭面板', exact: true }).click();
        await expect(dialog).not.toBeVisible();
        await nav.getByRole('button', { name: '更多', exact: true }).click();
        await client.page.getByRole('dialog').getByRole('link', { name: '同步', exact: true }).click();
        await expect(client.page.getByRole('heading', { name: '同步中心', exact: true })).toBeVisible();
        await expect(client.page.getByRole('dialog')).not.toBeVisible();
      } else await expect(nav).not.toBeVisible();
    }
  });
  await scenario('模式独立切换：路线位置保留，离线重开仍可查看，计划任务不受影响', async () => {
    const line = await api('/mainlines', {name:'原生目标路线',description:'合成模式测试数据',color:'#002FA7'}, owner.token, 'POST', 201);
    await api(`/mainlines/${line.id}/items`, {title:'原生路线步骤',description:'合成步骤',acceptance:['验收'],plannedMinutes:20,sortOrder:0}, owner.token, 'POST', 201);
    for (const client of clients) {
      await client.page.getByLabel('工作模式').selectOption('route');
      await expect(client.page.getByRole('heading', {name:'继续路线',exact:true})).toBeVisible();
      await client.page.getByRole('link', {name:'继续这条路线',exact:true}).click();
      await expect(client.page.getByRole('heading', {name:'原生路线步骤',exact:true})).toBeVisible();
      await client.page.getByLabel('工作模式').selectOption('plan');
      await expect(client.page.getByRole('heading', {name:'执行日程',exact:true})).toBeVisible();
      await client.page.getByLabel('工作模式').selectOption('route');
      await expect(client.page.getByRole('heading', {name:'原生路线步骤',exact:true})).toBeVisible();
      await capture(report.cases.at(-1), `-route-${client.kind}`);
      client.proxy.setOnline(false);
      await restart(client);
      await expect(client.page.getByLabel('工作模式')).toHaveValue('route');
      await expect(client.page.getByRole('heading', {name:'原生路线步骤',exact:true})).toBeVisible();
      await expect(client.page.getByLabel('连接状态')).toContainText('离线工作区');
      await capture(report.cases.at(-1), `-offline-route-${client.kind}`);
      client.proxy.setOnline(true);
      await client.page.getByLabel('工作模式').selectOption('plan');
      await expect(client.page.getByRole('heading', {name:'执行日程',exact:true})).toBeVisible();
      await refresh(client, `/plans/${currentPlan.id}`);
      await expect(client.page.getByRole('heading', {name:'任务 A',exact:true})).toBeVisible();
    }
  });
  await scenario('目标路线阶段与编辑面板：前置依赖互通，真实离线编辑重开与同步', async () => {
    const line = await api('/mainlines', {name:'原生阶段路线',color:'#002FA7',executionMode:'FLEXIBLE'}, owner.token, 'POST', 201);
    const stage = await api(`/mainlines/${line.id}/stages`, {name:'基础练习',description:'合成阶段',sortOrder:0}, owner.token, 'POST', 201);
    const firstStep = await api(`/mainlines/${line.id}/items`, {title:'基础知识',description:'合成步骤',acceptance:['验收'],plannedMinutes:20,sortOrder:0,routing:{stageId:stage.id,dependencyMode:'INHERIT',prerequisiteIds:[]}}, owner.token, 'POST', 201);
    const second = await api(`/mainlines/${line.id}/items`, {title:'和弦练习',description:'合成步骤',acceptance:['验收'],plannedMinutes:20,sortOrder:1,routing:{stageId:stage.id,dependencyMode:'EXPLICIT',prerequisiteIds:[firstStep.id]}}, owner.token, 'POST', 201);
    for (const client of clients) {
      await refresh(client, `/mainlines?line=${line.id}`);
      const row = client.page.locator('.mainline-step').filter({has:client.page.getByRole('heading',{name:'和弦练习',exact:true})});
      await expect(row.getByRole('button',{name:'开始',exact:true})).toBeDisabled();
      await row.getByRole('button',{name:'编辑步骤',exact:true}).click();
      const panel = client.page.getByRole('dialog');
      await expect(panel.getByLabel('步骤标题',{exact:true})).toHaveValue('和弦练习');
      await expect(panel.getByLabel('基础知识',{exact:true})).toBeChecked();
      await capture(report.cases.at(-1), `-step-panel-${client.kind}`);
      await panel.getByRole('button',{name:'关闭面板',exact:true}).click();
      await client.page.getByRole('button',{name:'折叠全部',exact:true}).click(); await expect(client.page.locator('.mainline-step')).toHaveCount(0);
      await client.page.getByRole('button',{name:'展开全部',exact:true}).click(); await expect(client.page.locator('.mainline-step')).toHaveCount(2);
      expect(await client.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(1);
    }
    await api(`/mainlines/items/${firstStep.id}/complete`,{note:'验收',version:firstStep.version},owner.token);
    let title='和弦练习';
    for (const client of clients) {
      await refresh(client, `/mainlines?line=${line.id}`);
      const row=client.page.locator('.mainline-step').filter({has:client.page.getByRole('heading',{name:title,exact:true})});
      await expect(row.getByRole('button',{name:'开始',exact:true})).toBeEnabled();
      client.proxy.setOnline(false);
      // Reload through the actual native bridge before editing so the app knows it is offline.
      await refresh(client, `/mainlines?line=${line.id}`);
      await expect(client.page.getByLabel('连接状态')).toContainText('离线工作区');
      await client.page.locator('.mainline-step').filter({has:client.page.getByRole('heading',{name:title,exact:true})}).getByRole('button',{name:'编辑步骤',exact:true}).click();
      const panel=client.page.getByRole('dialog'); await expect(panel.getByLabel('前置条件',{exact:true})).toBeDisabled();
      title = `和弦练习－${client.kind}离线`; await panel.getByLabel('步骤标题',{exact:true}).fill(title);
      await panel.getByRole('button',{name:'保存步骤',exact:true}).click(); await expect(panel).not.toBeVisible();
      await restart(client); await route(client, `/mainlines?line=${line.id}`);
      await expect(client.page.getByRole('heading',{name:title,exact:true})).toBeVisible();
      await route(client,'/sync'); await expect(client.page.locator('.sync-row')).toHaveCount(1);
      client.proxy.setOnline(true); await client.page.getByRole('button',{name:'同步等待项',exact:true}).click();
      await expect(client.page.getByText('队列为空',{exact:true})).toBeVisible();
      const updated=(await api('/mainlines',undefined,owner.token)).find(l=>l.id===line.id).items.find(i=>i.id===second.id);
      expect(updated.title).toBe(title); expect(updated.prerequisiteIds).toEqual([firstStep.id]); expect(updated.stageId).toBe(stage.id);
    }
    for (const client of clients) {
      await refresh(client, `/mainlines?line=${line.id}`); await expect(client.page.getByRole('heading',{name:title,exact:true})).toBeVisible();
      await client.page.getByLabel('工作模式').selectOption('plan'); await route(client, `/plans/${currentPlan.id}`);
    }
  });
  await scenario('今日待办独立模式：原生新增、离线编辑、重开与幂等同步', async () => {
    for (const client of clients) {
      await client.page.getByLabel('工作模式').selectOption('todo');
      await expect(client.page.getByRole('heading', {name:'今日待办',exact:true})).toBeVisible();
      await client.page.getByLabel('查看日期').fill('2026-10-12');
      const title=`原生待办-${client.kind}`;
      await client.page.getByLabel('快速记录').fill(title);
      await client.page.getByRole('button',{name:'记一件事',exact:true}).click();
      await expect(client.page.getByRole('heading',{name:title,exact:true})).toBeVisible();
      client.proxy.setOnline(false); await refresh(client,'/todos');
      await client.page.getByLabel('查看日期').fill('2026-10-12');
      await client.page.locator('.todo-row').filter({hasText:title}).getByRole('button',{name:'编辑',exact:true}).click();
      const panel=client.page.getByRole('dialog');
      await panel.getByLabel('待办标题').fill(`${title}-离线`);
      await panel.getByRole('button',{name:'保存待办',exact:true}).click();
      await expect(panel).not.toBeVisible();
      await restart(client); await route(client,'/todos');
      await client.page.getByLabel('查看日期').fill('2026-10-12');
      await expect(client.page.getByRole('heading',{name:`${title}-离线`,exact:true})).toBeVisible();
      await route(client,'/sync'); await expect(client.page.locator('.sync-row')).toHaveCount(1);
      client.proxy.setOnline(true); await client.page.getByRole('button',{name:'同步等待项',exact:true}).click();
      await expect(client.page.getByText('队列为空',{exact:true})).toBeVisible();
      const todos=await api('/todos?from=2026-10-12&to=2026-10-12',undefined,owner.token);
      expect(todos.filter(todo=>todo.title===`${title}-离线`)).toHaveLength(1);
      await client.page.getByLabel('工作模式').selectOption('plan'); await route(client,`/plans/${currentPlan.id}`);
      await expect(client.page.getByRole('heading',{name:'任务 A',exact:true})).toBeVisible();
    }
  });
  await scenario('终止并重开真实 EXE/APK：原生会话与计划保留', async () => {
    for (const client of clients) {
      await restart(client);
      await expect(client.page.locator('.site-header .brand')).toBeVisible();
      await route(client, `/plans/${currentPlan.id}`);
      await expect(client.page.getByRole('heading', { name: '任务 A', exact: true })).toBeVisible();
      await expect(client.page.getByText('每周 星期日 休息', { exact: true })).toBeVisible();
    }
  });
  await scenario('真实网络中断：离线修改可见，重开后仍有 1 项本地操作', async () => {
    for (let i = 0; i < clients.length; i++) {
      const client = clients[i]; const title = i === 0 ? '任务 A' : '任务 B';
      client.task = tasks.find(task => task.title === title); client.offlineTitle = `${title}－${client.kind}离线`;
      client.proxy.setOnline(false);
      await editTask(client, title, client.offlineTitle);
      await expect(client.page.locator('.task-row').filter({ hasText: client.offlineTitle })).toContainText('本地修改 · 待同步');
      await restart(client);
      await route(client, `/plans/${currentPlan.id}`);
      await expect(client.page.getByRole('heading', { name: client.offlineTitle, exact: true })).toBeVisible();
      await expect(client.page.getByLabel('连接状态')).toContainText('离线工作区');
      await route(client, '/sync'); await expect(client.page.locator('.sync-row')).toHaveCount(1);
      await expect(client.page.locator('.sync-row--conflict')).toHaveCount(0);
      expect((await api(`/plans/${currentPlan.id}/tasks`, undefined, owner.token)).find(task => task.id === client.task.id).title).toBe(title);
    }
  });
  await scenario('恢复网络：真实队列重放、双端刷新可见且没有重复任务', async () => {
    for (const client of clients) {
      client.proxy.setOnline(true);
      await client.page.getByRole('button', { name: '同步等待项', exact: true }).click();
      await expect(client.page.getByText('队列为空', { exact: true })).toBeVisible();
    }
    const rows = await api(`/plans/${currentPlan.id}/tasks`, undefined, owner.token); expect(rows).toHaveLength(2);
    for (const client of clients) {
      expect(rows.find(task => task.id === client.task.id).title).toBe(client.offlineTitle);
      await refresh(client, `/plans/${currentPlan.id}`);
      for (const peer of clients) await expect(client.page.getByRole('heading', { name: peer.offlineTitle, exact: true })).toBeVisible();
    }
  });
  await scenario('旧版本冲突停靠：不覆盖远端，后续操作保持等待', async () => {
    const latest = await api(`/plans/${currentPlan.id}/tasks`, undefined, owner.token);
    const a = latest.find(task => task.id === tasks[0].id); const b = latest.find(task => task.id === tasks[1].id);
    await refresh(editor, `/plans/${currentPlan.id}`);
    await expect(editor.page.getByRole('heading', { name: a.title, exact: true })).toBeVisible();
    editor.proxy.setOnline(false);
    await editTask(editor, a.title, '任务 A－冲突本地版');
    await editTask(editor, b.title, '任务 B－队首后等待');
    if (clients.length === 2) {
      await refresh(first, `/plans/${currentPlan.id}`);
      await editTask(first, a.title, '任务 A－PC远端版');
    } else await api(`/tasks/${a.id}`, { ...a, title: '任务 A－另一会话版', version: a.version }, owner.token, 'PUT');
    editor.proxy.setOnline(true); await route(editor, '/sync');
    await editor.page.getByRole('button', { name: '同步等待项', exact: true }).click();
    await expect(editor.page.locator('.sync-row')).toHaveCount(2);
    await expect(editor.page.locator('.sync-row--conflict')).toHaveCount(1);
    await expect(editor.page.locator('.sync-row--pending')).toHaveCount(1);
    await expect(editor.page.locator('.sync-row--pending')).toContainText('0 次尝试');
    const server = await api(`/plans/${currentPlan.id}/tasks`, undefined, owner.token);
    expect(server.find(task => task.id === a.id).title).toBe(clients.length === 2 ? '任务 A－PC远端版' : '任务 A－另一会话版');
    expect(server.find(task => task.id === b.id).title).toBe(b.title);
  });
  await scenario('退出与账号隔离：不自动恢复原账号、不泄露原计划和待同步队列', async () => {
    if (await editor.page.getByRole('button', { name: '更多', exact: true }).isVisible()) await editor.page.getByRole('button', { name: '更多', exact: true }).click();
    await editor.page.getByRole('button', { name: `${owner.name} · 退出`, exact: true }).click();
    await expect(editor.page.getByRole('heading', { name: '登录计划平台' })).toBeVisible();
    await restart(editor);
    await expect(editor.page.getByRole('heading', { name: '登录计划平台' })).toBeVisible();
    await login(editor, other); await route(editor, '/plans');
    await expect(editor.page.getByText('创建第一个计划后，它会显示在这里。')).toBeVisible();
    await route(editor, '/sync'); await expect(editor.page.getByText('队列为空', { exact: true })).toBeVisible();
    if (clients.length === 2) { await refresh(first, `/plans/${currentPlan.id}`); await expect(first.page.getByRole('heading', { name: '原生回归计划', exact: true })).toBeVisible(); }
    expect(await api('/plans', undefined, other.token)).toEqual([]);
    await api(`/plans/${currentPlan.id}/tasks`, undefined, other.token, 'GET', 404);
  });
  report.status = 'PASS';
}
async function cleanup() {
  const errors = [];
  const attempt = async fn => { try { await fn(); } catch (error) { errors.push(redact(error.message)); } };
  for (const client of clients) {
    await attempt(async () => { if (client.browser) await client.browser.close(); });
    await attempt(async () => client.kind === 'windows' ? stopChild(client.process) : adb('shell', 'am', 'force-stop', ANDROID_PACKAGE));
    await attempt(async () => { if (client.proxy) await client.proxy.close(); });
  }
  if (forwardOwned) await attempt(() => adb('forward', '--remove', `tcp:${PORTS.androidCdp}`));
  if (reverseOwned) await attempt(() => adb('reverse', '--remove', `tcp:${PORTS.androidProxy}`));
  await attempt(() => stopChild(apiProcess));
  if (apiProcess) await attempt(async () => { await unlink(join(runDir, 'api.jar')); });
  apiLog?.end();
  if (formalAndroidBefore !== undefined) await attempt(async () => {
    const after = (await adb('shell', 'dumpsys', 'package', 'cn.zijun701.plan.app')).match(/versionCode=\d+[^\r\n]*|versionName=[^\r\n]+/g)?.join('\n') || 'not installed';
    report.environment.android.installedMainVersionAfter = after;
    expect(after).toBe(formalAndroidBefore);
  });
  if (errors.length) throw new Error(`Cleanup failed: ${errors.join('; ')}`);
}
await mkdir(runDir, { recursive: true });
try {
  lock = await open(join(resultsRoot, 'run.lock'), 'wx');
  await lock.writeFile(String(process.pid));
  await run();
} catch (error) {
  report.error = redact(error.stack || error.message); console.error(redact(error.message)); process.exitCode = 1;
} finally {
  try { await cleanup(); } catch (error) { report.status = 'FAIL'; report.cleanupError = redact(error.message); report.error = [report.error, report.cleanupError].filter(Boolean).join('\n'); process.exitCode = 1; }
  if (lock) { await lock.close(); await unlink(join(resultsRoot, 'run.lock')); }
  if (!options.check || report.error) {
    await writeFile(join(runDir, 'report.json'), JSON.stringify(report, null, 2));
    await writeFile(join(resultsRoot, 'latest.html'), reportHtml(report));
    await writeFile(join(resultsRoot, 'latest.junit.xml'), junitXml(report));
    console.log(`Report: ${join(resultsRoot, 'latest.html')}`);
  }
}
