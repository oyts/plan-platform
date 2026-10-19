"""Post-activation HTTPS checks. Dedicated invited USERs, no provider/mail calls."""
import importlib.util
import json
import os
import re
import uuid
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('web', Path(__file__).with_name('public-web-acceptance.py'))
w = importlib.util.module_from_spec(spec)
spec.loader.exec_module(w)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH', str(ROOT / '.desktop-tools/playwright'))


def main():
    run = w.Run()
    code = 0
    try:
        with sync_playwright() as p:
            # Refuse the old production version before loading test credentials.
            public = p.request.new_context(base_url=w.ORIGIN)
            try:
                assert public.get('/api/health').json()['version'] == '0.1.17', 'Deploy and verify 0.1.17 first.'
                assert public.get('/api/auth/config').json()['registrationEnabled'] is False
            finally:
                public.dispose()
            users = json.loads((w.PRIVATE / 'accounts.json').read_text(encoding='utf-8'))
            assert set(users) == set(w.EMAILS) and all(v.get('registered') for v in users.values())
            run.secrets.extend(v['password'] for v in users.values())
            browser = p.chromium.launch(headless=True)
            try:
                for profile, viewport in w.PROFILES:
                    ctx = browser.new_context(viewport=viewport, locale='zh-CN', timezone_id='Asia/Shanghai', service_workers='block')
                    other = browser.new_context(service_workers='block')
                    saved_id, token = str(uuid.uuid4()), None
                    created = False
                    try:
                        page, token = w.login(ctx, w.EMAILS[0], users[w.EMAILS[0]])
                        _, other_token = w.login(other, w.EMAILS[1], users[w.EMAILS[1]])
                        run.secrets.extend([token, other_token])
                        api = lambda method, path, data=None, status=200: w.api(ctx, token, method, path, data, status)
                        quota = api('GET', '/ai/quota')
                        content = dict(schemaVersion=1, mode='ROUTE', message='合成未发送要求，不调用AI',
                                       importJson='', draft=None, sourceSessionId=None, sourceVersion=None)
                        name = 'HTTPS工作台合成-' + saved_id[:8]

                        def save():
                            nonlocal created
                            body = dict(name=name, version=-1, contentJson=json.dumps(content, ensure_ascii=False))
                            result = api('PUT', '/ai/saved-drafts/' + saved_id, body)
                            created = True
                            assert result['version'] == 0 and result['content'] == content
                            response = ctx.request.get(w.ORIGIN + '/api/ai/saved-drafts/' + saved_id,
                                                       headers={'Authorization': 'Bearer ' + token})
                            assert response.status == 200 and 'no-store' in response.headers.get('cache-control', '')
                            api('PUT', '/ai/saved-drafts/' + saved_id, body, 409)
                            w.api(other, other_token, 'GET', '/ai/saved-drafts/' + saved_id, status=404)
                        run.check(profile + ': 独立草稿真实保存、私有缓存、409和他人不可读', save)

                        def workspace():
                            page.goto(w.ORIGIN + '/ai')
                            expect(page.get_by_role('heading', name='AI 规划助手', exact=True)).to_be_visible()
                            shelf = page.get_by_role('region', name='规划草稿箱', exact=True)
                            shelf.get_by_role('button', name=re.compile('^规划草稿箱')).click()
                            shelf.locator('article').filter(has_text=name).get_by_role('button', name='查看／继续', exact=True).click()
                            expect(page.get_by_label('你的目标或修改要求', exact=True)).to_have_value(content['message'])
                            expect(page.get_by_role('button', name='发送给 AI', exact=True)).to_be_disabled()
                            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1')
                            page.screenshot(path=str(run.out / (profile + '-workbench.png')), full_page=True)
                        run.check(profile + ': 正式页面读取材料且禁止独立副本直接发送', workspace)

                        def limits():
                            assert ctx.request.get(w.ORIGIN + '/api/ai/saved-drafts', headers={'Authorization': ''}).status == 401
                            api('GET', '/admin/ai/settings', status=403)
                            after = api('GET', '/ai/quota')
                            assert (after['used'], after['pending']) == (quota['used'], quota['pending'])
                            assert ctx.request.get(w.ORIGIN + '/downloads/manifest.json').json()['android'] is None
                        run.check(profile + ': 身份边界、未扣次数、官网APK仍关闭', limits)
                    finally:
                        if created and token:
                            try:
                                # Only this invocation's UUID. Never bulk-clear existing drafts.
                                current = w.api(ctx, token, 'GET', '/ai/saved-drafts/' + saved_id)
                                result = w.api(ctx, token, 'POST', '/ai/saved-drafts/clear',
                                               {'selected': [{'id': saved_id, 'version': current['version']}]})
                                assert result['deleted'] == [saved_id] and not result['skipped']
                            except Exception:
                                code = 1
                                run.results.append({'name': profile + ': 本轮合成草稿清理', 'status': 'FAIL', 'seconds': 0})
                        for context in [ctx, other]:
                            context.request.post(w.ORIGIN + '/api/auth/logout')
                            context.close()
            finally:
                browser.close()
    except Exception as error:
        code = 1
        print(run.redact(error)[:1000])
        if not run.results:
            run.results.append({'name': 'HTTPS workbench prerequisites', 'status': 'FAIL', 'seconds': 0})
    finally:
        run.report()
    return code


if __name__ == '__main__':
    raise SystemExit(main())
