"""0.1.22 HTTPS stage2 only; invited synthetic owners, no provider or business apply."""
import copy,importlib.util,json,os,re
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('public_long_base',Path(__file__).with_name('public-web-acceptance.py'))
w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))

def main():
    run=w.Run();failed=False
    try:
        with sync_playwright() as p:
            public=p.request.new_context(base_url=w.ORIGIN)
            assert public.get('/api/health').json()['version']=='0.1.22'
            assert public.get('/api/auth/config').json()['registrationEnabled'] is False
            public.dispose()
            users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'));assert set(users)==set(w.EMAILS) and all(v.get('registered') for v in users.values())
            run.secrets.extend(v['password'] for v in users.values())
            browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block');other=browser.new_context(service_workers='block');session=None;api=None
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]]);_,other_token=w.login(other,w.EMAILS[1],users[w.EMAILS[1]])
                        run.secrets.extend([token,other_token]);api=lambda method,path,data=None,status=200:w.api(ctx,token,method,path,data,status)
                        quota=api('GET','/ai/quota');session=api('POST','/ai/sessions',dict(mode='ROUTE',consent=True));sid=session['id']
                        def long_protocol():
                            nonlocal session
                            cap=api('GET','/ai/capabilities');assert cap['longPlanningEnabled'] and cap['collaborationEnabled'] and cap['limits']['batchItems']==10
                            draft=dict(schemaVersion=1,mode='ROUTE',revision=0,title='HTTPS合成完整大纲',startDate=None,endDate=None,restDays=[],stages=[],items=[dict(localId='t'+str(i),title='合成练习'+str(i),note='待补充',dueDate=None,plannedMinutes=30,stageId=None,prerequisites=[] if i==0 else ['t'+str(i-1)]) for i in range(12)])
                            session=api('PUT',f'/ai/sessions/{sid}/draft',dict(version=session['version'],draftJson=json.dumps(draft)))
                            old=session['version'];session=api('POST',f'/ai/sessions/{sid}/long-plan',dict(version=old,coverage='完整12项合成练习，分两批手动补齐。',confirmed=True))
                            assert len(session['longPlan']['batches'])==2 and session['longPlan']['submissions']==0
                            api('POST',f'/ai/sessions/{sid}/apply',dict(version=session['version'],confirmed=True),409)
                            api('POST',f'/ai/sessions/{sid}/long-plan',dict(version=old,coverage='过期操作',confirmed=True),409)
                            w.api(other,other_token,'GET',f'/ai/sessions/{sid}',status=404)
                            for start,end in [(0,10),(10,12)]:
                                part=copy.deepcopy(session['draft']);part['items']=part['items'][start:end]
                                for item in part['items']:item['note']='合成手动细化：练习后检查结果。'
                                session=api('PUT',f'/ai/sessions/{sid}/batch',dict(version=session['version'],draftJson=json.dumps(part)))
                            assert session['longPlan']['complete'] and session['longPlan']['submissions']==0 and len(session['draft']['items'])==12 and session['applied'] is None
                        run.check(profile+': 完整大纲/两批手动完成、未完保护、409与隔离（无AI）',long_protocol)
                        def controls():
                            page.goto(w.ORIGIN+'/ai?session='+sid)
                            expect(page.get_by_role('button',name='先生成完整大纲',exact=True)).to_be_visible()
                            if profile=='mobile-web':page.get_by_role('navigation',name='AI 工作区').get_by_role('button',name=re.compile('^草稿')).click()
                            expect(page.get_by_label('长规划分批细化')).to_contain_text('已完成 2 / 2批')
                            expect(page.get_by_role('button',name='确认生成',exact=True)).to_be_disabled()
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(profile+'-long.png')),full_page=True)
                        run.check(profile+': 新前端分批状态、确认门槛与窄屏',controls)
                        def collaboration():
                            nonlocal session
                            c=page.get_by_label('外部AI协作');c.locator('summary').first.click()
                            material=api('POST',f'/ai/sessions/{sid}/collaboration/export',dict(version=session['version'],instructions='只修改合成第一项说明',consent=True))
                            assert material['format']=='plan-cabin-ai-collaboration' and sid not in json.dumps(material)
                            material['draft']['items'][0]['note']='HTTPS返回的合成说明';source=json.dumps(material,ensure_ascii=False)
                            c.get_by_label('外部返回协作JSON',exact=True).fill(source);c.get_by_role('button',name='只读预检协作材料',exact=True).click()
                            expect(c.get_by_label('协作差异预览')).to_contain_text('HTTPS返回的合成说明')
                            assert api('GET',f'/ai/sessions/{sid}')['version']==session['version']
                            c.get_by_role('checkbox',name=re.compile('我已核对差异，只保存')).check();c.get_by_role('button',name='确认保存返回草稿',exact=True).click()
                            expect(page.get_by_label('任务说明',exact=True).first).to_have_value('HTTPS返回的合成说明')
                            response=ctx.request.get(w.ORIGIN+f'/api/ai/sessions/{sid}',headers={'Authorization':'Bearer '+token});assert 'no-store' in response.headers.get('cache-control','')
                            session=response.json();assert session['applied'] is None
                            usage=api('GET','/ai/quota');assert (usage['used'],usage['pending'])==(quota['used'],quota['pending'])
                        run.check(profile+': 协作只读差异/明确保存、私有缓存、额度未变',collaboration)
                    finally:
                        if session and api:
                            current=api('GET','/ai/sessions/'+session['id']);api('DELETE',f"/ai/sessions/{session['id']}?version={current['version']}",status=204)
                        ctx.close();other.close()
            finally:browser.close()
    except Exception as error:failed=True;print('Stopped: '+run.redact(error),flush=True)
    finally:run.report()
    if failed:raise SystemExit(1)
if __name__=='__main__':main()
