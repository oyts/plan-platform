"""Focused formal 0.1.16 UI checks; no auth input, mail, governance or queue writes."""
import argparse, importlib.util, json, re
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ops', Path(__file__).with_name('android-account-operations-acceptance.py'))
ops = importlib.util.module_from_spec(spec); spec.loader.exec_module(ops)
u, ui = ops.upgrade, ops.ui
ui.LABELS.update({'更多功能','关闭面板','运行与治理', '运行状态', '成员状态', '重新读取状态', '重新读取验证状态',
                  '邮箱验证', '成员治理开关已启用。', '帮助与版本', '检查服务器版本'})
FOLDER = ROOT / 'acceptance-results/native-mail-operations-0116'
SHA = 'beccfba5cf8c0722400fa6cd1e61323e220dfbe3b2e5c93e5979202c69197afc'


class AccountReadOnlyDevice(ui.Device):
    def snapshot(self):
        return super().snapshot(allow_blank_account_settings=True)


def reveal_menu(device, label):
    for _ in range(12):
        nodes = device.snapshot(); u.no_errors(nodes)
        if not ops.present(nodes,'更多功能') or not ops.present(nodes,'关闭面板'):
            raise RuntimeError('Observed grouped-navigation sheet required.')
        views = [n for n in nodes if n.get('class')=='android.webkit.WebView' and n.get('text')=='计划舱']
        if len(views)!=1: raise RuntimeError('Expected application WebView.')
        edge = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',views[0].get('bounds',''))
        controls = [n for n in nodes if label in (n.get('text'),n.get('content-desc')) and n.get('clickable')=='true']
        if len(controls)==1 and edge:
            bounds=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',controls[0].get('bounds',''))
            if bounds and 350<=int(bounds[2])<int(bounds[4])<int(edge[4])-30: return
        device.scroll('down')
    raise RuntimeError('Navigation control remains clipped; no click attempted.')


def go(device, label):
    nodes=device.snapshot()
    if ops.present(nodes,'更多功能') and ops.present(nodes,'关闭面板'): device.click('关闭面板')
    device.click('更多')
    u.wait(device, lambda n: ops.present(n, label), 'Observed grouped navigation')
    reveal_menu(device, label)
    device.click(label)


def sync(device):
    go(device,'同步')
    return u.baseline.queue_counts(u.wait(device,lambda n:ops.present(n,'本地操作'),'Queue summary'))


def main():
    p = argparse.ArgumentParser(); p.add_argument('--serial', required=True)
    p.add_argument('--phase', required=True, choices=['before', 'after', 'update-check'])
    args = p.parse_args(); d = AccountReadOnlyDevice(args.serial)
    u.verify_version(d.adb('shell', 'dumpsys', 'package', ui.PACKAGE),
                     '0.1.15' if args.phase == 'before' else '0.1.16', 20 if args.phase == 'before' else 21)
    d.foreground()
    u.wait(d, lambda n: ops.present(n, '工作模式') and ops.present(n, '更多'), 'Existing authenticated workspace')
    FOLDER.mkdir(parents=True, exist_ok=True)
    counts = sync(d)
    result = {'version': '0.1.15' if args.phase == 'before' else '0.1.16', 'status': 'FAIL',
              'businessWrites': 0, 'realEmailsSent': 0, 'paidAiCalls': 0, 'queueSubmitted': False, 'cases': []}
    try:
        if args.phase == 'before':
            result.update(status='BASELINE_RECORDED', queue=counts, authenticatedWorkspaceVisible=True)
            with (FOLDER / 'android-before.json').open('x', encoding='utf-8') as f:
                json.dump(result, f, indent=2)
            print(json.dumps(result)); return
        before = json.loads((FOLDER / 'android-before.json').read_text(encoding='utf-8'))
        if counts != before['queue']: raise RuntimeError('Queue differs from baseline; untouched.')
        paths = d.adb('shell', 'pm', 'path', ui.PACKAGE).strip().splitlines()
        if len(paths) != 1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk', paths[0]):
            raise RuntimeError('Expected one installed formal APK.')
        if d.adb('shell', 'sha256sum', paths[0][8:]).split()[0].lower() != SHA:
            raise RuntimeError('Installed APK differs from accepted candidate.')
        result['installedApkSha256'] = SHA
        result['cases'].append('Original authenticated workspace and exact baseline queue retained')
        if args.phase == 'update-check':
            go(d, '客户端'); ops.reveal(d, '检查新版本'); d.click('检查新版本')
            u.wait(d, lambda n: ops.present(n, '当前已是最新版本。'), 'Protected update source')
            nodes = ops.reveal(d, '自动下载更新（安装前仍需确认）')
            box = [n for n in nodes if n.get('text') == '自动下载更新（安装前仍需确认）']
            if len(box) != 1 or box[0].get('checked') != 'false': raise RuntimeError('Automatic-download preference differs; unchanged.')
            result['cases'].append('Published protected update source and unchecked automatic download')
        else:
            go(d, '设置'); nodes = ops.reveal(d, '重新读取验证状态')
            d.click('重新读取验证状态')
            nodes = ops.reveal(d, '邮箱验证')
            if not any(n.get('text') in {'尚未验证', '已验证'} for n in nodes):
                nodes = ops.reveal(d, '重新读取验证状态')
            text = '\n'.join(n.get('text', '') for n in nodes)
            if not any(s in text for s in ['尚未验证', '已验证']) or '服务器暂未启用邮箱验证' in text:
                raise RuntimeError('Enabled account verification state unavailable.')
            result['cases'].append('Native account verification status loads without sending mail')
            go(d, '运行与治理')
            nodes = u.wait(d, lambda n: any('运维采集已启用' in v.get('text', '') for v in n), 'Enabled operations status')
            if not any('状态证据可读取' in n.get('text', '') for n in nodes): raise RuntimeError('Operations evidence unreadable.')
            if not any('邮件告警未启用或配置不可用' in n.get('text', '') for n in nodes): raise RuntimeError('Alerts unexpectedly enabled.')
            result['cases'].append('Native operations evidence readable; alert mail remains disabled')
            ops.reveal(d, '成员状态'); d.click('成员状态')
            u.wait(d, lambda n: ops.present(n, '成员治理开关已启用。'), 'Approved governance flag')
            result['cases'].append('Approved member policy visible; no member changed')
            if sync(d) != counts: raise RuntimeError('Queue changed; no sync attempted.')
            d.foreground(); d.adb('shell', 'am', 'force-stop', ui.PACKAGE); d.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
            u.wait(d, lambda n: ops.present(n, '工作模式') and ops.present(n, '更多'), 'Session after restart')
            if sync(d) != counts: raise RuntimeError('Queue changed on restart; untouched.')
            result['cases'].append('Cold reopen retains original login and queue')
        result.update(status='PASS', queueBefore=counts, queueAfter=counts, nonemptyQueueUpgradeProven=counts[0] > 0)
    finally:
        result['at'] = datetime.now(timezone.utc).isoformat()
        name = args.phase + '-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.json'
        with (FOLDER / name).open('x', encoding='utf-8') as f: json.dump(result, f, indent=2)
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
