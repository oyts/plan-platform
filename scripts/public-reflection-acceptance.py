"""Affected 0.1.18 HTTPS checks with existing invited synthetic ordinary users.

No business writes, provider calls, messages, native permissions or reminders.
Browser local drafts contain only fixed synthetic text, in isolated contexts.
"""
import importlib.util
import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('web',Path(__file__).with_name('public-web-acceptance.py'))
w=importlib.util.module_from_spec(spec); spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))


def main():
    run=w.Run(); code=0
    try:
        with sync_playwright() as p:
            public=p.request.new_context(base_url=w.ORIGIN)
            try:
                assert public.get('/api/health').json()['version']=='0.1.18'
                assert public.get('/api/auth/config').json()['registrationEnabled'] is False
            finally: public.dispose()
            users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'))
            assert set(users)==set(w.EMAILS) and all(v.get('registered') for v in users.values())
            run.secrets.extend(v['password'] for v in users.values())
            browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block')
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]])
                        run.secrets.append(token)
                        api=lambda method,path,data=None,status=200:w.api(ctx,token,method,path,data,status)
                        date=datetime.now(timezone(timedelta(hours=8))).date()
                        day=str(date); week=str(date-timedelta(days=date.weekday()))
                        quota=api('GET','/ai/quota')
                        def facts():
                            for kind,start in [('DAY',day),('WEEK',week)]:
                                value=api('GET',f'/reflection/facts?kind={kind}&start={start}')
                                assert value['kind']==kind and value['start']==start and value['timezone']=='Asia/Shanghai'
                                for mode in ['PLAN','TODO','ROUTE']:
                                    bucket='ACTUAL' if mode=='ROUTE' else 'ARRANGED'
                                    items=api('GET',f'/reflection/items?kind={kind}&start={start}&mode={mode}&bucket={bucket}&page=0&size=20')
                                    assert len(items['content'])<=20
                            prefs=api('GET','/reflection/preferences')
                            assert prefs['dailyEnabled'] is False and prefs['weeklyEnabled'] is False
                            assert prefs['dailyTime']=='22:30' and prefs['weekDay']==7
                        run.check(profile+': 日周三模式事实、分页与保守默认',facts)
                        def draft():
                            page.goto(w.ORIGIN+'/reflection?kind=DAY&date='+day)
                            expect(page.get_by_role('heading',name='日周复盘',exact=True)).to_be_visible()
                            expect(page.get_by_role('status').filter(has_text='正在读取')).to_have_count(0)
                            field=page.get_by_label('收获',exact=True)
                            field.fill('仅本轮浏览器合成草稿，不提交云端')
                            page.get_by_role('button',name='保存本机草稿',exact=True).click()
                            expect(page.get_by_role('status')).to_contain_text('本机')
                            page.reload()
                            expect(field).to_have_value('仅本轮浏览器合成草稿，不提交云端')
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(profile+'-reflection.png')),full_page=True)
                        run.check(profile+': 正式复盘本机草稿重开与布局',draft)
                        def reminders():
                            page.goto(w.ORIGIN+'/reminders')
                            expect(page.get_by_role('heading',name='系统通知与事项提醒',exact=True)).to_be_visible()
                            expect(page.get_by_text('网页版仅提供打开页面且联网时的站内提醒',exact=False)).to_be_visible()
                            expect(page.get_by_role('button',name='保存提醒规则',exact=True)).to_be_enabled()
                            assert not api('GET','/reminders/devices')
                            r=api('GET','/reminders/preferences')
                            assert not r['advanceEnabled'] and not r['startEnabled'] and not r['todoEnabled']
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(profile+'-reminders.png')),full_page=True)
                        run.check(profile+': 提醒页面、默认关闭及网页版边界',reminders)
                        def privacy():
                            value=api('POST','/reflection/analysis-preview',dict(kind='DAY',start=day,includeNotes=False))
                            assert len(value['fingerprint'])==64 and value['kind']=='DAY'
                            assert ctx.request.get(w.ORIGIN+'/api/reflection/preferences').status==401
                            api('GET','/admin/ai/settings',status=403)
                            after=api('GET','/ai/quota')
                            assert (quota['used'],quota['pending'])==(after['used'],after['pending'])
                            assert ctx.request.get(w.ORIGIN+'/downloads/manifest.json').json()['android'] is None
                            assert ctx.request.get(w.ORIGIN+'/client-updates/android/latest.json').status==404
                        run.check(profile+': 仅预览不调用AI、权限及APK限制',privacy)
                    finally:
                        ctx.request.post(w.ORIGIN+'/api/auth/logout');ctx.close()
            finally: browser.close()
    except Exception as error:
        code=1; print(run.redact(error)[:1000])
        if not run.results: run.results.append(dict(name='HTTPS prerequisites',status='FAIL',seconds=0))
    finally: run.report()
    return code


if __name__=='__main__': raise SystemExit(main())
