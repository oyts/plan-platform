"""Real HTTPS0.1.25 UI, existing synthetic USER, no native device operations.

Native public feeds are byte-compared with a pre-cutover baseline. The protected
Android feed remains0.1.24/29; this is not native download/install acceptance.
"""
import argparse,hashlib,importlib.util,json,os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('updates_public_base',Path(__file__).with_name('public-web-acceptance.py'))
w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))
BASELINE=ROOT/'acceptance-results/update-experience/public-feeds-before.json'
PUBLIC_FEEDS=['/downloads/manifest.json','/client-updates/windows/latest.json']

def hashes(request):
    result={}
    for path in PUBLIC_FEEDS:
        response=request.get(w.ORIGIN+path);assert response.status==200
        value=response.json()
        assert (value['windows']['version'] if path.startswith('/downloads') else value['version'])=='0.1.24'
        if path.startswith('/downloads'):assert value['android'] is None
        result[path]=hashlib.sha256(response.body()).hexdigest()
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline',action='store_true');args=parser.parse_args()
    if args.baseline:
        BASELINE.parent.mkdir(parents=True,exist_ok=True)
        assert not BASELINE.exists(),'Existing baseline preserved; inspect before any repeat.'
        with sync_playwright() as p:
            request=p.request.new_context();assert request.get(w.ORIGIN+'/api/health').json()['version']=='0.1.24'
            value=hashes(request);request.dispose()
        with BASELINE.open('x',encoding='utf-8') as f:json.dump(value,f,indent=2)
        print('Native public0.1.24 feed baseline saved; no installation or source change.')
        return
    run=w.Run();failed=False
    try:
        with sync_playwright() as p:
            request=p.request.new_context()
            def health():
                health=request.get(w.ORIGIN+'/api/health').json();assert health['version']=='0.1.25' and health['ok']
                cfg=request.get(w.ORIGIN+'/api/auth/config').json();assert cfg['serverVersion']=='0.1.25' and cfg['registrationEnabled'] is False and cfg['invitationEnabled'] is True
                assert hashes(request)==json.loads(BASELINE.read_text(encoding='utf-8'))
                assert request.get(w.ORIGIN+'/api/client-updates/android/latest').status==401
                assert request.get(w.ORIGIN+'/client-updates/android/latest.json').status==404
                assert request.get(w.ORIGIN+'/api/admin/access').status==401
            run.check('HTTPS0.1.25、注册关闭、原生公共清单逐字节保持0.1.24及私有鉴权',health);request.dispose()
            users=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'));assert set(users)==set(w.EMAILS) and all(v.get('registered') for v in users.values())
            run.secrets.extend(v['password'] for v in users.values());browser=p.chromium.launch(headless=True)
            try:
                for profile,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block')
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],users[w.EMAILS[0]]);run.secrets.append(token)
                        def highlights():
                            notice=page.get_by_role('status',name='本版更新亮点',exact=True);expect(notice).to_contain_text('0.1.25');expect(notice.get_by_role('listitem')).to_have_count(3)
                            notice.get_by_role('button',name='知道了',exact=True).click();expect(notice).to_have_count(0)
                            page.goto(w.ORIGIN+'/help#release-history');history=page.get_by_role('region',name='版本说明与历史',exact=True)
                            expect(history).to_contain_text('0.1.25');expect(history).to_contain_text('自动下载默认关闭');expect(page.get_by_text('浏览器页面，没有原生安装版本',exact=False)).to_be_visible()
                            expect(page.get_by_role('region',name='当前设备与服务器')).to_contain_text('0.1.25')
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(profile+'-release-history.png')),full_page=True)
                        run.check(profile+': 当前网页亮点、关闭、版本历史与窄屏布局',highlights)
                        def reopen():
                            page.reload();expect(page.get_by_role('region',name='版本说明与历史')).to_be_visible();expect(page.get_by_role('status',name='本版更新亮点')).to_have_count(0)
                            expect(page.get_by_role('combobox',name='工作模式',exact=True)).to_be_visible()
                            assert any(c.get('secure') and c.get('httpOnly') for c in ctx.cookies(w.ORIGIN+'/api/auth/refresh')),'Secure HttpOnly refresh cookie missing at its scoped path'
                            response=ctx.request.get(w.ORIGIN+'/api/client-updates/android/latest',headers={'Authorization':'Bearer '+token});assert response.status==200,'Protected feed rejected a synthetic member'
                            assert 'private' in response.headers.get('cache-control','') and 'no-store' in response.headers.get('cache-control',''),'Private feed cache policy differs'
                            manifest=response.json();assert manifest['version']=='0.1.24' and manifest['versionCode']==29 and manifest['minimumSupportedVersionCode']==1,'Native feed version/minimum changed'
                            assert manifest['sha256']=='cd78c412f99f313b43b922dce8cebd339ab38da3c9c0d7b225abf81e216f0bba','Accepted old APK hash changed'
                            me=ctx.request.get(w.ORIGIN+'/api/auth/me',headers={'Authorization':'Bearer '+token});assert me.status==200,'Synthetic account identity unavailable'
                            assert manifest['packageName']=='cn.zijun701.plan.app' and manifest['accountId']==me.json()['id'],'Private feed package/owner differs'
                        run.check(profile+': 刷新保留会话、亮点仅一次及Android受限源仍0.1.24',reopen)
                    finally:ctx.close()
            finally:browser.close()
    except Exception as e:failed=True;print('Stopped: '+run.redact(e),flush=True)
    finally:run.report()
    if failed:raise SystemExit(1)
if __name__=='__main__':main()
