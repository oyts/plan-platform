"""Formal 0.1.18 -> 0.1.19 readonly acceptance. Never enters auth or writes plans."""
import argparse,importlib.util,json,re
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('previous',Path(__file__).with_name('android-ai-workbench-formal.py'))
previous=importlib.util.module_from_spec(spec);spec.loader.exec_module(previous)
formal,ops,u,ui=previous.formal,previous.ops,previous.u,previous.ui
ui.LABELS.update({'个人模板库','个人整套模板库','保存一套结构','预览保存结构','使用教学','新手教学','整套模板'})
FOLDER=ROOT/'acceptance-results/native-template-0119'
SHA='488216b7136f89ee9c3d17fda54416084f40c9fd4b7e56ecc8681b516c137491'

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True)
    p.add_argument('--phase',required=True,choices=['before','after','update-check']);args=p.parse_args()
    d=previous.StableDevice(args.serial);before=args.phase=='before'
    version,code=('0.1.18',23) if before else ('0.1.19',24)
    u.verify_version(d.adb('shell','dumpsys','package',ui.PACKAGE),version,code)
    d.foreground();u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original authenticated workspace')
    FOLDER.mkdir(parents=True,exist_ok=True);counts=formal.sync(d)
    result=dict(status='FAIL',version=version,versionCode=code,businessWrites=0,queueSubmitted=False,cases=[])
    try:
        if before:
            result.update(status='BASELINE_RECORDED',queue=counts,authenticatedWorkspaceVisible=True)
            with (FOLDER/'android-before.json').open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
            return
        baseline=json.loads((FOLDER/'android-before.json').read_text(encoding='utf-8'))
        if counts!=baseline['queue']:raise RuntimeError('Original queue differs; no sync attempted.')
        path=d.adb('shell','pm','path',ui.PACKAGE).strip()
        if not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',path):raise RuntimeError('Expected one base APK.')
        if d.adb('shell','sha256sum',path[8:]).split()[0].lower()!=SHA:raise RuntimeError('Installed APK hash differs.')
        result['cases'].append('Exact signed APK, original login and baseline queue retained')
        if args.phase=='update-check':
            formal.go(d,'客户端');ops.reveal(d,'检查新版本');d.click('检查新版本')
            u.wait(d,lambda n:ops.present(n,'当前已是最新版本。'),'Protected current release')
            boxes=[n for n in ops.reveal(d,'自动下载更新（安装前仍需确认）') if n.get('text')=='自动下载更新（安装前仍需确认）']
            if len(boxes)!=1 or boxes[0].get('checked')!='false':raise RuntimeError('Auto download differs; left unchanged.')
            result['cases'].append('Protected update is latest; auto download off')
        else:
            formal.go(d,'个人模板库')
            nodes=u.wait(d,lambda n:ops.present(n,'个人整套模板库'),'New template library');u.no_errors(nodes)
            ops.reveal(d,'保存一套结构');ops.reveal(d,'预览保存结构')
            result['cases'].append('Real HTTPS template library and preview controls loaded without writing')
            if formal.sync(d)!=counts:raise RuntimeError('Queue differs; untouched.')
            d.foreground();d.adb('shell','am','force-stop',ui.PACKAGE);d.adb('shell','am','start','-n',ui.ACTIVITY)
            u.wait(d,lambda n:ops.present(n,'工作模式') and ops.present(n,'更多'),'Original login after reopen')
            if formal.sync(d)!=counts:raise RuntimeError('Queue changed after reopen; untouched.')
            result['cases'].append('Cold reopen retains original login and queue')
        result.update(status='PASS',queueBefore=counts,queueAfter=counts,nonemptyQueueUpgradeProven=counts[0]>0)
    finally:
        result['at']=datetime.now(timezone.utc).isoformat()
        path=FOLDER/(args.phase+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')
        with path.open('x',encoding='utf-8') as f:json.dump(result,f,indent=2)
        print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
