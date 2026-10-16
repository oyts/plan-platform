"""Read-only teaching entry checks in the installed formal APK, no login input."""
import argparse
import json
from pathlib import Path
import importlib.util
from datetime import datetime,timezone

spec=importlib.util.spec_from_file_location('upgrade',Path(__file__).with_name('android-post-upgrade-acceptance.py'))
upgrade=importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)
ui=upgrade.ui
ui.LABELS.update({'使用教学','全部课程','离线与同步','AI 规划'})

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',required=True)
    parser.add_argument('--expected-version',required=True)
    parser.add_argument('--expected-code',required=True,type=int)
    parser.add_argument('--ai-only',action='store_true',help='Only the new AI lesson and queue preservation, not old course regression')
    args=parser.parse_args()
    courses=[('周期计划','练习计划名称'),('目标路线','练习目标名称'),('今日待办','练习待办标题'),('离线与同步','已在练习中联网打开这条任务')]
    if tuple(map(int,args.expected_version.split('.'))) >= (0,1,12):courses.append(('AI 规划','练习目标'))
    if args.ai_only:courses=[('AI 规划','练习目标')]
    device=ui.Device(args.serial)
    device.foreground()
    upgrade.wait(device,lambda n:any(v.get('text')=='工作模式' for v in n),'Preserved login after upgrade')
    upgrade.verify_version(device.adb('shell','dumpsys','package',ui.PACKAGE),args.expected_version,args.expected_code)
    before=upgrade.sync_state(device)
    upgrade.menu(device,'使用教学')
    nodes=upgrade.wait(device,lambda n:any(v.get('text')=='全部课程' for v in n),'Teaching navigation')
    upgrade.reveal(device,'全部课程','up');device.click('全部课程')
    nodes=upgrade.wait(device,lambda n:any(v.get('text')=='选择教学' or v.get('content-desc')=='选择教学' for v in n) or any(v.get('text')=='开始练习' for v in n),'Teaching cards')
    for title,_ in courses:
        if not any(v.get('text')==title for v in nodes):raise RuntimeError('A teaching course is missing.')
    result={'version':args.expected_version,'versionCode':args.expected_code,'scope':'Installed formal APK, read-only teaching navigation; full interactions covered separately in isolated native regression','courses':[]}
    for title,marker in courses:
        upgrade.reveal(device,title,'up')
        device.click(title)
        nodes=upgrade.wait(device,lambda n:any(v.get('text')==marker or (args.ai_only and ('AI 教学模拟' in (v.get('text'),v.get('content-desc')) or v.get('text')=='AI 规划教学已完成')) for v in n),'Teaching exercise '+title)
        if not any(v.get('text')=='独立练习区' for v in nodes):raise RuntimeError('Independent teaching notice missing.')
        result['courses'].append(title)
    upgrade.reveal(device,'全部课程','up')
    device.click('全部课程')
    upgrade.wait(device,lambda n:any(v.get('text')=='开始练习' for v in n),'Return to courses')
    after=upgrade.sync_state(device)
    if before!=after:raise RuntimeError('Read-only teaching navigation changed the queue.')
    result.update(status='PASS',queueBefore=before,queueAfter=after)
    directory=Path(__file__).resolve().parents[1]/'acceptance-results'/'android-learning-formal'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    directory.mkdir(parents=True,exist_ok=False)
    (directory/'report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
    print('Evidence:',directory)

if __name__=='__main__':main()
