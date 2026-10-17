"""Exact formal 0.1.14 APK: read-only daily pages and upgrade retention.

No authentication automation, reminders changes, review writes or queue replay.
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
ui.LABELS.update({'今日与提醒', '周复盘', '今日待完成', '已逾期', '即将到期', '刷新事项',
                  '站内提醒设置', '启用站内提醒', '保存提醒设置', '读取云端设置',
                  '本周概况', '保存本周复盘', '本周收获', '遗留与阻塞', '下周重点', '读取云端版本'})
BASELINE = ROOT / '.desktop-tools/formal-android/daily-0114-before.json'
SHA = '6652007bedf481170ad1f57c2a2ed3ed0f09a887e0d6373c4f61662330f2d215'


def present(nodes, text):
    return any(text in (n.get('text'), n.get('content-desc')) for n in nodes)


def matches(node, text):
    return text in (node.get('text'), node.get('content-desc')) or (
        text in {'今日待完成', '已逾期', '即将到期'} and
        re.fullmatch(re.escape(text) + r'\s+\d+', node.get('text', '')) is not None)


def reveal(device, label, limit=24):
    for _ in range(limit):
        nodes = device.snapshot()
        upgrade.no_errors(nodes)
        if any(n.get('text', '').startswith(('Error:', '请求未完成')) for n in nodes):
            raise RuntimeError('API error visible; no business writes performed.')
        dock = [n for n in nodes if n.get('text') == '更多' and n.get('clickable') == 'true']
        if len(dock) != 1:
            raise RuntimeError('Expected application navigation dock.')
        dock_top = int(re.findall(r'\d+', dock[0].get('bounds', ''))[1])
        if any(matches(n, label) for n in nodes):
            controls = [n for n in nodes if matches(n, label)]
            if any(re.fullmatch(r'\[\d+,\d+\]\[\d+,\d+\]', n.get('bounds', '')) and
                   350 <= int(re.findall(r'\d+', n.get('bounds'))[1]) <
                   int(re.findall(r'\d+', n.get('bounds'))[3]) < dock_top
                   for n in controls):
                return nodes
        device.scroll('down')
    raise RuntimeError('Required daily experience control not visible: ' + label)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--phase', choices=['before', 'after', 'update-check'], required=True)
    args = parser.parse_args()
    device = ui.Device(args.serial)
    device.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
    upgrade.wait(device, lambda n: present(n, '工作模式'), 'Original authenticated workspace')
    metadata = device.adb('shell', 'dumpsys', 'package', ui.PACKAGE)
    if args.phase == 'update-check':
        upgrade.verify_version(metadata, '0.1.14', 19)
        upgrade.menu(device, '客户端')
        upgrade.wait(device, lambda n: present(n, '检查新版本'), 'Native update panel')
        reveal(device, '检查新版本')
        device.click('检查新版本')
        nodes = upgrade.wait(device, lambda n: present(n, '当前已是最新版本。'), 'Published protected update manifest')
        checkboxes = [n for n in nodes if n.get('text') == '自动下载更新（安装前仍需确认）']
        if len(checkboxes) != 1:
            nodes = reveal(device, '自动下载更新（安装前仍需确认）')
            checkboxes = [n for n in nodes if n.get('text') == '自动下载更新（安装前仍需确认）']
        if len(checkboxes) != 1:
            raise RuntimeError('Automatic download preference not observed; not modified.')
        result = {'status': 'PASS', 'version': '0.1.14', 'versionCode': 19,
                  'publishedUpdateCheck': 'latest', 'automaticDownloadChecked': checkboxes[0].get('checked') == 'true',
                  'preferenceModified': False, 'scope': 'Exact formal APK reads published protected update feed; no cross-version download/install claim'}
        directory = ROOT / 'acceptance-results/android-daily' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        directory.mkdir(parents=True, exist_ok=False)
        (directory / 'update-check.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
        print(json.dumps(result, indent=2))
        print('Evidence:', directory)
        return
    counts = upgrade.sync_state(device)
    if args.phase == 'before':
        upgrade.verify_version(metadata, '0.1.13', 18)
        if BASELINE.exists():
            raise RuntimeError('Prior baseline exists; inspect before reusing.')
        result = {'version': '0.1.13', 'versionCode': 18, 'queue': counts,
                  'authenticatedWorkspaceVisible': True, 'recordedAt': datetime.now(timezone.utc).isoformat()}
        BASELINE.parent.mkdir(parents=True, exist_ok=True)
        BASELINE.write_text(json.dumps(result, indent=2), encoding='utf-8')
        print(json.dumps({'status': 'BASELINE_RECORDED', **result}, indent=2))
        return
    upgrade.verify_version(metadata, '0.1.14', 19)
    before = json.loads(BASELINE.read_text(encoding='utf-8'))
    if counts != before['queue']:
        raise RuntimeError('Queue differs from pre-install baseline; not submitted.')
    paths = device.adb('shell', 'pm', 'path', ui.PACKAGE).strip().splitlines()
    if len(paths) != 1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk', paths[0]):
        raise RuntimeError('Expected one installed formal APK.')
    actual = device.adb('shell', 'sha256sum', paths[0][8:]).split()[0].lower()
    if actual != SHA:
        raise RuntimeError('Installed APK differs from the signed candidate.')
    result = {'status': 'RUNNING', 'version': '0.1.14', 'versionCode': 19, 'installedApkSha256': actual,
              'queueBefore': counts, 'businessWrites': 0, 'paidCalls': 0, 'emailsSent': 0, 'cases': []}
    upgrade.menu(device, '今日与提醒')
    upgrade.wait(device, lambda n: present(n, '刷新事项'), 'Daily page from cloud')
    for label in ['已逾期', '即将到期', '今日待完成']:
        nodes = reveal(device, label)
        candidates = [n for n in nodes if matches(n, label) and n.get('clickable') == 'true']
        if len(candidates) != 1:
            raise RuntimeError('Expected one visible category button.')
        actual_label = candidates[0].get('text', '')
        ui.LABELS.add(actual_label)
        device.click(actual_label)
        upgrade.wait(device, lambda n: not present(n, '正在读取事项…'), 'Daily category loaded')
    result['cases'].append('Three dated daily categories load in the exact signed native WebView')
    reveal(device, '站内提醒设置')
    nodes = reveal(device, '保存提醒设置')
    if not present(nodes, '读取云端设置'):
        nodes = reveal(device, '读取云端设置')
    result['cases'].append('Cloud reminder preferences load; no settings changed or saved')
    upgrade.menu(device, '周复盘')
    upgrade.wait(device, lambda n: present(n, '周期计划本周完成'), 'Cloud weekly metrics')
    for label in ['本周收获', '遗留与阻塞', '下周重点', '保存本周复盘']:
        reveal(device, label)
    result['cases'].append('Weekly metrics and private review editor load; existing content left unchanged')
    after = upgrade.sync_state(device)
    if after != counts:
        raise RuntimeError('Read-only navigation changed queue; left untouched.')
    device.foreground()
    device.adb('shell', 'am', 'force-stop', ui.PACKAGE)
    device.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
    upgrade.wait(device, lambda n: present(n, '工作模式'), 'Original login after process reopen')
    if upgrade.sync_state(device) != counts:
        raise RuntimeError('Queue changed across reopen; not submitted.')
    result['cases'].append('Original authenticated workspace and exact queue survive upgrade and process reopen')
    result.update(status='PASS', queueAfter=after, nonemptyQueueProven=counts[0] > 0,
                  finishedAt=datetime.now(timezone.utc).isoformat())
    directory = ROOT / 'acceptance-results/android-daily' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    directory.mkdir(parents=True, exist_ok=False)
    (directory / 'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print('Evidence:', directory)


if __name__ == '__main__':
    main()
