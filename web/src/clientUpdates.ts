import { reactive } from 'vue';
import { checkDesktopUpdate, downloadDesktopUpdate, getDesktopInfo, installDesktopUpdate, isNativeClient, nativeInvoke, type DesktopInfo, type DesktopUpdate } from '@/desktop';

const CONSENT = 'plan-platform:update-download-consent';
const POLICY = 'plan-platform:last-required-update';
export const clientUpdates = reactive({
  info: null as DesktopInfo | null, update: null as DesktopUpdate | null,
  checking: false, downloading: false, installing: false, ready: false,
  autoDownload: false, dismissedVersion: '', error: '', message: '', ownerKey: '',
});
function currentBuild() { const info = clientUpdates.info; return `${info?.updateChannel ?? ''}|${info?.version ?? ''}|${info?.versionCode ?? ''}`; }
function failure(error: unknown) { return error instanceof Error ? error.message : String(error); }
function read(key: string): unknown { try { return JSON.parse(localStorage.getItem(key) ?? 'null'); } catch { return null; } }
function restorePreference(): void {
  const consent = read(CONSENT) as { enabled?: boolean; channel?: string; owner?: string } | null;
  clientUpdates.autoDownload = consent?.enabled === true && !!clientUpdates.info?.updateChannel && consent.channel === clientUpdates.info.updateChannel
    && (!clientUpdates.info.updateRequiresLogin || (!!clientUpdates.ownerKey && consent.owner === clientUpdates.ownerKey));
  const saved = read(POLICY) as { build?: string; update?: DesktopUpdate; owner?: string } | null;
  if (clientUpdates.info?.updatesEnabled && saved?.build === currentBuild() && saved.update?.mandatory === true && typeof saved.update.version === 'string'
      && (!clientUpdates.info.updateRequiresLogin || (!!clientUpdates.ownerKey && saved.owner === clientUpdates.ownerKey))) clientUpdates.update = saved.update;
}
export function bindClientUpdateOwner(owner: string | null): void {
  const next = owner ?? '';
  if (clientUpdates.ownerKey === next) return;
  clientUpdates.ownerKey = next;
  if (!clientUpdates.info?.updateRequiresLogin) return;
  clientUpdates.update = null; clientUpdates.ready = false; clientUpdates.dismissedVersion = ''; clientUpdates.error = ''; clientUpdates.message = '';
  restorePreference();
}
export function requiredClientUpdate(): boolean { return isNativeClient() && clientUpdates.update?.mandatory === true; }
export function updateRouteAllowed(path: string): boolean { return ['/desktop', '/sync', '/exports', '/settings', '/login'].includes(path); }
export async function initializeClientUpdates(): Promise<void> {
  if (!isNativeClient()) return;
  clientUpdates.info = await getDesktopInfo();
  restorePreference();
}
export function setAutomaticDownload(value: boolean): void {
  // Consent is explicit, channel-bound, and never inferred from a missing setting.
  if (!clientUpdates.info?.updatesEnabled) return;
  if (clientUpdates.info.updateRequiresLogin && !clientUpdates.ownerKey) { clientUpdates.error = '请先登录受邀账号，再设置自动下载。'; return; }
  const consent = { enabled: value === true, channel: clientUpdates.info.updateChannel, ...(clientUpdates.info.updateRequiresLogin ? { owner: clientUpdates.ownerKey } : {}) };
  try { localStorage.setItem(CONSENT, JSON.stringify(consent)); clientUpdates.autoDownload = consent.enabled; }
  catch { clientUpdates.error = '无法保存更新偏好，本次没有启用自动下载。'; return; }
  if (clientUpdates.autoDownload && clientUpdates.update && !clientUpdates.ready) void downloadClientUpdate();
}
export async function checkClientUpdate(): Promise<void> {
  if (!clientUpdates.info?.updatesEnabled || clientUpdates.checking || clientUpdates.downloading || clientUpdates.installing || clientUpdates.ready) return;
  clientUpdates.checking = true; clientUpdates.error = ''; clientUpdates.message = '';
  const owner = clientUpdates.ownerKey;
  try {
    const update = await checkDesktopUpdate();
    if (clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey) return;
    if (update && (typeof update.version !== 'string' || typeof update.mandatory !== 'boolean')) throw new Error('更新清单格式无效，未进行下载。');
    clientUpdates.update = update;
    try {
      if (update?.mandatory) localStorage.setItem(POLICY, JSON.stringify({ build: currentBuild(), update, ...(clientUpdates.info?.updateRequiresLogin ? { owner } : {}) }));
      else localStorage.removeItem(POLICY);
    } catch { /* Keep the verified in-memory policy; never turn a required update into optional. */ }
    if (!update) clientUpdates.message = '当前已是最新版本。';
    if (update && clientUpdates.autoDownload) await downloadClientUpdate();
  } catch (error) { if (!clientUpdates.info?.updateRequiresLogin || owner === clientUpdates.ownerKey) clientUpdates.error = failure(error); }
  finally { clientUpdates.checking = false; }
}
export async function downloadClientUpdate(): Promise<void> {
  if (!clientUpdates.update || clientUpdates.downloading || clientUpdates.installing || clientUpdates.ready) return;
  clientUpdates.downloading = true; clientUpdates.error = ''; clientUpdates.message = '正在下载并校验更新包，不会自动关闭应用。';
  const owner = clientUpdates.ownerKey;
  try { await downloadDesktopUpdate(); if (clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey) return; clientUpdates.ready = true; clientUpdates.message = '更新包已校验，安装前请保存正在编辑的内容。'; }
  catch (error) { if (!clientUpdates.info?.updateRequiresLogin || owner === clientUpdates.ownerKey) { clientUpdates.error = failure(error); clientUpdates.message = ''; } }
  finally { clientUpdates.downloading = false; }
}
export async function installClientUpdate(): Promise<void> {
  if (!clientUpdates.ready || clientUpdates.installing) return;
  if (!window.confirm('请先保存正在编辑的内容。安装会关闭客户端或打开系统安装确认；本地待同步操作会保留，不会自动提交。继续安装吗？')) return;
  clientUpdates.installing = true; clientUpdates.error = '';
  const owner = clientUpdates.ownerKey;
  try { await installDesktopUpdate(); if (clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey) return; clientUpdates.message = '已请求安装，请完成系统提示；取消安装后可再次点击。'; }
  catch (error) {
    if (clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey) return;
    clientUpdates.error = failure(error);
    if (/校验失败|不匹配/.test(clientUpdates.error)) { clientUpdates.ready = false; clientUpdates.message = '更新包已拒绝，请重新下载。'; }
  }
  finally { clientUpdates.installing = false; }
}
export async function allowAndroidUpdateInstall(): Promise<void> {
  try { await nativeInvoke('desktop_allow_update_install'); }
  catch (error) { clientUpdates.error = failure(error); }
}
export function dismissClientUpdate(): void {
  if (clientUpdates.update && !clientUpdates.update.mandatory) clientUpdates.dismissedVersion = clientUpdates.update.version;
}
