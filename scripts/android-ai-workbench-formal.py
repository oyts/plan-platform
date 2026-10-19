"""Formal 0.1.16 -> 0.1.17 readonly acceptance; never auth or business input."""
import argparse
import importlib.util
import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('formal', Path(__file__).with_name('android-mail-operations-formal.py'))
formal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(formal)
ops, u, ui = formal.ops, formal.u, formal.ui
ui.LABELS.update({'AI 规划', 'AI 规划助手', '对话', '草稿'})
FOLDER = ROOT / 'acceptance-results/native-ai-workbench-0117'
SHA = '9330bf71ae6d821a8cf3f304c91308772a7483d264599e1be0e8dc603c7ed7c2'


class StableDevice(ui.Device):
    def snapshot(self):
        try:
            return super().snapshot()
        except RuntimeError as error:
            # OEM uiautomator occasionally fails during a page transition.
            # Retry only that command failure once; auth/focus/privacy failures
            # still stop, and the parent rechecks the foreground on every call.
            if str(error) != 'Android command failed; private output withheld.':
                raise
            time.sleep(1)
            return super().snapshot()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--phase', required=True, choices=['before', 'after', 'update-check'])
    args = parser.parse_args()
    d = StableDevice(args.serial)
    before_phase = args.phase == 'before'
    version, code = ('0.1.16', 21) if before_phase else ('0.1.17', 22)
    u.verify_version(d.adb('shell', 'dumpsys', 'package', ui.PACKAGE), version, code)
    d.foreground()
    u.wait(d, lambda n: ops.present(n, '工作模式') and ops.present(n, '更多'), 'Original authenticated workspace')
    FOLDER.mkdir(parents=True, exist_ok=True)
    counts = formal.sync(d)
    result = dict(status='FAIL', version=version, versionCode=code, cases=[], businessWrites=0,
                  paidAiCalls=0, realEmailsSent=0, queueSubmitted=False)
    try:
        if before_phase:
            result.update(status='BASELINE_RECORDED', queue=counts, authenticatedWorkspaceVisible=True)
            with (FOLDER / 'android-before.json').open('x', encoding='utf-8') as stream:
                json.dump(result, stream, indent=2)
            print(json.dumps(result)); return
        baseline = json.loads((FOLDER / 'android-before.json').read_text(encoding='utf-8'))
        if counts != baseline['queue']:
            raise RuntimeError('Queue differs; no sync attempted.')
        paths = d.adb('shell', 'pm', 'path', ui.PACKAGE).strip().splitlines()
        if len(paths) != 1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk', paths[0]):
            raise RuntimeError('Expected one installed base APK.')
        if d.adb('shell', 'sha256sum', paths[0][8:]).split()[0].lower() != SHA:
            raise RuntimeError('Installed candidate hash differs.')
        result['installedApkSha256'] = SHA
        result['cases'].append('Original login and baseline queue retained; exact signed APK installed')
        if args.phase == 'update-check':
            formal.go(d, '客户端'); ops.reveal(d, '检查新版本'); d.click('检查新版本')
            u.wait(d, lambda n: ops.present(n, '当前已是最新版本。'), 'Protected source after publication')
            nodes = ops.reveal(d, '自动下载更新（安装前仍需确认）')
            boxes = [n for n in nodes if n.get('text') == '自动下载更新（安装前仍需确认）']
            if len(boxes) != 1 or boxes[0].get('checked') != 'false':
                raise RuntimeError('Automatic download differs; preference unchanged.')
            result['cases'].append('Protected source latest; automatic download remains unchecked')
        else:
            formal.go(d, 'AI 规划')
            u.wait(d, lambda n: ops.present(n, 'AI 规划助手'), 'Shipped AI workspace')
            nodes = u.wait(d, lambda n: any('规划草稿箱' in x.get('text', '') for x in n), 'Shipped draft shelf')
            if not any('规划草稿箱' in x.get('text', '') for x in nodes):
                raise RuntimeError('Draft shelf missing.')
            result['cases'].append('Formal new AI workspace and private draft shelf visible')
            ops.reveal(d, '对话'); d.click('对话')
            nodes = ops.reveal(d, '你的目标或修改要求')
            u.no_errors(nodes)
            ops.reveal(d, '草稿'); d.click('草稿')
            u.wait(d, lambda n: ops.present(n, '02 草稿预览'), 'Mobile draft panel')
            result['cases'].append('Formal chat/draft navigation works without editing or sending')
            if formal.sync(d) != counts:
                raise RuntimeError('Queue changed; untouched.')
            d.foreground(); d.adb('shell', 'am', 'force-stop', ui.PACKAGE)
            d.adb('shell', 'am', 'start', '-n', ui.ACTIVITY)
            u.wait(d, lambda n: ops.present(n, '工作模式') and ops.present(n, '更多'), 'Original session after reopen')
            if formal.sync(d) != counts:
                raise RuntimeError('Queue changed on reopen; untouched.')
            result['cases'].append('Cold reopen retains original login and queue')
        result.update(status='PASS', queueBefore=counts, queueAfter=counts,
                      nonemptyQueueUpgradeProven=counts[0] > 0)
    finally:
        result['at'] = datetime.now(timezone.utc).isoformat()
        path = FOLDER / (args.phase + '-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.json')
        with path.open('x', encoding='utf-8') as stream:
            json.dump(result, stream, indent=2)
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
