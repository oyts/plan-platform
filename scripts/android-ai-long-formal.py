"""Formal0.1.21/26 ->0.1.22/27: readonly changed controls and retention only."""
import argparse,importlib.util,json,re
from pathlib import Path
from datetime import datetime,timezone
spec=importlib.util.spec_from_file_location('long_formal_base',Path(__file__).with_name('android-mail-operations-formal.py'));b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
ops,u,ui=b.ops,b.u,b.ui
ui.LABELS.update({'AI 规划','AI 规划助手','对话','草稿','发送并讨论','生成规划草稿','先生成完整大纲','交给外部 AI 修改／导回','只读预检协作材料'})
FOLDER=Path(__file__).resolve().parents[1]/'acceptance-results/native-ai-long-0122';SHA='7c65fc9bbf009c43a75f087b818a63692cbc061aaf9d7a184d2faf7d695acb7f'
def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--phase',choices=['before','after','update-check'],required=True);a=p.parse_args();d=b.AccountReadOnlyDevice(a.serial)
    version,code=('0.1.21',26) if a.phase=='before' else ('0.1.22',27);u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code);d.foreground()
    u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original logged-in workspace');FOLDER.mkdir(parents=True,exist_ok=True);counts=b.sync(d)
    report=dict(status='FAIL',version=version,versionCode=code,cases=[],businessWrites=0,paidAiCalls=0,realEmails=0,queueSubmitted=False)
    try:
        if a.phase=='before':
            report.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
            return
        before=json.loads((FOLDER/'android-before.json').read_text());assert counts==before['queue'],'Original queue differs; not submitted'
        path=d.adb('shell','pm','path',ui.PACKAGE).strip();assert re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',path)
        assert d.adb('shell','sha256sum',path[8:]).split()[0].lower()==SHA;report['cases'].append('Exact signed APK, original login and queue retained')
        if a.phase=='update-check':
            b.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本');u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Current authenticated update source')
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）'];assert len(boxes)==1 and boxes[0].get('checked')=='false';report['cases'].append('Protected latest release and automatic download unchecked')
        else:
            b.go(d,'AI 规划');u.wait(d,lambda n:ops.present(n,'AI 规划助手'),'AI workspace');ops.reveal(d,'对话');d.click('对话');ops.reveal(d,'先生成完整大纲');report['cases'].append('New full-outline action present without AI submission')
            ops.reveal(d,'草稿');d.click('草稿');u.wait(d,lambda n:ops.present(n,'02 草稿预览'),'Draft tab');ops.reveal(d,'交给外部 AI 修改／导回');d.click('交给外部 AI 修改／导回');ops.reveal(d,'只读预检协作材料');report['cases'].append('New external collaboration panel opens without exporting private content')
            assert b.sync(d)==counts;d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE);d.adb('shell','am','start','-n',ui.ACTIVITY);u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original login after cold reopen');assert b.sync(d)==counts;report['cases'].append('Cold reopen retains original login and queue')
        report.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        with (FOLDER/(a.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')).open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
        print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
