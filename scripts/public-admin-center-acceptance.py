"""0.1.23 HTTPS checks with existing synthetic USER; no admin or business writes."""
import importlib.util,json,os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('public_admin_base',Path(__file__).with_name('public-web-acceptance.py'));w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))

def main():
    run=w.Run();failed=False
    try:
        with sync_playwright() as p:
            public=p.request.new_context(base_url=w.ORIGIN)
            assert public.get('/api/health').json()['version']=='0.1.23'
            assert public.get('/api/auth/config').json()['registrationEnabled'] is False
            assert public.get('/api/admin/access').status==401
            public.dispose()
            users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'));assert set(users)==set(w.EMAILS) and all(v.get('registered') for v in users.values())
            run.secrets.extend(v['password'] for v in users.values())
            browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block')
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]]);run.secrets.append(token);headers={'Authorization':'Bearer '+token}
                        def security():
                            for path in ['/admin/access','/admin/overview','/admin/ai/runtime','/admin/ai/runtime/audit','/admin/invitations']:
                                response=ctx.request.get(w.ORIGIN+'/api'+path,headers=headers);assert response.status==403
                            page.goto(w.ORIGIN+'/admin/ai');expect(page.get_by_role('heading',name='管理中心暂不可访问',exact=True)).to_be_visible();expect(page.get_by_role('navigation',name='管理导航')).to_have_count(0)
                        run.check(profile+': 普通成员管理API403及独立布局拒绝越权',security)
                        def legacy_and_return():
                            page.goto(w.ORIGIN+'/members');expect(page).to_have_url(w.ORIGIN+'/admin/members');expect(page.get_by_role('heading',name='管理中心暂不可访问',exact=True)).to_be_visible()
                            page.get_by_role('button',name='返回个人工作区',exact=True).click();expect(page.get_by_role('combobox',name='工作模式',exact=True)).to_be_visible()
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.reload();expect(page.get_by_role('combobox',name='工作模式',exact=True)).to_be_visible();page.screenshot(path=str(run.out/(profile+'-admin-return.png')),full_page=True)
                        run.check(profile+': 旧链接兼容、返回个人工作区及重载保留',legacy_and_return)
                        def capabilities():
                            response=ctx.request.get(w.ORIGIN+'/api/ai/capabilities',headers=headers);assert response.status==200 and 'no-store' in response.headers.get('cache-control','')
                            cap=response.json();assert 100<=cap['limits']['contextChars']<=32000 and cap['maxInputChars']<=8000
                            assert ctx.request.get(w.ORIGIN+'/downloads/manifest.json').json()['android'] is None
                            assert ctx.request.get(w.ORIGIN+'/client-updates/android/latest.json').status==404
                        run.check(profile+': 新资源能力私有响应及公众APK仍关闭',capabilities)
                    finally:ctx.close()
            finally:browser.close()
    except Exception as e:failed=True;print('Stopped: '+run.redact(e),flush=True)
    finally:run.report()
    if failed:raise SystemExit(1)
if __name__=='__main__':main()
