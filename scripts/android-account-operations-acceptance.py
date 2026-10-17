"""0.1.13 exact signed APK: affected UI and upgrade retention only.

Never enters authentication, reads session storage, sends mail, submits AI,
changes invitations/quotas, replays queues, or modifies real business records.
"""
import argparse
import importlib.util
import json
import re
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('upgrade', Path(__file__).with_name('android-post-upgrade-acceptance.py'))
upgrade = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)
ui = upgrade.ui
ui.LABELS.update({'成员', 'AI 规划', '刷新邮件状态', '刷新用量', '不确定下一步怎么做？'})
BASELINE = ROOT / '.desktop-tools/formal-android/operations-0113-before.json'
APK_SHA256 = 'a5fbf315047f5e5dfbd1c82a839f9de60c73cfec4fa61198b94b0b9178614ded'


def present(nodes, text):
    return any(text in (n.get('text'), n.get('content-desc')) for n in nodes)


def reveal(device, text, limit=28):
    for _ in range(limit):
        nodes = device.snapshot()
        upgrade.no_errors(nodes)
        dock = [n for n in nodes if n.get('text') == '更多' and n.get('clickable') == 'true']
        if len(dock) != 1:
            raise RuntimeError('Expected the observed application navigation dock.')
        dock_y = int(re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', dock[0].get('bounds'))[2])
        for n in nodes:
            if text not in (n.get('text'), n.get('content-desc')):
                continue
            bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds', ''))
            if bounds and 350 <= int(bounds[2]) < int(bounds[4]) < dock_y:
                return nodes
        device.scroll('down')
    raise RuntimeError('Required affected control was not visibly available: ' + text)


def field(nodes, label, expected):
    labels = [i for i, n in enumerate(nodes) if n.get('text') == label]
    if len(labels) != 1 or labels[0] + 1 >= len(nodes) or nodes[labels[0] + 1].get('text') != expected:
        raise RuntimeError('Mail field did not match expected production state: ' + label)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--phase', required=True, choices=['before', 'after'])
    args = parser.parse_args()
    device = ui.Device(args.serial)
    metadata = device.adb('shell', 'dumpsys', 'package', ui.PACKAGE)
    upgrade.verify_version(metadata, '0.1.12' if args.phase == 'before' else '0.1.13', 17 if args.phase == 'before' else 18)
    upgrade.wait(device, lambda n: present(n, '工作模式'), 'Original authenticated workspace')
    counts = upgrade.sync_state(device)
    if args.phase == 'before':
        if BASELINE.exists():
            raise RuntimeError('Existing upgrade baseline retained; inspect it before reuse.')
        BASELINE.parent.mkdir(parents=True, exist_ok=True)
        result = {'version': '0.1.12', 'versionCode': 17, 'queue': counts,
                  'authenticatedWorkspaceVisible': True, 'recordedAt': datetime.now(timezone.utc).isoformat()}
        BASELINE.write_text(json.dumps(result, indent=2), encoding='utf-8')
        print(json.dumps({'status': 'BASELINE_RECORDED', **result}, indent=2))
        return
    before = json.loads(BASELINE.read_text(encoding='utf-8'))
    if counts != before['queue']:
        raise RuntimeError('Queue differs from the pre-install baseline; no sync attempted.')
    paths = device.adb('shell', 'pm', 'path', ui.PACKAGE).strip().splitlines()
    if len(paths) != 1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk', paths[0]):
        raise RuntimeError('Expected one formal installed base APK.')
    installed_hash = device.adb('shell', 'sha256sum', paths[0][8:]).split()[0].lower()
    if installed_hash != APK_SHA256:
        raise RuntimeError('Installed APK differs from the approved signed candidate.')
    result = {'version': '0.1.13', 'versionCode': 18, 'status': 'RUNNING',
              'scope': 'Exact formal APK: changed admin mail/usage, AI guidance and upgrade retention only',
              'installedApkSha256': installed_hash, 'queueBefore': counts, 'paidCalls': 0,
              'emailsSent': 0, 'businessWrites': 0, 'cases': []}
    result['cases'].append('Original authenticated workspace and queue retained across same-package upgrade')
    upgrade.menu(device, '成员')
    upgrade.wait(device, lambda n: present(n, '成员邀请'), 'Administrator member page')
    reveal(device, '刷新邮件状态')
    device.click('刷新邮件状态')
    nodes = reveal(device, '密码找回')
    field(nodes, '密码找回', '已启用')
    # On a narrow screen each field may need its own reveal, not a desktop layout assumption.
    if not any('已配置，需确认实际收信' == n.get('text') for n in nodes):
        raise RuntimeError('SMTP configured state missing; no test mail sent.')
    nodes = reveal(device, '邮箱验证')
    field(nodes, '邮箱验证', '未启用')
    result['cases'].append('Mobile administrator mail panel shows configured SMTP and enabled recovery; no email sent')
    reveal(device, '刷新用量')
    device.click('刷新用量')
    nodes = reveal(device, '接收请求')
    if not present(nodes, '成功回复'):
        nodes = reveal(device, '成功回复')
    for _ in range(10):
        nodes = device.snapshot()
        upgrade.no_errors(nodes)
        if any(n.get('text', '').startswith('当前服务已启用 · 排队 ') for n in nodes):
            break
        device.scroll('down')
    else:
        raise RuntimeError('AI runtime summary was not loaded on the real mobile client.')
    result['cases'].append('Mobile private AI usage and runtime metadata load without connection errors')
    upgrade.menu(device, 'AI 规划')
    upgrade.wait(device, lambda n: present(n, 'AI 规划助手'), 'AI helper')
    reveal(device, '不确定下一步怎么做？')
    device.click('不确定下一步怎么做？')
    nodes = reveal(device, '进入 AI 四步教学')
    if not any('不要发送密码、完整邀请链接或私人对话' in n.get('text', '') for n in nodes):
        # Guidance can wrap beyond the first screen on this device.
        device.scroll('down')
        nodes = device.snapshot()
    if not any('不要发送密码、完整邀请链接或私人对话' in n.get('text', '') for n in nodes):
        raise RuntimeError('New safe feedback guidance was not visible.')
    result['cases'].append('New AI next-step teaching and safe feedback guidance expand in the signed APK')
    counts_after = upgrade.sync_state(device)
    if counts_after != counts:
        raise RuntimeError('Read-only acceptance changed queue; left untouched.')
    device.foreground()
    device.adb('shell', 'am', 'force-stop', ui.PACKAGE)
    device.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
    upgrade.wait(device, lambda n: present(n, '工作模式'), 'Login retained after process restart')
    if upgrade.sync_state(device) != counts:
        raise RuntimeError('Queue changed across process restart; left untouched.')
    result.update(status='PASS', queueAfter=counts_after, nonemptyQueueProven=counts[0] > 0,
                  finishedAt=datetime.now(timezone.utc).isoformat())
    result['cases'].append('Cold process reopen retains login and exact queue counts')
    directory = ROOT / 'acceptance-results/android-account-operations' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    directory.mkdir(parents=True, exist_ok=False)
    (directory / 'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print('Evidence:', directory)


if __name__ == '__main__':
    main()
