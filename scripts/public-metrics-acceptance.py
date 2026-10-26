"""Public0.1.24 scoped acceptance, isolated browser and existing synthetic USER.
No administrator authentication, private plans, paid AI or real email.
"""
import importlib.util,json,os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('metrics_public_base',Path(__file__).with_name('public-web-acceptance.py'));w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))
def main():
    run=w.Run();failed=False
    try:
        with sync_playwright() as p:
            r=p.request.new_context(base_url=w.ORIGIN)
            def health():
                assert r.get('/api/health').json()['version']=='0.1.24'
                assert r.get('/api/auth/config').json()['registrationEnabled'] is False
                cfg=r.get('/api/public/analytics/config');assert cfg.status==200 and cfg.json()['enabled'] is True and 'no-store' in cfg.headers.get('cache-control','')
                for path in ['/api/admin/metrics/runtime','/api/admin/metrics/growth?from=2026-10-26&to=2026-10-26','/api/admin/metrics/website?from=2026-10-26&to=2026-10-26']:assert r.get(path).status==401
                assert r.get('/client-updates/android/latest.json').status==404 and r.get('/downloads/manifest.json').json()['android'] is None
            run.check('正式健康、匿名指标拒绝与公众APK关闭',health);r.dispose()
            users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'));run.secrets.extend(v['password'] for v in users.values());browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block')
                    try:
                        page=ctx.new_page();requests=[];responses=[]
                        def capture(req):
                            if '/api/public/analytics/event' in req.url:requests.append(req)
                        page.on('request',capture)
                        page.on('response',lambda response: responses.append(response) if '/api/public/analytics/event' in response.url else None)
                        def consent():
                            page.goto(w.ORIGIN+'/welcome');page.get_by_text('隐私与可选访客分析',exact=True).click();toggle=page.get_by_role('checkbox',name='参与官网匿名访客估算（可选，默认关闭）');expect(toggle).not_to_be_checked();assert not requests
                            toggle.check();expect(page.get_by_role('region',name='官网可选访客分析')).to_contain_text('已记录本次匿名估算');assert len(requests)==1 and responses[0].status==204
                            req=requests[0];headers=req.all_headers();assert not headers.get('authorization') and not headers.get('cookie');body=req.post_data_json;assert set(body)=={'page','ticket','visitorId'} and body['page']=='WELCOME'
                            page.reload();page.get_by_text('隐私与可选访客分析',exact=True).click();expect(toggle).to_be_checked();expect(page.get_by_role('region',name='官网可选访客分析')).to_contain_text('已记录本次匿名估算');assert len(requests)==2 and responses[-1].status==204
                            toggle.uncheck();page.reload();page.get_by_text('隐私与可选访客分析',exact=True).click();expect(toggle).not_to_be_checked();page.wait_for_timeout(600);assert len(requests)==2
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1');page.screenshot(path=str(run.out/(profile+'-metrics-consent.png')),full_page=True)
                        run.check(profile+': 默认不参与、主动无凭据采集与退出保留',consent)
                        def member():
                            unused,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]]);run.secrets.append(token)
                            for path in ['/api/admin/metrics/runtime','/api/admin/metrics/growth?from=2026-10-26&to=2026-10-26','/api/admin/metrics/website?from=2026-10-26&to=2026-10-26']:
                                response=ctx.request.get(w.ORIGIN+path,headers={'Authorization':'Bearer '+token});assert response.status==403
                        run.check(profile+': 普通成员不能读取管理指标',member)
                    finally:ctx.close()
            finally:browser.close()
    except Exception as e:failed=True;print('Stopped: '+run.redact(e),flush=True)
    finally:run.report()
    if failed:raise SystemExit(1)
if __name__=='__main__':main()
