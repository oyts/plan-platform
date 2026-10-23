"""Formal0.1.20/25 ->0.1.21/26 readonly retention and affected AI entry checks."""
import argparse, importlib.util, json, re
from datetime import datetime, timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('reliability_formal_base',Path(__file__).with_name('android-mail-operations-formal.py'))
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
ops,u,ui=base.ops,base.u,base.ui
ui.LABELS.update({'AI 规划','AI 规划助手','对话','草稿','发送并讨论','生成规划草稿'})
FOLDER=ROOT/'acceptance-results/native-ai-reliability-0121'
SHA='93b071d63ad392669ce61a96d2d309b3500b575205fb169958ffceeea1e21e6e'

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--phase',required=True,choices=['before','after','update-check']);args=p.parse_args()
    d=base.AccountReadOnlyDevice(args.serial);before=args.phase=='before'
    version,code=('0.1.20',25) if before else ('0.1.21',26)
    u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code);d.foreground()
    u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original authenticated workspace')
    FOLDER.mkdir(parents=True,exist_ok=True);counts=base.sync(d)
    result=dict(status='FAIL',version=version,versionCode=code,cases=[],businessWrites=0,paidAiCalls=0,realEmails=0,queueSubmitted=False)
    try:
        if before:
            result.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
            return
        baseline=json.loads((FOLDER/'android-before.json').read_text(encoding='utf-8'))
        if counts!=baseline['queue']:raise RuntimeError('Original queue differs; no sync attempted.')
        path=d.adb('shell','pm','path',ui.PACKAGE).strip()
        if not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',path):raise RuntimeError('One installed formal APK required.')
        if d.adb('shell','sha256sum',path[8:]).split()[0].lower()!=SHA:raise RuntimeError('Installed APK differs.')
        result['cases'].append('Exact signed APK, original login and baseline queue retained')
        if args.phase=='update-check':
            base.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本')
            u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Current protected release')
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）']
            if len(boxes)!=1 or boxes[0].get('checked')!='false':raise RuntimeError('Auto download differs; preference unchanged.')
            result['cases'].append('Protected source latest; automatic download unchecked')
        else:
            base.go(d,'AI 规划');u.wait(d,lambda n:ops.present(n,'AI 规划助手'),'Affected AI workspace')
            ops.reveal(d,'对话');d.click('对话');nodes=ops.reveal(d,'发送并讨论');u.no_errors(nodes)
            if not ops.present(nodes,'生成规划草稿'):nodes=ops.reveal(d,'生成规划草稿')
            if not ops.present(nodes,'发送并讨论') or not ops.present(nodes,'生成规划草稿'):raise RuntimeError('New explicit intent controls unavailable.')
            result['cases'].append('New discussion/generation controls visible without sending paid request')
            ops.reveal(d,'草稿');d.click('草稿');u.wait(d,lambda n:ops.present(n,'02 草稿预览'),'Draft panel')
            result['cases'].append('Chat/draft mobile navigation without business mutation')
            if base.sync(d)!=counts:raise RuntimeError('Queue changed; left untouched.')
            d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE);d.adb('shell','am','start','-n',ui.ACTIVITY)
            u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original session after reopen')
            if base.sync(d)!=counts:raise RuntimeError('Queue changed after reopen.')
            result['cases'].append('Cold reopen retains original login and queue')
        result.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        result['at']=datetime.now(timezone.utc).isoformat()
        with (FOLDER/(args.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')).open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
        print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
