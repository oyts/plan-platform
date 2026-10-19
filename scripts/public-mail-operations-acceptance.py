"""Real HTTPS focused checks; existing synthetic USER accounts, no real mail/AI."""
import importlib.util,json,os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('web',Path(__file__).with_name('public-web-acceptance.py'))
w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))

def main():
    run=w.Run();code=0
    users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'))
    assert set(users)==set(w.EMAILS) and all(v.get('registered') for v in users.values())
    run.secrets.extend(v['password'] for v in users.values())
    try:
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block')
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]]);run.secrets.append(token)
                        api=lambda path:w.api(ctx,token,'GET',path)
                        def account():
                            assert api('/health')['version']=='0.1.16'
                            config=api('/auth/config')
                            assert config['emailVerificationEnabled'] and config['operationsAvailable']
                            assert not config['registrationEnabled'] and config['passwordResetEnabled'] and config['invitationEnabled']
                            response=ctx.request.get(w.ORIGIN+'/api/auth/email-verification/status',headers={'Authorization':'Bearer '+token})
                            assert response.status==200 and 'no-store' in response.headers.get('cache-control','')
                            status=response.json();assert status['enabled'] and status['configured']
                            page.goto(w.ORIGIN+'/settings')
                            panel=page.get_by_role('region',name='邮箱验证',exact=True)
                            expect(panel).to_be_visible();expect(panel.get_by_text('已验证' if status['verified'] else '尚未验证',exact=True)).to_be_visible()
                            panel.get_by_role('button',name='重新读取验证状态',exact=True).click()
                            expect(panel.get_by_role('alert')).to_have_count(0)
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(profile+'-verification.png')),full_page=True)
                        run.check(profile+': 正式邮箱验证状态、受限开关及只读刷新',account)
                        def expired():
                            page.goto(w.ORIGIN+'/verify-email?token='+'0'*64)
                            expect(page.get_by_role('heading',name='验证邮箱',exact=True)).to_be_visible()
                            page.get_by_role('button',name='确认验证',exact=True).click()
                            expect(page.get_by_role('alert')).to_be_visible()
                            page.get_by_role('link',name='前往账号设置',exact=True).click()
                            expect(page.get_by_role('heading',name='账号安全与数据迁移',exact=True)).to_be_visible()
                            assert api('/auth/me')['role']=='USER'
                        run.check(profile+': 无效合成验证链接不退出已有账号',expired)
                        def boundaries():
                            for path in ['/admin/operations/status','/admin/operations/mail-status','/admin/governance/config']:
                                w.api(ctx,token,'GET',path,status=403)
                            for path in ['/api/admin/operations/status','/api/auth/email-verification/status','/api/client-updates/android/latest']:
                                assert ctx.request.get(w.ORIGIN+path,headers={'Authorization':''}).status==401
                            manifest=ctx.request.get(w.ORIGIN+'/downloads/manifest.json').json()
                            assert manifest['android'] is None
                            assert ctx.request.get(w.ORIGIN+'/client-updates/android/latest.json').status==404
                        run.check(profile+': 普通成员及未登录边界，官网APK继续关闭',boundaries)
                    finally:
                        ctx.request.post(w.ORIGIN+'/api/auth/logout');ctx.close()
            finally:browser.close()
    except Exception as error:
        print(run.redact(error)[:1000]);code=1
        if not run.results or run.results[-1]['status']!='FAIL':run.results.append({'name':'Focused prerequisites','status':'FAIL','error':type(error).__name__})
    finally:run.report()
    return code
if __name__=='__main__':raise SystemExit(main())
