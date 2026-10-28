"""HTTPS0.1.26 affected weekly flow, existing synthetic USER and unchanged feeds."""
import argparse,hashlib,importlib.util,json,os
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('core_public_base',Path(__file__).with_name('public-web-acceptance.py'))
w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH',str(ROOT/'.desktop-tools/playwright'))
BASELINE=ROOT/'acceptance-results/core-freeze/public-feeds-before-0126.json'
def feed_hashes(request):
    result={}
    for path in ['/downloads/manifest.json','/client-updates/windows/latest.json']:
        r=request.get(w.ORIGIN+path);assert r.status==200;value=r.json()
        assert (value['windows']['version'] if path.startswith('/downloads') else value['version'])=='0.1.25'
        if path.startswith('/downloads'):assert value['android'] is None
        result[path]=hashlib.sha256(r.body()).hexdigest()
    return result
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline',action='store_true');args=parser.parse_args()
    if args.baseline:
        assert not BASELINE.exists(),'Prior baseline retained'
        with sync_playwright() as p:
            r=p.request.new_context();assert r.get(w.ORIGIN+'/api/health').json()['version']=='0.1.25';value=feed_hashes(r);r.dispose()
        BASELINE.parent.mkdir(parents=True,exist_ok=True)
        with BASELINE.open('x',encoding='utf-8')as f:json.dump(value,f)
        print('Saved pre-cutover public25 feed hashes; no mutation.');return
    run=w.Run()
    try:
        with sync_playwright()as p:
            request=p.request.new_context()
            def health():
                h=request.get(w.ORIGIN+'/api/health').json();assert h['version']=='0.1.26'and h['ok']
                c=request.get(w.ORIGIN+'/api/auth/config').json();assert c['serverVersion']=='0.1.26'and c['registrationEnabled']is False and c['invitationEnabled']is True
                assert request.get(w.ORIGIN+'/api/client-updates/android/latest').status==401
                assert request.get(w.ORIGIN+'/client-updates/android/latest.json').status==404
                assert request.get(w.ORIGIN+'/api/admin/access').status==401
                assert feed_hashes(request)==json.loads(BASELINE.read_text())
            run.check('26健康、账号限制及原生25公共源字节保留',health);request.dispose()
            accounts=json.loads((w.PRIVATE/'accounts.json').read_text(encoding='utf-8'));assert set(accounts)==set(w.EMAILS)and all(v.get('registered')for v in accounts.values())
            run.secrets.extend(v['password']for v in accounts.values());browser=p.chromium.launch(headless=True)
            try:
                for name,viewport in w.PROFILES:
                    ctx=browser.new_context(viewport=viewport,locale='zh-CN',timezone_id='Asia/Shanghai',service_workers='block')
                    try:
                        page,token=w.login(ctx,w.EMAILS[0],accounts[w.EMAILS[0]]);run.secrets.append(token)
                        def weekly():
                            page.goto(w.ORIGIN+'/weekly-review?date=2026-10-12')
                            expect(page.get_by_role('heading',name='2026-10-12 至 2026-10-18',exact=True)).to_be_visible()
                            note='公网核心冻结合成本机草稿-'+run.tag
                            page.get_by_label('本周收获',exact=True).fill(note)
                            page.once('dialog',lambda d:d.dismiss());page.get_by_role('button',name='前一周',exact=True).click()
                            expect(page).to_have_url(w.ORIGIN+'/weekly-review?date=2026-10-12');expect(page.get_by_label('本周收获',exact=True)).to_have_value(note)
                            page.get_by_role('button',name='保存本机草稿',exact=True).click();expect(page.locator('.weekly-review').get_by_role('status')).to_contain_text('已存本机')
                            page.reload();expect(page.get_by_label('本周收获',exact=True)).to_have_value(note);expect(page).to_have_url(w.ORIGIN+'/weekly-review?date=2026-10-12')
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out/(name+'-weekly.png')),full_page=True)
                        run.check(name+'往周取消换周、本机草稿和刷新登录保留',weekly)
                        def security():
                            cookies=[c for c in ctx.cookies(w.ORIGIN+'/api/auth')if c['name']=='plan_refresh'];assert cookies and all(c['secure']and c['httpOnly']for c in cookies)
                            page.goto(w.ORIGIN+'/admin');expect(page.get_by_role('heading',name='管理总览',exact=True)).to_have_count(0)
                            assert ctx.request.get(w.ORIGIN+'/api/admin/access',headers={'Authorization':'Bearer '+token}).status==403
                        run.check(name+'HTTPS会话及普通成员管理权限保持',security)
                    finally:ctx.close()
            finally:browser.close()
    finally:run.report()
if __name__=='__main__':main()
