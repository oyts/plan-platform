"""Affected formal APK navigation checks only; no business edits/authentication."""
import argparse
import importlib.util
import json
import re
from pathlib import Path

spec = importlib.util.spec_from_file_location('upgrade', Path(__file__).with_name('android-post-upgrade-acceptance.py'))
upgrade = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)
ui = upgrade.ui
ui.LABELS.update({'使用教学', '全部课程', '离线与同步'})
ROOT = Path(__file__).resolve().parents[1]

def near_top(nodes, label):
    headers = [n for n in nodes if n.get('text') == label and n.get('class') == 'android.widget.TextView']
    for node in headers:
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if match and 200 <= int(match[2]) < 700 and int(match[4]) > int(match[2]):
            return True
    return False

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--phase', choices=['before', 'after'], required=True)
    parser.add_argument('--reopen-only', action='store_true', help='Only rerun the failed reopening gate, not completed navigation cases.')
    args = parser.parse_args()
    device = ui.Device(args.serial)
    device.foreground()
    version, code = ('0.1.8', 13) if args.phase == 'before' else ('0.1.9', 14)
    upgrade.verify_version(device.adb('shell', 'dumpsys', 'package', ui.PACKAGE), version, code)
    installed_hash = None
    if args.phase == 'after':
        paths = device.adb('shell', 'pm', 'path', ui.PACKAGE).strip().splitlines()
        if len(paths) != 1 or not paths[0].startswith('package:'):
            raise RuntimeError('Expected one installed formal base APK.')
        apk = paths[0][len('package:'):]
        if not re.fullmatch(r'/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk', apk):
            raise RuntimeError('Unexpected installed package path.')
        installed_hash = device.adb('shell', 'sha256sum', apk).split()[0].lower()
        candidate = json.loads((ROOT / 'production-artifacts/client-release/0.1.9/20261015T080518Z-a6b7a60d/metadata/candidate.json').read_text(encoding='utf-8'))
        if installed_hash != candidate['android']['sha256']:
            raise RuntimeError('Installed APK differs from the verified signed candidate.')
    upgrade.wait(device, lambda nodes: any(n.get('text') == '更多' for n in nodes), 'Application navigation ready')
    baseline = ROOT / '.desktop-tools/formal-android/navigation-019-before.json'
    counts = upgrade.sync_state(device)
    if args.phase == 'before':
        if baseline.exists():
            raise RuntimeError('Existing baseline retained; inspect before reusing.')
        baseline.parent.mkdir(parents=True, exist_ok=True)
        baseline.write_text(json.dumps({'version': version, 'versionCode': code, 'queue': counts}), encoding='utf-8')
        print(json.dumps({'status': 'BASELINE_RECORDED', 'version': version, 'queue': counts}))
        return
    prior = json.loads(baseline.read_text(encoding='utf-8'))
    if counts != prior['queue']:
        raise RuntimeError('Queue differs after upgrade; no sync or edits attempted.')
    if not args.reopen_only:
        upgrade.menu(device, '客户端')
        upgrade.wait(device, lambda nodes: any(n.get('text') == '手机客户端' for n in nodes), 'Client page loaded')
        for _ in range(4):
            device.scroll('down')
        nodes = device.snapshot()
        if near_top(nodes, '手机客户端'):
            raise RuntimeError('Source page did not scroll, so reset-to-top was not exercised.')
        upgrade.menu(device, '使用教学')
        upgrade.wait(device, lambda nodes: near_top(nodes, '使用教学'), 'New teaching page must start at top without reveal scrolling')
        for title in ['周期计划', '目标路线', '今日待办', '离线与同步']:
            device.click(title)
            upgrade.wait(device, lambda nodes: near_top(nodes, '使用教学') and any(n.get('text') == '独立练习区' for n in nodes), 'Course top and independent practice notice')
            device.scroll('down')
            # Return through observed bottom navigation, not a hidden element tap.
            upgrade.menu(device, '使用教学')
            upgrade.wait(device, lambda nodes: near_top(nodes, '使用教学'), 'Return to course list at top')
            print('PASS formal navigation: ' + title, flush=True)
    after = upgrade.sync_state(device)
    if after != counts:
        raise RuntimeError('Read-only navigation changed the queue.')
    device.adb('shell', 'am', 'force-stop', ui.PACKAGE)
    device.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
    # Native session restoration returns to the remembered workspace, not /sync.
    upgrade.wait(device, lambda nodes: any(n.get('text') == '更多' for n in nodes), 'Reopen without login')
    if upgrade.sync_state(device) != counts:
        raise RuntimeError('Queue changed after reopening.')
    result = {'status': 'PASS', 'version': version, 'versionCode': code,
              'scope': 'Formal APK navigation and upgrade queue/session retention only; no business writes',
              'queueBefore': counts, 'queueAfter': after, 'nonemptyQueueProven': counts[0] > 0,
              'courses': 0 if args.reopen_only else 4, 'reopenOnly': args.reopen_only,
              'reopenWithoutLogin': True, 'installedApkSha256': installed_hash}
    out = ROOT / ('acceptance-results/formal-android-reopen-019.json' if args.reopen_only else 'acceptance-results/formal-android-navigation-019.json')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
