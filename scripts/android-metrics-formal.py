"""Formal0.1.23/28 ->0.1.24/29, read-only metrics and upgrade retention.
No authentication input, private text export, AI request or queue submission.
"""
import argparse,importlib.util,json,re
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('metric_formal_base',Path(__file__).with_name('android-mail-operations-formal.py'));b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
ops,u,ui=b.ops,b.u,b.ui
ui.LABELS.update({'管理中心','管理总览','返回个人工作区','AI资源与用量','运行与安全','注册增长与官网访问','轻量运行与请求指标','刷新运行指标','昨日','近7日','近30日','查看每日新增注册数据表','阈值、持续窗口与恢复条件'})
FOLDER=ROOT/'acceptance-results/native-metrics-0124'
SHA='cd78c412f99f313b43b922dce8cebd339ab38da3c9c0d7b225abf81e216f0bba'

def reveal(d,text):
    for _ in range(25):
        nodes=d.snapshot();u.no_errors(nodes)
        if any(text in n.get('text','') or text in n.get('content-desc','') for n in nodes):return nodes
        d.scroll('down')
    raise RuntimeError('Metrics text not visible: '+text)

def top(d):
    for _ in range(12):
        nodes=d.snapshot();u.no_errors(nodes)
        if ops.present(nodes,'返回个人工作区'):return
        d.scroll('up')
    raise RuntimeError('Admin return control unavailable.')

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--phase',required=True,choices=['before','after','update-check']);a=p.parse_args();d=b.AccountReadOnlyDevice(a.serial)
    version,code=('0.1.23',28) if a.phase=='before' else ('0.1.24',29)
    u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code);d.foreground()
    u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original authenticated workspace')
    FOLDER.mkdir(parents=True,exist_ok=True);counts=b.sync(d)
    report=dict(status='FAIL',version=version,versionCode=code,cases=[],businessWrites=0,paidAiCalls=0,realEmails=0,queueSubmitted=False)
    try:
        if a.phase=='before':
            report.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
            return
        baseline=json.loads((FOLDER/'android-before.json').read_text(encoding='utf-8'));assert counts==baseline['queue'],'Original queue changed; untouched'
        path=d.adb('shell','pm','path',ui.PACKAGE).strip();assert re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',path)
        assert d.adb('shell','sha256sum',path[8:]).split()[0].lower()==SHA,'Exact signed bytes required'
        report['cases'].append('Exact signed APK, authenticated workspace and original queue retained')
        if a.phase=='update-check':
            b.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本');u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Current authenticated optional update')
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）'];assert len(boxes)==1 and boxes[0].get('checked')=='false'
            report['cases'].append('Protected0.1.24 update source; automatic download unchecked')
        else:
            b.go(d,'管理中心');u.wait(d,lambda n:ops.present(n,'管理总览') and ops.present(n,'返回个人工作区'),'Management overview')
            reveal(d,'注册增长与官网访问');reveal(d,'按账号created_at');reveal(d,'跨日仅为日去重访客合计')
            report['cases'].append('Actual growth and opt-in website statistics with explicit daily-visitor meaning')
            top(d);reveal(d,'运行与安全');d.click('运行与安全');reveal(d,'轻量运行与请求指标');reveal(d,'采样在新鲜度内');reveal(d,'保持关闭');reveal(d,'本机就绪')
            report['cases'].append('Fresh runtime resources/readiness and independent mail off')
            top(d);reveal(d,'AI资源与用量');d.click('AI资源与用量');reveal(d,'尚保留的上游尝试');reveal(d,'平均排队等待')
            report['cases'].append('AI attempt/repair/wait metadata read without provider request')
            top(d);d.click('返回个人工作区');u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Personal workspace restored');assert b.sync(d)==counts
            d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE);d.adb('shell','am','start','-n',ui.ACTIVITY)
            u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original login after cold reopen');assert b.sync(d)==counts
            report['cases'].append('Cold reopen preserves login and exact original queue')
        report.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        file=a.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json'
        with (FOLDER/file).open('x',encoding='utf-8') as f:json.dump(report,f,indent=2)
        print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
