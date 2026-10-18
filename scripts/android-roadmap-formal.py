"""Read-only exact formal APK 0.1.15 checks after an explicitly approved upgrade."""
import argparse, importlib.util, json, re
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('upgrade',Path(__file__).with_name('android-post-upgrade-acceptance.py'))
u=importlib.util.module_from_spec(spec);spec.loader.exec_module(u)
u.ui.LABELS.update({'帮助与版本','检查服务器版本','个人数据中心','数据中心','反馈与诊断','问题摘要','生成并查看诊断摘要','离线范围','离线范围与预加载','当前设备缓存','预加载这 7 天','下载私有 JSON 备份'})
SHA='19fd4815f6477716b4eea170e30b6f381b82589449a2d04d9f0b45e6a448e9ee'

def present(nodes,text):return any(text in (n.get('text'),n.get('content-desc')) for n in nodes)

def reveal(device,label):
    for _ in range(10):
        nodes=device.snapshot();u.no_errors(nodes)
        found=[n for n in nodes if label in (n.get('text'),n.get('content-desc'))]
        if len(found)==1:
            bounds=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',found[0].get('bounds',''))
            if bounds and int(bounds[3])>int(bounds[1]) and int(bounds[4])>int(bounds[2]) and int(bounds[2])>=350 and int(bounds[4])<2506:return nodes
        device.scroll('down')
    raise RuntimeError('Read-only control not visibly available: '+label)

def go(device,label):
    device.click('更多');u.wait(device,lambda n:present(n,label),'Grouped navigation')
    reveal(device,label);device.click(label)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True);parser.add_argument('--update-check',action='store_true');args=parser.parse_args()
    device=u.ui.Device(args.serial)
    u.verify_version(device.adb('shell','dumpsys','package',u.ui.PACKAGE),'0.1.15',20)
    device.foreground()
    folder=ROOT/'acceptance-results/native-roadmap-0115'
    before=json.loads((folder/'android-before.json').read_text(encoding='utf-8'))
    report={'version':'0.1.15','versionCode':20,'status':'FAIL','businessWrites':0,'paidAiCalls':0,'realEmailsSent':0,'queueSubmitted':False,'cases':[]}
    try:
        if args.update_check:
            go(device,'客户端');reveal(device,'检查新版本');device.click('检查新版本')
            nodes=u.wait(device,lambda n:present(n,'当前已是最新版本。'),'Protected update check')
            nodes=reveal(device,'自动下载更新（安装前仍需确认）')
            checkbox=next(n for n in nodes if n.get('text')=='自动下载更新（安装前仍需确认）')
            report.update(status='PASS',automaticDownloadChecked=checkbox.get('checked')=='true',preferenceModified=False)
            report['cases'].append('Exact formal APK checks the published protected source; no download/install replay')
        else:
            paths=device.adb('shell','pm','path',u.ui.PACKAGE).strip().splitlines()
            if len(paths)!=1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk',paths[0]):raise RuntimeError('Expected one installed formal APK')
            if device.adb('shell','sha256sum',paths[0][8:]).split()[0].lower()!=SHA:raise RuntimeError('Installed APK differs from the signed candidate')
            report['installedApkSha256']=SHA
            if u.sync_state(device)!=before['queue']:raise RuntimeError('Queue differs from baseline; left untouched')
            report['cases'].append('Original authenticated workspace and baseline queue preserved after same-signature upgrade')
            for link,marker in [('数据中心','下载私有 JSON 备份'),('反馈与诊断','问题摘要'),('离线范围','当前设备缓存'),('帮助与版本','检查服务器版本')]:
                go(device,link);u.wait(device,lambda n,m=marker:present(n,m),'New readonly page')
                report['cases'].append('New page loads read-only: '+link)
            reveal(device,'检查服务器版本');device.click('检查服务器版本')
            u.wait(device,lambda n:sum('0.1.15' in v.get('text','') for v in n)>=3,'Resource/install/server version match')
            report['cases'].append('Resource, installed APK and HTTPS server all show 0.1.15')
            device.foreground();device.adb('shell','am','force-stop',u.ui.PACKAGE);device.adb('shell','am','start','-n',u.ui.ACTIVITY)
            u.wait(device,lambda n:present(n,'工作模式'),'Original account after reopen')
            if u.sync_state(device)!=before['queue']:raise RuntimeError('Queue changed on reopen; no sync performed')
            report['cases'].append('Authenticated workspace and exact baseline queue retained after reopen')
            report.update(status='PASS',queueBefore=before['queue'],queueAfter=before['queue'],nonemptyQueueUpgradeProven=before['queue'][0]>0)
    finally:
        report['at']=datetime.now(timezone.utc).isoformat()
        name='android-update-check.json' if args.update_check else 'android-formal.json'
        # Private generated evidence only; never stores UI XML, account names or plan text.
        (folder/name).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
