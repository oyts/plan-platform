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
    args = parser.parse_args()
    device = ui.Device(args.serial)
    device.foreground()
    version, code = ('0.1.8', 13) if args.phase == 'before' else ('0.1.9', 14)
    upgrade.verify_version(device.adb('shell', 'dumpsys', 'package', ui.PACKAGE), version, code)
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
    after = upgrade.sync_state(device)
    if after != counts:
        raise RuntimeError('Read-only navigation changed the queue.')
    device.adb('shell', 'am', 'force-stop', ui.PACKAGE)
    device.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
    upgrade.wait(device, lambda nodes: any(n.get('text') == '本地操作' for n in nodes), 'Reopen without login')
    if upgrade.sync_state(device) != counts:
        raise RuntimeError('Queue changed after reopening.')
    result = {'status': 'PASS', 'version': version, 'versionCode': code,
              'scope': 'Formal APK navigation and upgrade queue/session retention only; no business writes',
              'queueBefore': counts, 'queueAfter': after, 'nonemptyQueueProven': counts[0] > 0,
              'courses': 4, 'reopenWithoutLogin': True}
    out = ROOT / 'acceptance-results/formal-android-navigation-019.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
