"""0.1.19 affected HTTPS checks; invited synthetic accounts, no AI/mail calls."""
import importlib.util,json,os
from pathlib import Path
from uuid import uuid4
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('web',Path(__file__).with_name('public-web-acceptance.py'))
w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))

def main():
    run=w.Run();code=0
    try:
        users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'))
        assert set(users)==set(w.EMAILS) and all(x.get('registered') for x in users.values())
        run.secrets.extend(x['password'] for x in users.values())
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block')
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]]);run.secrets.append(token)
                        api=lambda method,path,data=None,status=200:w.api(ctx,token,method,path,data,status)
                        def capability():
                            health=ctx.request.get(w.ORIGIN+'/api/health').json()
                            assert health['version']=='0.1.19' and health['capabilities']['structureTemplates']
                            assert ctx.request.get(w.ORIGIN+'/api/auth/config').json()['registrationEnabled'] is False
                            assert ctx.request.get(w.ORIGIN+'/api/structure-templates').status==401
                            api('GET','/admin/ai/settings',status=403)
                        run.check(profile+': 新版能力、受邀身份与权限',capability)
                        def template():
                            source=api('POST','/plans',dict(name='HTTPS模板合成来源-'+run.tag,startDate='2026-11-01',endDate='2026-11-07',restDays=[7]),201)
                            api('POST',f'/plans/{source["id"]}/tasks',dict(title='HTTPS合成结构任务',date='2026-11-02',startTime='09:30',endTime='10:00',plannedMinutes=30,category='STUDY',required=True,sortOrder=0,acceptance=[]),201)
                            page.goto(w.ORIGIN+'/templates')
                            expect(page.get_by_role('heading',name='个人整套模板库',exact=True)).to_be_visible()
                            page.get_by_role('combobox',name='来源对象',exact=True).select_option(source['id'])
                            page.get_by_label('模板名称',exact=True).fill('HTTPS合成模板-'+run.tag+'-'+profile)
                            page.get_by_role('button',name='预览保存结构',exact=True).click()
                            preview=page.get_by_role('region',name='模板提交预览')
                            expect(preview).to_contain_text('09:30')
                            confirm=page.get_by_role('button',name='确认保存模板',exact=True)
                            expect(confirm).to_be_disabled()
                            preview.get_by_label('我已核对完整结构、保存范围及新对象设置，确认继续。',exact=True).check()
                            confirm.click();expect(page.get_by_role('button',name='查看保存的模板',exact=True)).to_be_visible()
                            tasks=api('GET',f'/plans/{source["id"]}/tasks');assert len(tasks)==1 and tasks[0]['startTime'] in ['09:30','09:30:00']
                            assert tasks[0]['progress']['status']=='PENDING'
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(profile+'-templates.png')),full_page=True)
                        run.check(profile+': 真实模板预览与明确保存、来源不变及布局',template)
                        def lesson():
                            page.goto(w.ORIGIN+'/learn/template')
                            expect(page.get_by_role('combobox',name='练习结构来源',exact=True)).to_be_visible()
                            expect(page.get_by_role('button',name='下一步',exact=True)).to_be_disabled()
                            assert ctx.request.get(w.ORIGIN+'/downloads/manifest.json').json()['android'] is None
                            assert ctx.request.get(w.ORIGIN+'/client-updates/android/latest.json').status==404
                        run.check(profile+': 第六课和公开APK限制',lesson)
                    finally:
                        ctx.request.post(w.ORIGIN+'/api/auth/logout');ctx.close()
            finally:browser.close()
    except Exception as error:
        code=1;print(run.redact(error)[:1000])
        if not run.results:run.results.append(dict(name='HTTPS prerequisites',status='FAIL',seconds=0))
    finally:run.report()
    return code
if __name__=='__main__':raise SystemExit(main())
