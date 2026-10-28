"""Formal 0.1.24/29 -> 0.1.25/30, readonly UI/retention/source checks.
No authentication input, queue submission, business write, or raw UI export.
"""
import argparse, importlib.util, json, re
from pathlib import Path
from datetime import datetime, timezone
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('update_formal_base',Path(__file__).with_name('android-mail-operations-formal.py'))
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
ops,u,ui=b.ops,b.u,b.ui
ui.LABELS.update({'帮助与版本','当前设备与服务器','版本说明与历史','本版更新亮点','知道了','客户端连接与更新'})
FOLDER=ROOT/'acceptance-results/native-update-experience-0125'
SHA='1c748869224eb470b95a528d39e3b626be87cd3369b3091024a9209b80846390'
BEFORE_VERSION,BEFORE_CODE='0.1.24',29
VERSION,CODE='0.1.25',30

def reveal(d,text):
    for _ in range(20):
        nodes=d.snapshot();u.no_errors(nodes)
        if ops.present(nodes,text):return nodes
        d.scroll('down')
    raise RuntimeError('Required readonly control not visible: '+text)

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True)
    p.add_argument('--phase',choices=['before','after','update-check'],required=True);a=p.parse_args()
    d=b.AccountReadOnlyDevice(a.serial);version,code=(BEFORE_VERSION,BEFORE_CODE) if a.phase=='before' else (VERSION,CODE)
    u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code);d.foreground()
    u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Existing authenticated workspace')
    FOLDER.mkdir(parents=True,exist_ok=True)
    report=dict(status='FAIL',version=version,versionCode=code,cases=[],businessWrites=0,queueSubmitted=False,paidAiCalls=0,realEmails=0)
    try:
        nodes=d.snapshot()
        if a.phase!='before' and ops.present(nodes,'知道了'):
            d.click('知道了');report['cases'].append('Installed release highlights closed explicitly')
        counts=b.sync(d)
        if a.phase=='before':
            report.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
            return
        before=json.loads((FOLDER/'android-before.json').read_text(encoding='utf-8'))
        assert counts==before['queue'],'Original queue differs; not submitted'
        path=d.adb('shell','pm','path',ui.PACKAGE).strip()
        assert re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',path)
        assert d.adb('shell','sha256sum',path[8:]).split()[0].lower()==SHA,'Exact frozen signed APK required'
        report['cases'].append('Exact signed APK, authenticated workspace and original queue retained')
        if a.phase=='update-check':
            b.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本')
            u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Protected current optional update')
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）']
            assert len(boxes)==1 and boxes[0].get('checked')=='false'
            report['cases'].append('Native protected'+VERSION+' source reports latest; automatic download unchecked')
        else:
            b.go(d,'客户端');reveal(d,VERSION+' / 构建 '+str(CODE))
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）']
            assert len(boxes)==1 and boxes[0].get('checked')=='false'
            report['cases'].append('Actual installed'+VERSION+'/'+str(CODE)+' and unchecked automatic download')
            b.go(d,'帮助与版本');reveal(d,'版本说明与历史')
            report['cases'].append('Installed release history reachable without paid request or business writes')
            assert b.sync(d)==counts
            d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE);d.adb('shell','am','start','-n',ui.ACTIVITY)
            nodes=u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Existing session after cold reopen')
            assert not ops.present(nodes,'知道了'),'Closed installed highlights should not repeat'
            assert b.sync(d)==counts
            report['cases'].append('Cold reopen retains authenticated workspace and original queue, highlights do not repeat')
        report.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        report['updatedAt']=datetime.now(timezone.utc).isoformat()
        with (FOLDER/(a.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')).open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
        print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
