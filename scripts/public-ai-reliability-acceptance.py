"""0.1.21 HTTPS new protocol/UI checks; dedicated invited USERs, no provider requests."""
import importlib.util,json,os,re
from datetime import datetime,timedelta,timezone
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('public_reliability_base',Path(__file__).with_name('public-web-acceptance.py'))
w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))
def main():
    run=w.Run();failed=False
    try:
        with sync_playwright() as p:
            check=p.request.new_context(base_url=w.ORIGIN)
            assert check.get('/api/health').json()['version']=='0.1.21'
            config=check.get('/api/auth/config').json();assert config['registrationEnabled'] is False and config['invitationEnabled'] is True
            check.dispose()
            users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'))
            assert set(users)==set(w.EMAILS) and all(v.get('registered') for v in users.values())
            run.secrets.extend(v['password'] for v in users.values())
            browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block');other=browser.new_context(service_workers='block')
                    session=None;api=None
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]]);_,other_token=w.login(other,w.EMAILS[1],users[w.EMAILS[1]])
                        run.secrets.extend([token,other_token]);api=lambda method,path,data=None,status=200:w.api(ctx,token,method,path,data,status)
                        quota=api('GET','/ai/quota');notes='仅HTTPS合成验收：零基础Java，两个月，每天一小时，星期日休息。'
                        session=api('POST','/ai/sessions',dict(mode='PLAN',consent=True))
                        sid=session['id']
                        def protocol():
                            nonlocal session
                            capability=api('GET','/ai/capabilities');assert capability['intentEnabled'] and capability['requirementsEnabled'] and capability['available']
                            version=session['version'];session=api('PUT',f'/ai/sessions/{sid}/requirements',dict(text=notes,version=version))
                            assert session['requirementsText']==notes and session['draft'] is None
                            api('PUT',f'/ai/sessions/{sid}/requirements',dict(text='合成陈旧修改',version=version),409)
                            w.api(other,other_token,'GET',f'/ai/sessions/{sid}',status=404)
                            response=ctx.request.get(w.ORIGIN+f'/api/ai/sessions/{sid}',headers={'Authorization':'Bearer '+token})
                            assert 'no-store' in response.headers.get('cache-control','')
                        run.check(profile+': 新能力、需求保存、409、所有权及私有缓存',protocol)
                        def controls():
                            page.goto(w.ORIGIN+'/ai?session='+sid)
                            expect(page.get_by_role('button',name='发送并讨论',exact=True)).to_be_visible()
                            page.get_by_text('需求笔记（发送过的要求持续保留）',exact=False).click()
                            note=page.get_by_label('需求笔记',exact=True);expect(note).to_have_value(notes)
                            note.fill(notes+'\n补充：不自动创建。');expect(page.get_by_role('button',name='生成规划草稿',exact=True)).to_be_disabled()
                            page.get_by_role('button',name='保存需求笔记',exact=True).click()
                            expect(page.get_by_text('需求笔记已保存；下次发送才会交给AI，没有扣次数。')).to_be_visible()
                            assert api('GET','/ai/quota')['used']==quota['used']
                        run.check(profile+': 真实新前端需求编辑保护和不收费保存',controls)
                        def retained():
                            current=api('GET',f'/ai/sessions/{sid}');day=datetime.now(timezone(timedelta(hours=8))).date()
                            while day.isoweekday()==7:day+=timedelta(days=1)
                            draft=dict(schemaVersion=1,mode='PLAN',revision=0,title='HTTPS合成未应用草稿',startDate=str(day),endDate=str(day+timedelta(days=30)),restDays=[7],stages=[],items=[dict(localId='one',title='合成未应用事项',note='',dueDate=str(day),plannedMinutes=30,stageId=None,prerequisites=[])])
                            api('PUT',f'/ai/sessions/{sid}/draft',dict(version=current['version'],draftJson=json.dumps(draft)))
                            page.reload();page.get_by_text('需求笔记（发送过的要求持续保留）',exact=False).click();expect(page.get_by_label('需求笔记',exact=True)).to_have_value(re.compile('不自动创建'))
                            if profile=='mobile-web':page.get_by_role('navigation',name='AI 工作区').get_by_role('button',name=re.compile('^草稿')).click()
                            expect(page.get_by_label('任务标题',exact=True)).to_have_value('合成未应用事项')
                            expect(page.get_by_role('button',name='确认生成',exact=True)).to_be_disabled()
                            after=api('GET','/ai/quota');assert (after['used'],after['pending'])==(quota['used'],quota['pending'])
                        run.check(profile+': 重开保留需求/手动草稿、确认门槛、额度不变',retained)
                    finally:
                        if session and api:
                            current=api('GET','/ai/sessions/'+session['id']);api('DELETE',f"/ai/sessions/{session['id']}?version={current['version']}",status=204)
                        ctx.close();other.close()
            finally:browser.close()
    except Exception as error:
        failed=True;print('Stopped: '+run.redact(error),flush=True)
    finally:run.report()
    if failed:raise SystemExit(1)
if __name__=='__main__':main()
