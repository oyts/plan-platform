"""0.1.20 HTTPS readonly affected acceptance; invited synthetic USER accounts only."""
import importlib.util,json,os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('historical_public_base',Path(__file__).with_name('public-web-acceptance.py'))
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
                            assert ctx.request.get(w.ORIGIN+'/api/health').json()['version']=='0.1.20'
                            config=ctx.request.get(w.ORIGIN+'/api/auth/config').json();assert config['registrationEnabled'] is False and config['invitationEnabled'] is True
                            status=api('GET','/auth/email-change/status');assert status['enabled'] is True and status['configured'] is True
                            assert 'token' not in status;api('GET','/admin/governance/members',status=403)
                        run.check(profile+': 正式版本、邮箱能力及普通用户权限',capability)
                        def panels():
                            page.goto(w.ORIGIN+'/settings')
                            panel=page.get_by_role('region',name='更改登录邮箱',exact=True)
                            expect(panel.get_by_label('新邮箱',exact=True)).to_be_visible()
                            expect(panel.get_by_label('再次输入新邮箱',exact=True)).to_be_visible()
                            expect(panel.get_by_role('button',name='发送两封验证邮件',exact=True)).to_be_visible()
                            expect(page.get_by_role('region',name='补迁旧版回收站',exact=True)).to_be_visible()
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(profile+'-historical-panels.png')),full_page=True)
                            page.reload();expect(page.get_by_role('heading',name='账号安全与数据迁移',exact=True)).to_be_visible()
                        run.check(profile+': 新增双邮箱和补迁页面、刷新登录及布局',panels)
                        def boundaries():
                            preview=api('POST','/migrations/legacy/preview',[],200);assert preview['ready'] is False
                            api('GET','/migrations/legacy/00000000-0000-0000-0000-000000000000/recycling-preview',status=404)
                            assert ctx.request.get(w.ORIGIN+'/downloads/manifest.json').json()['android'] is None
                            assert ctx.request.get(w.ORIGIN+'/client-updates/android/latest.json').status==404
                        run.check(profile+': 旧格式预检、所有权隐藏和公开APK限制',boundaries)
                    finally:ctx.request.post(w.ORIGIN+'/api/auth/logout');ctx.close()
            finally:browser.close()
    except Exception as error:
        code=1;print(run.redact(error)[:1000])
        if not run.results:run.results.append(dict(name='HTTPS prerequisites',status='FAIL',seconds=0))
    finally:run.report()
    return code
if __name__=='__main__':raise SystemExit(main())
