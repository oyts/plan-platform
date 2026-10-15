"""Read-only teaching entry checks in the installed formal APK, no login input."""
import argparse
import json
from pathlib import Path
import importlib.util

spec=importlib.util.spec_from_file_location('upgrade',Path(__file__).with_name('android-post-upgrade-acceptance.py'))
upgrade=importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)
ui=upgrade.ui
ui.LABELS.update({'使用教学','全部课程','离线与同步'})

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',required=True)
    parser.add_argument('--expected-version',required=True)
    parser.add_argument('--expected-code',required=True,type=int)
    args=parser.parse_args()
    device=ui.Device(args.serial)
    device.foreground()
    upgrade.verify_version(device.adb('shell','dumpsys','package',ui.PACKAGE),args.expected_version,args.expected_code)
    before=upgrade.sync_state(device)
    upgrade.menu(device,'使用教学')
    nodes=upgrade.wait(device,lambda n:any(v.get('text')=='选择教学' or v.get('content-desc')=='选择教学' for v in n) or sum(v.get('text')=='开始练习' for v in n)==4,'Four teaching cards')
    for title in ['周期计划','目标路线','今日待办','离线与同步']:
        if not any(v.get('text')==title for v in nodes):raise RuntimeError('A teaching course is missing.')
    result={'version':args.expected_version,'versionCode':args.expected_code,'scope':'Installed formal APK, read-only teaching navigation; full interactions covered separately in isolated native regression','courses':[]}
    for title,marker in [('周期计划','练习计划名称'),('目标路线','练习目标名称'),('今日待办','练习待办标题'),('离线与同步','已在练习中联网打开这条任务')]:
        upgrade.reveal(device,title,'up')
        device.click(title)
        nodes=upgrade.wait(device,lambda n:any(v.get('text')==marker for v in n),'Teaching exercise '+title)
        if not any(v.get('text')=='独立练习区' for v in nodes):raise RuntimeError('Independent teaching notice missing.')
        result['courses'].append(title)
    upgrade.reveal(device,'全部课程','up')
    device.click('全部课程')
    upgrade.wait(device,lambda n:sum(v.get('text')=='开始练习' for v in n)==4,'Return to four courses')
    after=upgrade.sync_state(device)
    if before!=after:raise RuntimeError('Read-only teaching navigation changed the queue.')
    result.update(status='PASS',queueBefore=before,queueAfter=after)
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
