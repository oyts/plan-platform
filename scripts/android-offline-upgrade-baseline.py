"""Saved synthetic Todo only; restore both network switches even on failure."""
import argparse
import importlib.util
import json
import re
import time
from pathlib import Path

spec=importlib.util.spec_from_file_location('android_ui',Path(__file__).with_name('android-ui-acceptance.py'))
ui=importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)

def wait_for(device,predicate,description):
    deadline=time.monotonic()+35
    while time.monotonic()<deadline:
        try:
            nodes=device.snapshot()
            if predicate(nodes):return nodes
        except RuntimeError as error:
            if 'not focused' not in str(error):raise
        time.sleep(.3)
    raise RuntimeError(description+' was not verified; no follow-up save/sync.')

def queue_counts(nodes):
    values=[]
    for label in ['本地操作','待处理冲突']:
        indices=[i for i,n in enumerate(nodes) if n.get('text')==label]
        if len(indices)!=1 or indices[0]<1:raise RuntimeError('Queue summary not visible.')
        value=nodes[indices[0]-1].get('text','')
        if not value.isdigit():raise RuntimeError('Queue count was not numeric.')
        values.append(int(value))
    return values

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',required=True)
    parser.add_argument('--title',required=True)
    args=parser.parse_args()
    if not re.fullmatch(r'nativeupgrade-[a-z0-9-]{1,100}-offline',args.title):
        raise RuntimeError('Expected a synthetic offline title.')
    device=ui.Device(args.serial)
    nodes=device.snapshot()
    labels=[i for i,n in enumerate(nodes) if n.get('text')=='待办标题']
    if len(labels)!=1:raise RuntimeError('Open the isolated synthetic Todo editor first.')
    before=nodes[labels[0]+1].get('text','')
    if not before.startswith('nativeupgrade-') or before==args.title:
        raise RuntimeError('Only an existing synthetic Todo can be used once.')
    original={name:device.adb('shell','settings','get','global',name).strip() for name in ['wifi_on','mobile_data']}
    if any(value not in {'0','1'} for value in original.values()):raise RuntimeError('Unrecognized network switch state.')
    result={'scope':'Formal Android UI, synthetic Todo only','beforeTitle':before,'offlineTitle':args.title,'networkBefore':original,'status':'RUNNING'}
    try:
        device.adb('shell','svc','wifi','disable')
        device.adb('shell','svc','data','disable')
        time.sleep(1)
        device.type_title(args.title)
        device.click('保存待办')
        wait_for(device,lambda n: not any(v.get('text')=='待办标题' for v in n) and any(v.get('text')==args.title for v in n),'Offline saved list')
        device.foreground()
        device.adb('shell','am','force-stop',ui.PACKAGE)
        device.adb('shell','am','start','-n',ui.ACTIVITY)
        wait_for(device,lambda n:any(v.get('text')==args.title for v in n),'Reopened cached synthetic title')
        device.click('更多')
        wait_for(device,lambda n:any(v.get('text')=='同步' or v.get('content-desc')=='同步' for v in n),'Navigation')
        device.click('同步')
        nodes=wait_for(device,lambda n:any(v.get('text')=='本地操作' for v in n),'Sync summary')
        result['queueBeforeUpgrade'],result['conflictsBeforeUpgrade']=queue_counts(nodes)
        if (result['queueBeforeUpgrade'],result['conflictsBeforeUpgrade'])!=(1,0):
            raise RuntimeError('Expected exactly one isolated operation and no conflicts. Queue preserved, not submitted.')
        result['reopenedOfflineTitleVerified']=True
        result['status']='PASS'
    finally:
        for name,service in [('wifi_on','wifi'),('mobile_data','data')]:
            device.adb('shell','svc',service,'enable' if original[name]=='1' else 'disable')
        result['networkRestored']={name:device.adb('shell','settings','get','global',name).strip() for name in original}
        if result['networkRestored']!=original:raise RuntimeError('Network restoration needs user attention.')
        print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
