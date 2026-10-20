"""Formal 0.1.17 -> 0.1.18 readonly checks; no auth, queue or business writes."""
import argparse
import importlib.util
import json
import re
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('previous',Path(__file__).with_name('android-ai-workbench-formal.py'))
previous=importlib.util.module_from_spec(spec);spec.loader.exec_module(previous)
formal,ops,u,ui=previous.formal,previous.ops,previous.u,previous.ui
ui.LABELS.update({'日周复盘','系统提醒','系统通知与事项提醒','查看所选范围','保存本机草稿',
                 '保存云端复盘','重新读取状态','开启本设备提醒','关闭本设备提醒','系统提醒未开启'})
FOLDER=ROOT/'acceptance-results/native-reflection-0118'
SHA='82adbb9dfc58caeeccbca3a45e07f2b69dee2a4bea91a278141db4304e526699'


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True)
    parser.add_argument('--phase',required=True,choices=['before','after','update-check'])
    args=parser.parse_args(); d=previous.StableDevice(args.serial)
    before=args.phase=='before';version,code=('0.1.17',22) if before else ('0.1.18',23)
    u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code)
    d.foreground();u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original signed-in workspace')
    FOLDER.mkdir(parents=True,exist_ok=True);counts=formal.sync(d)
    result=dict(status='FAIL',version=version,versionCode=code,cases=[],businessWrites=0,paidAiCalls=0,
                realEmailsSent=0,queueSubmitted=False)
    try:
        if before:
            result.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
            print(json.dumps(result));return
        baseline=json.loads((FOLDER/'android-before.json').read_text(encoding='utf-8'))
        if counts!=baseline['queue']:raise RuntimeError('Queue differs; left untouched.')
        paths=d.adb('shell','pm','path',ui.PACKAGE).strip().splitlines()
        if len(paths)!=1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',paths[0]):
            raise RuntimeError('Expected one installed APK.')
        if d.adb('shell','sha256sum',paths[0][8:]).split()[0].lower()!=SHA:raise RuntimeError('Installed APK hash differs.')
        result['installedApkSha256']=SHA
        result['cases'].append('Exact signed APK; original login and baseline queue retained')
        if args.phase=='update-check':
            formal.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本')
            u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Authenticated release source')
            nodes=ops.reveal(d,'自动下载更新（安装前仍需确认）')
            boxes=[n for n in nodes if n.get('text')=='自动下载更新（安装前仍需确认）']
            if len(boxes)!=1 or boxes[0].get('checked')!='false':raise RuntimeError('Auto download differs; not changed.')
            result['cases'].append('Protected source latest; auto download remains off')
        else:
            formal.go(d,'日周复盘')
            u.wait(d,lambda n:ops.present(n,'日周复盘'),'New reflection entry')
            nodes=ops.reveal(d,'查看所选范围');u.no_errors(nodes)
            ops.reveal(d,'保存本机草稿');ops.reveal(d,'保存云端复盘')
            result['cases'].append('Reflection facts and read-only editor controls loaded')
            formal.go(d,'系统提醒')
            u.wait(d,lambda n:ops.present(n,'系统通知与事项提醒'),'Native reminders entry')
            nodes=ops.reveal(d,'开启本设备提醒')
            if not ops.present(nodes,'开启本设备提醒'):raise RuntimeError('Expected default-off native reminders; no toggle attempted.')
            result['cases'].append('Native reminder page loads; device remains default off; no permission changed')
            if formal.sync(d)!=counts:raise RuntimeError('Queue changed; untouched.')
            d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE)
            d.adb('shell','am','start','-n',ui.ACTIVITY)
            u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original session after reopen')
            if formal.sync(d)!=counts:raise RuntimeError('Queue changed on reopen; untouched.')
            result['cases'].append('Cold reopen retains original login and queue')
        result.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        result['at']=datetime.now(timezone.utc).isoformat()
        path=FOLDER/(args.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')
        with path.open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
        print(json.dumps(result,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
