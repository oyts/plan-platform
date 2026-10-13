"""Actual formal APK UI acceptance. Never logs in or reads session storage."""
import argparse
import importlib.util
import json
import re
from pathlib import Path

def load(name,filename):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(filename))
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

ui=load('android_ui','android-ui-acceptance.py')
baseline=load('android_baseline','android-offline-upgrade-baseline.py')
ui.LABELS.update({'我的计划','新建计划','新建路线','查看本地请求内容'})

def no_errors(nodes):
    for node in nodes:
        value=node.get('text','')
        if value.startswith('Error:') or re.search(r'请求未完成（HTTP \d+）',value) or value in {
            '连接失败，请检查网络、HTTPS 证书和服务器设置。',
            '无法连接服务器。请检查后端是否启动、服务器地址、网络和 HTTPS 证书。',
        }:
            raise RuntimeError('A business connection error is visible; acceptance stopped.')

def wait(device,predicate,label):
    nodes=baseline.wait_for(device,predicate,label)
    no_errors(nodes)
    return nodes

def menu(device,label):
    device.click('更多')
    wait(device,lambda n:any(label in (v.get('text'),v.get('content-desc')) for v in n),'Mobile navigation')
    device.click(label)

def sync_state(device):
    menu(device,'同步')
    nodes=wait(device,lambda n:any(v.get('text')=='本地操作' for v in n),'Queue summary')
    return baseline.queue_counts(nodes)

def todo(device,title):
    device.click('待办')
    return wait(device,lambda n:any(v.get('text')==title for v in n),'Preserved synthetic Todo')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',required=True)
    parser.add_argument('--title',required=True)
    parser.add_argument('--sync-fixture',action='store_true')
    args=parser.parse_args()
    if not re.fullmatch(r'nativeupgrade-[a-z0-9-]{1,100}-offline',args.title):raise RuntimeError('Only the synthetic acceptance Todo is allowed.')
    device=ui.Device(args.serial)
    device.foreground()
    metadata=device.adb('shell','dumpsys','package',ui.PACKAGE)
    if not re.search(r'versionName=0\.1\.7(?:\s|$)',metadata) or not re.search(r'versionCode=12(?:\s|$)',metadata):
        raise RuntimeError('Actual formal package must be 0.1.7 / 12.')
    result={'version':'0.1.7','versionCode':12,'scope':'Formal APK UI, original session, synthetic Todo only','status':'RUNNING'}
    counts=sync_state(device)
    if counts!=[1,0]:raise RuntimeError('Expected preserved 1/0 queue. No operation submitted.')
    result['queueAfterUpgrade']=counts
    todo(device,args.title)
    device.foreground()
    device.adb('shell','am','force-stop',ui.PACKAGE)
    device.adb('shell','am','start','-n',ui.ACTIVITY)
    wait(device,lambda n:any(v.get('text')==args.title for v in n),'Reopened cached title without login')
    result['reopenWithoutLoginVerified']=True
    if sync_state(device)!=[1,0]:raise RuntimeError('Queue changed after reopen; preserved for inspection.')
    result['queueAfterReopen']=[1,0]
    result['modes']=[]
    for mode,link,marker in [('周期计划','计划','我的计划'),('目标路线','路线','新建路线'),('今日待办','待办',args.title)]:
        device.click('工作模式')
        wait(device,lambda n:any(v.get('text')==mode for v in n),'Workspace option')
        device.click(mode)
        wait(device,lambda n:any(link in (v.get('text'),v.get('content-desc')) for v in n),'Workspace navigation')
        device.click(link)
        wait(device,lambda n:any(v.get('text')==marker for v in n),'Loaded workspace '+mode)
        result['modes'].append(mode)
    if sync_state(device)!=[1,0]:raise RuntimeError('Read-only mode checks changed queue; no sync attempted.')
    if args.sync_fixture:
        # Re-read the only queued request through its own UI disclosure before
        # submission, not app storage. Never submit an unrelated user's queue.
        device.scroll()
        wait(device,lambda n:any(v.get('text')=='查看本地请求内容' for v in n),'Queue request disclosure')
        device.click('查看本地请求内容')
        nodes=device.snapshot()
        payloads=[]
        for node in nodes:
            try:
                value=json.loads(node.get('text',''))
                if isinstance(value,dict) and isinstance(value.get('data'),dict) and value['data'].get('title')==args.title:payloads.append(value)
            except (ValueError,TypeError):pass
        if len(payloads)!=1 or not any(re.fullmatch(r'更新 /todos/[^ /]+',n.get('text','')) for n in nodes):
            raise RuntimeError('The single queued operation was not proven to be the synthetic Todo. Not submitted.')
        if baseline.queue_counts(nodes)!=[1,0]:raise RuntimeError('Queue changed before sync; not submitted.')
        # Scroll upwards until the observed primary control is above the dock.
        for attempt in range(4):
            nodes=device.snapshot()
            controls=[n for n in nodes if n.get('text')=='同步等待项']
            dock=[n for n in nodes if n.get('text')=='更多' and n.get('clickable')=='true']
            if len(controls)==1 and len(dock)==1:
                bounds=list(map(int,re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',controls[0].get('bounds')).groups()))
                dock_bounds=list(map(int,re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',dock[0].get('bounds')).groups()))
                if bounds[3]>bounds[1] and bounds[3]<dock_bounds[1]:break
            device.scroll('up')
        else:raise RuntimeError('Sync control was not visible above the navigation dock. Not submitted.')
        device.click('同步等待项')
        nodes=wait(device,lambda n:any(v.get('text')=='队列为空' for v in n),'Synthetic queue synchronized')
        if baseline.queue_counts(nodes)!=[0,0]:raise RuntimeError('Unexpected post-sync count.')
        todo(device,args.title)
        result['syntheticSyncVerified']=True
        result['queueAfterSync']=[0,0]
    result['status']='PASS'
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
