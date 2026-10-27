import { reactive } from 'vue';
import { checkDesktopUpdate, downloadDesktopUpdate, getDesktopInfo, installDesktopUpdate, isNativeClient, nativeInvoke, type DesktopInfo, type DesktopUpdate } from '@/desktop';
const CONSENT = 'plan-platform:update-download-consent';
const POLICY = 'plan-platform:last-required-update';
const PREFERENCES = 'plan-platform:update-preferences:v2';
interface Preference {
    key: string;
    auto?: boolean;
    ignored?: string[];
    snooze?: {
        target: string;
        until: number;
    };
    nextCheck?: number;
    failures?: number;
    checkedBuild?: string;
    policy?: {
        build: string;
        update: DesktopUpdate;
    };
}
const NORMAL_INTERVAL = 15 * 60 * 1000;
export const clientUpdates = reactive({
    info: null as DesktopInfo | null, update: null as DesktopUpdate | null,
    checking: false, downloading: false, installing: false, ready: false,
    autoDownload: false, dismissedVersion: '', error: '', message: '', ownerKey: '',
    ignored: false, snoozeUntil: 0, nextCheck: 0, failures: 0, noticeTime: Date.now(),
});
function scopeKey(): string { const i = clientUpdates.info; return JSON.stringify([i?.versionCode === undefined ? 'windows' : 'android', i?.updateChannel ?? '', i?.updateRequiresLogin ? clientUpdates.ownerKey : '']); }
function targetKey(u: DesktopUpdate): string { return `${u.version}|${u.versionCode ?? ''}`; }
function preferences(): Preference[] { const value = read(PREFERENCES); return Array.isArray(value) ? value.filter(p => p && typeof p.key === 'string').slice(-24) : []; }
function preference(): Preference { return preferences().find(p => p.key === scopeKey()) ?? { key: scopeKey() }; }
function savePreference(change: Partial<Preference>): boolean {
    try {
        const entry = { ...preference(), ...change, key: scopeKey() };
        localStorage.setItem(PREFERENCES, JSON.stringify([...preferences().filter(p => p.key !== entry.key), entry].slice(-24)));
        return true;
    }
    catch {
        return false;
    }
}
function applyVisibility(): void { const p = preference(), u = clientUpdates.update; clientUpdates.ignored = !!u && !u.mandatory && Array.isArray(p.ignored) && p.ignored.includes(targetKey(u)); clientUpdates.snoozeUntil = u && !u.mandatory && p.snooze?.target === targetKey(u) && Number.isFinite(p.snooze.until) ? p.snooze.until : 0; }
export function clientNoticeVisible(): boolean { void clientUpdates.noticeTime; return !!clientUpdates.update && (clientUpdates.update.mandatory || !clientUpdates.ignored && Date.now() >= clientUpdates.snoozeUntil); }
function currentBuild() { const info = clientUpdates.info; return `${info?.updateChannel ?? ''}|${info?.version ?? ''}|${info?.versionCode ?? ''}`; }
function failure(error: unknown) { return error instanceof Error ? error.message : String(error); }
function read(key: string): unknown { try {
    return JSON.parse(localStorage.getItem(key) ?? 'null');
}
catch {
    return null;
} }
function restorePreference(): void {
    const p = preference();
    const consent = read(CONSENT) as {
        enabled?: boolean;
        channel?: string;
        owner?: string;
    } | null;
    clientUpdates.autoDownload = consent?.enabled === true && !!clientUpdates.info?.updateChannel && consent.channel === clientUpdates.info.updateChannel
        && (!clientUpdates.info.updateRequiresLogin || (!!clientUpdates.ownerKey && consent.owner === clientUpdates.ownerKey));
    if (typeof p.auto === 'boolean')
        clientUpdates.autoDownload = p.auto && !!clientUpdates.info?.updatesEnabled && (!clientUpdates.info.updateRequiresLogin || !!clientUpdates.ownerKey);
    clientUpdates.nextCheck = p.checkedBuild === currentBuild() && typeof p.nextCheck === 'number' && p.nextCheck > Date.now() && p.nextCheck < Date.now() + 3600001 ? p.nextCheck : 0;
    const failures = p.failures;
    clientUpdates.failures = typeof failures === 'number' && Number.isInteger(failures) && failures >= 0 && failures <= 6 ? failures : 0;
    const saved = read(POLICY) as {
        build?: string;
        update?: DesktopUpdate;
        owner?: string;
    } | null;
    const cachedPolicy = validUpdate(saved?.update);
    if (clientUpdates.info?.updatesEnabled && saved?.build === currentBuild() && cachedPolicy?.mandatory === true
        && (!clientUpdates.info.updateRequiresLogin || (!!clientUpdates.ownerKey && saved.owner === clientUpdates.ownerKey)))
        clientUpdates.update = cachedPolicy;
    if (clientUpdates.info?.updatesEnabled && (!clientUpdates.info.updateRequiresLogin || !!clientUpdates.ownerKey) && p.policy?.build === currentBuild() && validUpdate(p.policy.update)?.mandatory)
        clientUpdates.update = validUpdate(p.policy.update);
    applyVisibility();
}
export function bindClientUpdateOwner(owner: string | null): void {
    const next = owner ?? '';
    if (clientUpdates.ownerKey === next)
        return;
    clientUpdates.ownerKey = next;
    if (!clientUpdates.info?.updateRequiresLogin)
        return;
    clientUpdates.update = null;
    clientUpdates.ready = false;
    clientUpdates.dismissedVersion = '';
    clientUpdates.error = '';
    clientUpdates.message = '';
    restorePreference();
}
export function requiredClientUpdate(): boolean { return isNativeClient() && clientUpdates.update?.mandatory === true; }
export function updateRouteAllowed(path: string): boolean { return ['/desktop', '/sync', '/exports', '/settings', '/login'].includes(path); }
export async function initializeClientUpdates(): Promise<void> {
    if (!isNativeClient())
        return;
    const old = scopeKey(), build = currentBuild();
    clientUpdates.info = await getDesktopInfo();
    if (old !== scopeKey() || build !== currentBuild()) {
        clientUpdates.update = null;
        clientUpdates.ready = false;
        clientUpdates.dismissedVersion = '';
        clientUpdates.error = '';
        clientUpdates.message = '';
    }
    restorePreference();
}
export function setAutomaticDownload(value: boolean): void {
    // Consent is explicit, channel-bound, and never inferred from a missing setting.
    if (!clientUpdates.info?.updatesEnabled)
        return;
    if (clientUpdates.info.updateRequiresLogin && !clientUpdates.ownerKey) {
        clientUpdates.error = '请先登录受邀账号，再设置自动下载。';
        return;
    }
    const consent = { enabled: value === true, channel: clientUpdates.info.updateChannel, ...(clientUpdates.info.updateRequiresLogin ? { owner: clientUpdates.ownerKey } : {}) };
    if (!savePreference({ auto: consent.enabled })) {
        clientUpdates.error = '无法保存更新偏好，本次没有启用自动下载。';
        return;
    }
    clientUpdates.autoDownload = consent.enabled;
    // The scoped record is authoritative. A failed legacy compatibility write
    // must not resurrect consent that the user was told was not saved.
    try { localStorage.setItem(CONSENT, JSON.stringify(consent)); } catch { /* New record is durable. */ }
    if (clientUpdates.autoDownload && clientUpdates.update && !clientUpdates.ready && !clientUpdates.ignored)
        void downloadClientUpdate();
}
function validUpdate(value: unknown): DesktopUpdate | null {
    if (!value || typeof value !== 'object')
        return null;
    const u = value as DesktopUpdate;
    if (!/^\d+\.\d+\.\d+$/.test(u.version) || typeof u.mandatory !== 'boolean' || u.versionCode !== undefined && (!Number.isSafeInteger(u.versionCode) || u.versionCode < 1) || u.notes !== null && u.notes !== undefined && typeof u.notes !== 'string')
        return null;
    return { ...u, notes: typeof u.notes === 'string' ? u.notes.slice(0, 4000) : null };
}
export async function checkClientUpdate(reason: 'manual' | 'automatic' = 'manual'): Promise<void> {
    clientUpdates.noticeTime = Date.now();
    if (!clientUpdates.info?.updatesEnabled || clientUpdates.checking || clientUpdates.downloading || clientUpdates.installing || clientUpdates.ready)
        return;
    if (clientUpdates.info.updateRequiresLogin && !clientUpdates.ownerKey) {
        if (reason === 'manual')
            clientUpdates.error = '请先联网登录受邀账号，再检查更新。';
        return;
    }
    if (reason === 'automatic' && (typeof navigator !== 'undefined' && navigator.onLine === false || Date.now() < clientUpdates.nextCheck))
        return;
    clientUpdates.checking = true;
    clientUpdates.error = '';
    clientUpdates.message = '';
    const owner = clientUpdates.ownerKey, scope = scopeKey();
    try {
        const update = await checkDesktopUpdate();
        if (scope !== scopeKey() || clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey)
            return;
        if (update && !validUpdate(update))
            throw new Error('更新清单格式无效，未进行下载。');
        clientUpdates.update = update ? validUpdate(update) : null;
        clientUpdates.failures = 0;
        clientUpdates.nextCheck = Date.now() + NORMAL_INTERVAL;
        savePreference({ nextCheck: clientUpdates.nextCheck, checkedBuild: currentBuild(), failures: 0, policy: update?.mandatory ? { build: currentBuild(), update: clientUpdates.update! } : undefined });
        applyVisibility();
        try {
            if (update?.mandatory)
                localStorage.setItem(POLICY, JSON.stringify({ build: currentBuild(), update, ...(clientUpdates.info?.updateRequiresLogin ? { owner } : {}) }));
            else
                localStorage.removeItem(POLICY);
        }
        catch { /* Keep the verified in-memory policy; never turn a required update into optional. */ }
        if (!update)
            clientUpdates.message = '当前已是最新版本。';
        if (update && clientUpdates.autoDownload && !clientUpdates.ignored)
            await downloadClientUpdate();
    }
    catch (error) {
        if (scope === scopeKey() && (!clientUpdates.info?.updateRequiresLogin || owner === clientUpdates.ownerKey)) {
            clientUpdates.error = failure(error);
            clientUpdates.failures = Math.min(6, clientUpdates.failures + 1);
            clientUpdates.nextCheck = Date.now() + Math.min(3600000, 60000 * 2 ** (clientUpdates.failures - 1));
            savePreference({ nextCheck: clientUpdates.nextCheck, checkedBuild: currentBuild(), failures: clientUpdates.failures });
        }
    }
    finally {
        clientUpdates.checking = false;
    }
}
export async function downloadClientUpdate(): Promise<void> {
    if (!clientUpdates.update || clientUpdates.downloading || clientUpdates.installing || clientUpdates.ready)
        return;
    clientUpdates.downloading = true;
    clientUpdates.error = '';
    clientUpdates.message = '正在下载并校验更新包，不会自动关闭应用。';
    const owner = clientUpdates.ownerKey;
    try {
        await downloadDesktopUpdate();
        if (clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey)
            return;
        clientUpdates.ready = true;
        clientUpdates.message = '更新包已校验，安装前请保存正在编辑的内容。';
    }
    catch (error) {
        if (!clientUpdates.info?.updateRequiresLogin || owner === clientUpdates.ownerKey) {
            clientUpdates.error = failure(error);
            clientUpdates.message = '';
        }
    }
    finally {
        clientUpdates.downloading = false;
    }
}
export async function installClientUpdate(): Promise<void> {
    if (!clientUpdates.ready || clientUpdates.installing)
        return;
    if (!window.confirm('请先保存正在编辑的内容。安装会关闭客户端或打开系统安装确认；本地待同步操作会保留，不会自动提交。继续安装吗？'))
        return;
    clientUpdates.installing = true;
    clientUpdates.error = '';
    const owner = clientUpdates.ownerKey;
    try {
        await installDesktopUpdate();
        if (clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey)
            return;
        clientUpdates.message = '已请求安装，请完成系统提示；取消安装后可再次点击。';
    }
    catch (error) {
        if (clientUpdates.info?.updateRequiresLogin && owner !== clientUpdates.ownerKey)
            return;
        clientUpdates.error = failure(error);
        if (/校验失败|不匹配/.test(clientUpdates.error)) {
            clientUpdates.ready = false;
            clientUpdates.message = '更新包已拒绝，请重新下载。';
        }
    }
    finally {
        clientUpdates.installing = false;
    }
}
export async function allowAndroidUpdateInstall(): Promise<void> {
    try {
        await nativeInvoke('desktop_allow_update_install');
    }
    catch (error) {
        clientUpdates.error = failure(error);
    }
}
export function dismissClientUpdate(): void {
    if (clientUpdates.update && !clientUpdates.update.mandatory) {
        const until = Date.now() + 3600000;
        if (!savePreference({ snooze: { target: targetKey(clientUpdates.update), until } })) {
            clientUpdates.error = '无法保存稍后提醒，重开后可能再次提示。';
        }
        clientUpdates.dismissedVersion = clientUpdates.update.version;
        clientUpdates.snoozeUntil = until;
    }
}
export function ignoreClientUpdate(): void { const u = clientUpdates.update; if (!u || u.mandatory)
    return; const ignored = preference().ignored; const values = Array.isArray(ignored) ? ignored.filter(v => typeof v === 'string') : []; if (savePreference({ ignored: [...new Set([...values, targetKey(u)])].slice(-12) }))
    applyVisibility();
else
    clientUpdates.error = '无法保存忽略设置，仍保留版本提醒。'; }
export function restoreClientUpdateNotice(): void { const u = clientUpdates.update; if (!u)
    return; const p = preference(); if (savePreference({ ignored: Array.isArray(p.ignored) ? p.ignored.filter(v => v !== targetKey(u)) : [], snooze: undefined })) {
    clientUpdates.dismissedVersion = '';
    applyVisibility();
}
else
    clientUpdates.error = '无法恢复提醒，请检查本机存储。'; }
