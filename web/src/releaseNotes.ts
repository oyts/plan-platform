import type { DesktopInfo, DesktopUpdate } from './desktop';
export interface ReleaseNote {
    version: string;
    date: string;
    androidCode: number;
    highlights: string[];
    cautions: string[];
}
// Authored, bundled plain text. Server versions and remote HTML never populate history.
export const releaseHistory: ReleaseNote[] = [
    { version: '0.1.26', date: '2026-10-28', androidCode: 31, highlights: ['查看往周复盘后，刷新或离线重开仍保留所选周次。', '切换复盘周次时保留未保存修改确认，本机草稿不自动覆盖云端。', '完成核心链路冻结前综合核对，帮助与版本继续可回看既有能力边界。'], cautions: ['离线草稿只在当前设备与账号保存，联网后仍需核对并明确保存。', '自动下载默认关闭，安装前仍需本人确认。'] },
    { version: '0.1.25', date: '2026-10-27', androidCode: 30, highlights: ['更新提醒支持稍后处理和忽略此版本，重开后仍保留选择。', '启动、登录和回到前台时有限检查，失败后退避，手动检查不受冷却限制。', '本版亮点首次进入展示一次，可在帮助中回看版本说明。'], cautions: ['Android需联网登录受邀账号后检查更新。', '自动下载默认关闭，安装仍需本人确认；必更不能被忽略。'] },
    { version: '0.1.24', date: '2026-10-26', androidCode: 29, highlights: ['管理员可查看北京时间注册增长和官网自愿参与的PV及按日访客估算。', '运行页提供有新鲜度的资源采样、请求分类及同源表格。', 'AI用量补充上游尝试、修复及等待口径，未知不记作零。'], cautions: ['官网访客分析默认不参与，新增指标告警邮件保持关闭。'] },
    { version: '0.1.23', date: '2026-10-25', androidCode: 28, highlights: ['独立管理中心与个人工作区分开。', '邀请支持行内重新发送和防重复操作。', '管理员可预览并确认AI并发、排队和资源参数，保存后热生效。'], cautions: ['管理员不能通过管理中心查看成员私人计划或AI对话。'] },
    { version: '0.1.22', date: '2026-10-24', androidCode: 27, highlights: ['长规划可先确认总纲，再按明确批次细化。', '草稿可准备外部AI协作材料，导回后先预检和展示差异。', '改善长内容滚动和手机输入时的操作可达性。'], cautions: ['外部协作材料可能包含选中的私人内容，外发前需本人核对。'] },
    { version: '0.1.21', date: '2026-10-24', androidCode: 26, highlights: ['区分需求讨论、生成和修订，保留需求笔记。', '已知格式失败可在预算内最多修复一次。', '完善失败分类、用量与原草稿保护。'], cautions: ['超时或结果未知不自动重复调用AI。'] },
];
export function compareVersions(a: string, b: string): number { const aa = a.split('.').map(Number), bb = b.split('.').map(Number); for (let i = 0; i < 3; i++) {
    if (aa[i] !== bb[i])
        return aa[i] - bb[i];
} return 0; }
export function installedRelease(version: string, info?: DesktopInfo | null): ReleaseNote | null {
    const release = releaseHistory.find(r => r.version === version);
    return release && (info?.versionCode === undefined || release.androidCode === info.versionCode) ? release : null;
}
export function visibleHistory(version: string, info?: DesktopInfo | null): ReleaseNote[] {
    if (!/^\d+\.\d+\.\d+$/.test(version))
        return [];
    return releaseHistory.filter(r => compareVersions(r.version, version) <= 0 && (r.version !== version || !info?.versionCode || r.androidCode === info.versionCode));
}
export function targetHighlights(update: DesktopUpdate): string[] {
    const release = targetRelease(update);
    if (release)
        return release.highlights;
    return typeof update.notes === 'string' ? update.notes.slice(0, 4000).split(/\r?\n/).map(s => s.trim().replace(/^[-*]\s+/, '')).filter(Boolean).slice(0, 5).map(s => s.slice(0, 400)) : [];
}
export function targetRelease(update: DesktopUpdate): ReleaseNote | null {
    return installedRelease(update.version, update.versionCode ? { version: update.version, serverUrl: '', updatesEnabled: false, versionCode: update.versionCode } : null);
}
