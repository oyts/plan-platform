"""Formal0.1.22/27 ->0.1.23/28: readonly management and retention; no auth input."""
import argparse,importlib.util,json,re,time
from pathlib import Path
from datetime import datetime,timezone
spec=importlib.util.spec_from_file_location('admin_formal_base',Path(__file__).with_name('android-mail-operations-formal.py'));b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
ops,u,ui=b.ops,b.u,b.ui
ui.LABELS.update({'管理中心','管理总览','成员与邀请','AI资源与用量','运行与安全','反馈处理','返回个人工作区','AI运行策略','全局并发上限','预览参数修改','创建邀请','账号邮件服务','读取最新运行配置'})
FOLDER=Path(__file__).resolve().parents[1]/'acceptance-results/native-admin-center-0123'
SHA='3972f735c5a223f6b7ac0a0738cc617b51471b91caf93046699656a16aaca0fa'

def reveal(d,label):
    for _ in range(20):
        nodes=d.snapshot();u.no_errors(nodes)
        if ops.present(nodes,label):return nodes
        d.scroll('down')
    raise RuntimeError('Admin control not visible: '+label)

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--phase',choices=['before','after','update-check'],required=True);a=p.parse_args();d=b.AccountReadOnlyDevice(a.serial)
    version,code=('0.1.22',27) if a.phase=='before' else ('0.1.23',28)
    u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code);d.foreground()
    u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original authenticated workspace')
    FOLDER.mkdir(parents=True,exist_ok=True);counts=b.sync(d)
    report=dict(status='FAIL',version=version,versionCode=code,cases=[],businessWrites=0,paidAiCalls=0,realEmails=0,queueSubmitted=False)
    try:
        if a.phase=='before':
            report.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
            return
        before=json.loads((FOLDER/'android-before.json').read_text());assert counts==before['queue'],'Original queue differs; not submitted'
        path=d.adb('shell','pm','path',ui.PACKAGE).strip();assert re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',path)
        assert d.adb('shell','sha256sum',path[8:]).split()[0].lower()==SHA
        report['cases'].append('Exact signed APK, original login and original queue retained')
        if a.phase=='update-check':
            b.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本');u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Current authenticated update source')
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）'];assert len(boxes)==1 and boxes[0].get('checked')=='false'
            report['cases'].append('Protected0.1.23 update source; automatic download unchecked')
        else:
            b.go(d,'管理中心');u.wait(d,lambda n:ops.present(n,'管理总览') and ops.present(n,'返回个人工作区'),'Independent admin overview')
            report['cases'].append('Independent five-section management navigation')
            reveal(d,'AI资源与用量');d.click('AI资源与用量');u.wait(d,lambda n:ops.present(n,'AI运行策略'),'Runtime policy visible')
            reveal(d,'预览参数修改');report['cases'].append('Native runtime policy read and numeric preview controls; no parameter write')
            d.scroll('up');d.scroll('up');reveal(d,'成员与邀请');d.click('成员与邀请');u.wait(d,lambda n:ops.present(n,'创建邀请'),'Member/invitation controls')
            report['cases'].append('Member and invitation section without sending invitation')
            d.scroll('up');reveal(d,'返回个人工作区');d.click('返回个人工作区');u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Personal workspace restored')
            assert b.sync(d)==counts
            d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE);d.adb('shell','am','start','-n',ui.ACTIVITY)
            u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original login after cold reopen');assert b.sync(d)==counts
            report['cases'].append('Personal workspace return and cold reopen retain login and queue')
        report.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        with (FOLDER/(a.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')).open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
        print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
