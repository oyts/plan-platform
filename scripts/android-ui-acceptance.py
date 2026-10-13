"""Formal APK UI driver: public controls and synthetic records only; no auth input.

Uses the real shipped WebView's accessibility nodes via adb, not a debug WebView
or direct database manipulation. Raw UI XML is held in memory, never reported.
"""
import argparse
import json
import re
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[1]
ADB=ROOT/'.desktop-tools/android-sdk/platform-tools/adb.exe'
PACKAGE='cn.zijun701.plan.app'
ACTIVITY=PACKAGE+'/cn.zijun701.plan.mobile.MainActivity'
LABELS={'计划舱','周期计划','目标路线','今日待办','待办','计划','路线','更多','设置','同步','同步中心','客户端','手机客户端','返回设置','返回登录','添加待办','待办标题','安排日期','备注','保存待办','取消','编辑','保存','检查新版本','下载更新','安装并重新打开','允许安装更新','当前已是最新版本。','客户端更新','服务器连接','本地操作','待处理冲突','队列为空','恢复连接','查看日期','搜索','未完成','全部','日期范围','自动下载更新（安装前仍需确认）','稍后处理','同步全部','开始同步'}
LABELS.add('工作模式')

class Device:
    def __init__(self,serial): self.serial=serial
    def adb(self,*args):
        result=subprocess.run([str(ADB),'-s',self.serial,*args],capture_output=True,encoding='utf-8',errors='replace',timeout=40)
        if result.returncode: raise RuntimeError('Android command failed; private output withheld.')
        return result.stdout
    def foreground(self):
        focused=self.adb('shell','dumpsys','window')
        # Do not interact through the lock screen, keyboard or an unrelated app.
        current=[line for line in focused.splitlines() if 'mCurrentFocus=' in line]
        if not any(PACKAGE+'/' in line for line in current):
            raise RuntimeError('Formal app is not focused; unlock/return to Plan Cabin before input.')
    def snapshot(self):
        self.foreground()
        path='/data/local/tmp/plan-ui-'+uuid.uuid4().hex+'.xml'
        try:
            self.adb('shell','uiautomator','dump',path)
            raw=self.adb('shell','cat',path)
            nodes=list(ET.fromstring(raw).iter('node'))
        finally: self.adb('shell','rm','-f',path)
        if any(n.get('password')=='true' or n.get('text') in {'登录计划平台','注册计划平台'} for n in nodes):
            raise RuntimeError('Authentication UI visible; no input/capture performed.')
        return nodes
    def allowed(self,text):
        return text in LABELS or text.lower().startswith('nativeupgrade-') or bool(re.fullmatch(r'(0\.1\.[567]( / 构建 \d+)?|\d+|可更新至 0\.1\.7)',text))
    def inspect(self):
        nodes=self.snapshot();output=[]
        for index,n in enumerate(nodes):
            text=n.get('text','');desc=n.get('content-desc','');editable=n.get('class','').endswith('EditText')
            if self.allowed(text) or self.allowed(desc) or editable:
                output.append({'index':index,'text':text if self.allowed(text) else '[input]' if editable else '',
                    'description':desc if self.allowed(desc) else '', 'class':n.get('class'),
                    'bounds':n.get('bounds'),'clickable':n.get('clickable'),'checked':n.get('checked')})
        return {'package':PACKAGE,'controls':output,'nodeCount':len(nodes)}
    def point(self,node):
        match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds',''))
        if not match: raise RuntimeError('Missing control bounds.')
        a,b,c,d=map(int,match.groups())
        if c<=a or d<=b: raise RuntimeError('Control is not visible.')
        return str((a+c)//2),str((b+d)//2)
    def click(self,text):
        if not self.allowed(text): raise RuntimeError('Only known controls or synthetic test records can be clicked.')
        nodes=[n for n in self.snapshot() if text in (n.get('text'),n.get('content-desc'))]
        clickable=[n for n in nodes if n.get('clickable')=='true']
        if clickable: nodes=clickable
        if len(nodes)!=1: raise RuntimeError(f'Expected one visible control: {text}; found {len(nodes)}.')
        self.foreground();self.adb('shell','input','tap',*self.point(nodes[0]))
    def type_title(self,text,label='待办标题'):
        if not re.fullmatch(r'nativeupgrade-[a-z0-9-]{1,100}',text) and not (label=='搜索' and text==''):
            raise RuntimeError('Only a synthetic upgrade title or an empty search may be entered.')
        nodes=self.snapshot()
        labels=[i for i,n in enumerate(nodes) if n.get('text')==label]
        if len(labels)!=1: raise RuntimeError('Expected one todo title field.')
        following=nodes[labels[0]+1:]
        if not following or not following[0].get('class','').endswith('EditText'):
            raise RuntimeError('Title field is not adjacent to its observed label.')
        field=following[0]
        if label=='待办标题' and field.get('text') and not field.get('text').lower().startswith('nativeupgrade-'):
            raise RuntimeError('Existing real title cannot be overwritten.')
        # OEM input methods can reorder adb key events. A separate, user-approved
        # test APK uses ACTION_SET_TEXT against the observed real WebView field;
        # production stays non-debuggable and session storage is never accessed.
        self.foreground()
        command=['shell','am','instrument','-w','-r','-e','class',
            'cn.zijun701.plan.mobile.FormalUiTextInstrumentedTest','-e','field',
            'search' if label=='搜索' else 'title']
        if text: command.extend(['-e','text',text])
        command.append('cn.zijun701.plan.mobile.regression.test/androidx.test.runner.AndroidJUnitRunner')
        result=self.adb(*command)
        if not re.search(r'OK \(1 test\)',result):
            raise RuntimeError('Native UI helper did not verify the exact value; do not save this draft.')
        current=self.snapshot();labels=[i for i,n in enumerate(current) if n.get('text')==label]
        if len(labels)!=1 or labels[0]+1>=len(current) or current[labels[0]+1].get('text','')!=text:
            raise RuntimeError('Synthetic text entry mismatch; do not save this draft.')
    def scroll(self):
        nodes=self.snapshot()
        views=[n for n in nodes if n.get('class')=='android.webkit.WebView' and n.get('text')=='计划舱']
        if len(views)!=1: raise RuntimeError('Expected one shipped application WebView.')
        a,b,c,d=map(int,re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',views[0].get('bounds')).groups())
        self.adb('shell','input','swipe',str((a+c)//2),str(b+(d-b)*7//10),str((a+c)//2),str(b+(d-b)*3//10),'350')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True)
    parser.add_argument('action',choices=['inspect','click','open','type-title','search','scroll']);parser.add_argument('text',nargs='?')
    args=parser.parse_args();device=Device(args.serial)
    if args.action=='open': device.adb('shell','am','start','-n',ACTIVITY)
    elif args.action=='click': device.click(args.text or '')
    elif args.action=='type-title': device.type_title(args.text or '')
    elif args.action=='search': device.type_title(args.text or '',label='搜索')
    elif args.action=='scroll': device.scroll()
    print(json.dumps(device.inspect(),ensure_ascii=False,indent=2))
if __name__=='__main__':
    try: main()
    except Exception as error: print(str(error),file=sys.stderr);sys.exit(1)
