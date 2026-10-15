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
    nodes=device.snapshot()
    if not any(v.get('content-desc')=='待办' and v.get('clickable')=='true' for v in nodes):
        device.click('工作模式')
        wait(device,lambda n:any(v.get('text')=='今日待办' for v in n),'Todo workspace option')
        device.click('今日待办')
        wait(device,lambda n:any(v.get('content-desc')=='待办' and v.get('clickable')=='true' for v in n),'Todo workspace navigation')
    device.click_navigation('待办')
    return wait(device,lambda n:any(v.get('text')==title for v in n),'Preserved synthetic Todo')

def verify_version(metadata,version,code):
    if not re.fullmatch(r'\d+\.\d+\.\d+',version) or not isinstance(code,int) or code<1:
        raise RuntimeError('Expected an explicit release version and positive build code.')
    if not re.search(r'versionName='+re.escape(version)+r'(?:\s|$)',metadata) or not re.search(r'versionCode='+str(code)+r'(?:\s|$)',metadata):
        raise RuntimeError('Actual formal package differs from the expected release. No sync attempted.')

def visible_above_dock(nodes,label):
    controls=[n for n in nodes if label in (n.get('text'),n.get('content-desc')) and n.get('clickable')=='true']
    dock=[n for n in nodes if n.get('text')=='更多' and n.get('clickable')=='true']
    if len(controls)!=1 or len(dock)!=1:return False
    def bounds(node):
        match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds',''))
        return list(map(int,match.groups())) if match else None
    control,dock_bounds=bounds(controls[0]),bounds(dock[0])
    return bool(control and dock_bounds and control[2]>control[0] and control[3]>control[1] and control[1]>=350 and control[3]<dock_bounds[1])

def reveal(device,label,direction):
    for _ in range(7):
        nodes=device.snapshot()
        no_errors(nodes)
        if visible_above_dock(nodes,label):return nodes
        device.scroll(direction)
    raise RuntimeError('Required control is not visibly actionable above the dock; no sync attempted.')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',required=True)
    parser.add_argument('--title',required=True)
    parser.add_argument('--sync-fixture',action='store_true')
    parser.add_argument('--expected-version',required=True)
    parser.add_argument('--expected-code',required=True,type=int)
    args=parser.parse_args()
    if not re.fullmatch(r'nativeupgrade-[a-z0-9-]{1,100}-offline',args.title):raise RuntimeError('Only the synthetic acceptance Todo is allowed.')
    device=ui.Device(args.serial)
    device.foreground()
    metadata=device.adb('shell','dumpsys','package',ui.PACKAGE)
    verify_version(metadata,args.expected_version,args.expected_code)
    result={'version':args.expected_version,'versionCode':args.expected_code,'scope':'Formal APK UI, original session, synthetic Todo only','status':'RUNNING'}
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
        device.click_navigation(link)
        wait(device,lambda n:any(v.get('text')==marker for v in n),'Loaded workspace '+mode)
        result['modes'].append(mode)
    if sync_state(device)!=[1,0]:raise RuntimeError('Read-only mode checks changed queue; no sync attempted.')
    if args.sync_fixture:
        # Re-read the only queued request through its own UI disclosure before
        # submission, not app storage. Never submit an unrelated user's queue.
        reveal(device,'查看本地请求内容','down')
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
        reveal(device,'同步等待项','up')
        device.click('同步等待项')
        nodes=wait(device,lambda n:any(v.get('text')=='队列为空' for v in n),'Synthetic queue synchronized')
        if baseline.queue_counts(nodes)!=[0,0]:raise RuntimeError('Unexpected post-sync count.')
        todo(device,args.title)
        result['syntheticSyncVerified']=True
        result['queueAfterSync']=[0,0]
    result['status']='PASS'
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
