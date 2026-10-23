"""Formal0.1.19/24 ->0.1.20/25 affected readonly checks; never auth, mail or queue writes."""
import argparse,importlib.util,json,re
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('historical_formal_base',Path(__file__).with_name('android-mail-operations-formal.py'))
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
ops,u,ui=base.ops,base.u,base.ui
ui.LABELS.update({'更改登录邮箱','重新读取变更状态','补迁旧版回收站','发送两封验证邮件','新邮箱','再次输入新邮箱'})
FOLDER=ROOT/'acceptance-results/native-historical-0120'
SHA='3ba9f4020e4c3c614c87b252a0835de50b20e37da4c361120a37d3b7a6cf579d'
def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--phase',required=True,choices=['before','after','update-check']);args=p.parse_args()
    d=base.AccountReadOnlyDevice(args.serial);before=args.phase=='before'
    version,code=('0.1.19',24) if before else ('0.1.20',25)
    u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code);d.foreground()
    u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Existing authenticated workspace')
    FOLDER.mkdir(parents=True,exist_ok=True);counts=base.sync(d)
    result=dict(status='FAIL',version=version,versionCode=code,businessWrites=0,queueSubmitted=False,realEmails=0,paidAiCalls=0,cases=[])
    try:
        if before:
            result.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
            return
        baseline=json.loads((FOLDER/'android-before.json').read_text(encoding='utf-8'))
        if counts!=baseline['queue']:raise RuntimeError('Original queue differs; no sync attempted.')
        path=d.adb('shell','pm','path',ui.PACKAGE).strip()
        if not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',path):raise RuntimeError('One installed APK required.')
        if d.adb('shell','sha256sum',path[8:]).split()[0].lower()!=SHA:raise RuntimeError('Installed APK differs.')
        result['cases'].append('Exact signed APK and original authenticated queue retained')
        if args.phase=='update-check':
            base.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本')
            u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Protected current release')
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）']
            if len(boxes)!=1 or boxes[0].get('checked')!='false':raise RuntimeError('Auto download differs; unchanged.')
            result['cases'].append('Protected source latest and automatic download unchecked')
        else:
            base.go(d,'设置');ops.reveal(d,'更改登录邮箱');ops.reveal(d,'重新读取变更状态');d.click('重新读取变更状态')
            nodes=ops.reveal(d,'发送两封验证邮件');u.no_errors(nodes)
            if any('服务器暂未启用邮箱变更' in n.get('text','') or '邮件服务暂不可用' in n.get('text','') for n in nodes):raise RuntimeError('Approved email-change capability unavailable.')
            result['cases'].append('New dual-mailbox controls load over HTTPS without entering password or sending mail')
            nodes=ops.reveal(d,'补迁旧版回收站');u.no_errors(nodes)
            result['cases'].append('Legacy supplement panel loads without import, preview selection or mutation')
            if base.sync(d)!=counts:raise RuntimeError('Queue changed; untouched.')
            d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE);d.adb('shell','am','start','-n',ui.ACTIVITY)
            u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original login after reopen')
            if base.sync(d)!=counts:raise RuntimeError('Queue changed after reopen.')
            result['cases'].append('Cold reopen retains original login and queue')
        result.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        result['at']=datetime.now(timezone.utc).isoformat()
        with (FOLDER/(args.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')).open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
        print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
