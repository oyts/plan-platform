"""Read-only AI/upgrade checks in the exact signed formal APK.

No authentication input, storage extraction, paid request, queue replay or
business mutation. Synthetic write acceptance runs in the separate test app.
"""
import argparse
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path

spec = importlib.util.spec_from_file_location('upgrade', Path(__file__).with_name('android-post-upgrade-acceptance.py'))
upgrade = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)
ui = upgrade.ui
ui.LABELS.update({'AI 规划', 'AI 规划助手', '目标路线'})


def check_update(device):
    upgrade.menu(device, '客户端')
    upgrade.wait(device, lambda n: any(v.get('text') == '检查新版本' for v in n), 'Client settings')
    upgrade.reveal(device, '检查新版本', 'down')
    device.click('检查新版本')
    nodes = upgrade.wait(device, lambda n: any(v.get('text') == '当前已是最新版本。' for v in n), 'Protected update source')
    boxes = [v for v in nodes if v.get('class') == 'android.widget.CheckBox']
    if not boxes or any(v.get('checked') == 'true' for v in boxes):
        raise RuntimeError('Automatic download setting was not verified as off; not changed.')


def finish(result):
    result.update(status='PASS', paidCalls=0, finishedAt=datetime.now(timezone.utc).isoformat())
    directory = Path(__file__).resolve().parents[1] / 'acceptance-results' / 'android-ai-formal' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    directory.mkdir(parents=True, exist_ok=False)
    (directory / 'report.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print('Evidence:', directory)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-version', required=True)
    parser.add_argument('--expected-code', required=True, type=int)
    parser.add_argument('--check-update', action='store_true')
    parser.add_argument('--update-only', action='store_true', help='Only the post-publication update check; no repeated workspace tests')
    args = parser.parse_args()
    device = ui.Device(args.serial)
    device.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
    upgrade.wait(device, lambda n: any(v.get('text') == '工作模式' for v in n), 'Preserved login')
    upgrade.verify_version(device.adb('shell', 'dumpsys', 'package', ui.PACKAGE), args.expected_version, args.expected_code)
    if args.update_only:
        check_update(device)
        finish({'version': args.expected_version, 'versionCode': args.expected_code,
                'scope': 'Exact formal APK, post-publication authenticated update check only',
                'cases': ['Authenticated update check succeeds; automatic download remains off']})
        return
    before = upgrade.sync_state(device)
    result = {'version': args.expected_version, 'versionCode': args.expected_code,
              'scope': 'Exact signed formal APK, read-only original session; no paid calls or business writes',
              'queueBefore': before, 'cases': [], 'status': 'RUNNING'}
    result['cases'].append('Original login restored after signed same-package cover upgrade')
    for mode, link, marker in [('周期计划', '计划', '我的计划'), ('目标路线', '路线', '新建路线'), ('今日待办', '待办', '添加待办')]:
        device.click('工作模式')
        upgrade.wait(device, lambda n: any(v.get('text') == mode for v in n), 'Workspace choice')
        device.click(mode)
        upgrade.wait(device, lambda n: any(v.get('content-desc') == link and v.get('clickable') == 'true' for v in n), 'Workspace navigation')
        device.click_navigation(link)
        upgrade.wait(device, lambda n: any(v.get('text') == marker for v in n), 'Loaded ' + mode)
        result['cases'].append(mode + ' loads without connection errors')
    upgrade.menu(device, 'AI 规划')
    nodes = upgrade.wait(device, lambda n: any(v.get('text') == 'AI 规划助手' for v in n), 'Formal AI entry')
    if not any('我同意将本次对话和草稿' in v.get('text', '') for v in nodes):
        raise RuntimeError('AI privacy consent is missing; no request made.')
    consent = [v for v in nodes if v.get('class') == 'android.widget.CheckBox']
    if consent and any(v.get('checked') == 'true' for v in consent):
        raise RuntimeError('Fresh AI consent was unexpectedly checked.')
    result['cases'].append('Formal AI entry and explicit privacy consent visible')
    after = upgrade.sync_state(device)
    if after != before:
        raise RuntimeError('Read-only navigation changed the existing queue; nothing replayed.')
    device.foreground()
    device.adb('shell', 'am', 'force-stop', ui.PACKAGE)
    device.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
    upgrade.wait(device, lambda n: any(v.get('text') == '工作模式' for v in n), 'Login preserved after process restart')
    if upgrade.sync_state(device) != before:
        raise RuntimeError('Queue differs after restart; left untouched.')
    result['cases'].append('Process restart retains login and queue')
    if args.check_update:
        check_update(device)
        result['cases'].append('Authenticated update check succeeds; automatic download remains off')
    result['queueAfter'] = before
    finish(result)


if __name__ == '__main__':
    main()
