"""0.1.14 production HTTPS daily scope; invited synthetic users only, headless.

No administrator login, paid requests, mail, HAR or credential-bearing traces.
"""
import importlib.util
import json
import os
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('public_web', Path(__file__).with_name('public-web-acceptance.py'))
web = importlib.util.module_from_spec(spec)
spec.loader.exec_module(web)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH', str(ROOT / '.desktop-tools/playwright'))


def main():
    users = json.loads((web.PRIVATE / 'accounts.json').read_text(encoding='utf-8'))
    assert set(users) == set(web.EMAILS) and all(v.get('registered') for v in users.values())
    run = web.Run()
    run.secrets.extend(v['password'] for v in users.values())
    code = 0
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            try:
                assert browser.new_context().request.get(web.ORIGIN + '/api/health').json()['version'] == '0.1.14'
                for profile, viewport in web.PROFILES:
                    context = browser.new_context(viewport=viewport, locale='zh-CN', timezone_id='Asia/Shanghai', service_workers='block')
                    other = browser.new_context(locale='zh-CN', timezone_id='Asia/Shanghai')
                    context.set_default_timeout(20000)
                    try:
                        page, token = web.login(context, web.EMAILS[0], users[web.EMAILS[0]])
                        _, outsider = web.login(other, web.EMAILS[1], users[web.EMAILS[1]])
                        run.secrets.extend([token, outsider])
                        api = lambda method, path, data=None, status=200: web.api(context, token, method, path, data, status)
                        today = (datetime.now(timezone.utc) + timedelta(hours=8)).date()
                        monday = today - timedelta(days=today.weekday())
                        prefix = 'daily-public-' + run.tag + '-' + profile
                        entries = {}
                        for offset, bucket in [(0, 'TODAY'), (-1, 'OVERDUE'), (1, 'UPCOMING')]:
                            title = prefix + '-' + bucket.lower()
                            entries[bucket] = api('POST', '/todos', {'title': title, 'dueDate': (today + timedelta(days=offset)).isoformat()}, 201)

                        def overview():
                            page.goto(web.ORIGIN + '/overview')
                            page.get_by_label('查看日期', exact=True).fill(today.isoformat())
                            with page.expect_response(lambda r: '/api/daily/items?' in r.url and 'mode=TODO' in r.url) as filtered:
                                page.get_by_role('combobox', name='模式', exact=True).select_option('TODO')
                            assert filtered.value.status == 200
                            for bucket, label in [('OVERDUE', '已逾期'), ('UPCOMING', '即将到期'), ('TODAY', '今日待完成')]:
                                with page.expect_response(lambda r, b=bucket: '/api/daily/items?' in r.url and 'bucket=' + b in r.url and 'mode=TODO' in r.url) as loaded:
                                    page.get_by_role('button', name=re.compile(label)).click()
                                assert loaded.value.status == 200
                                result = loaded.value.json()
                                # Stable pagination can put the dedicated fixture on a later page.
                                for _ in range(100):
                                    if any(item['id'] == entries[bucket]['id'] for item in result['content']):
                                        break
                                    if result['page'] + 1 >= result['totalPages']:
                                        raise AssertionError('Dedicated dated item missing')
                                    expect(page.get_by_role('region', name='汇总事项列表')).to_have_attribute('aria-busy', 'false')
                                    with page.expect_response(lambda r: '/api/daily/items?' in r.url) as advanced:
                                        page.get_by_role('button', name='下一页', exact=True).click()
                                    assert advanced.value.status == 200
                                    result = advanced.value.json()
                                expect(page.get_by_role('heading', name=entries[bucket]['title'], exact=True)).to_be_visible()
                            response = context.request.get(web.ORIGIN + '/api/daily/summary?date=' + today.isoformat(), headers={'Authorization': 'Bearer ' + token})
                            assert response.status == 200 and 'no-store' in response.headers.get('cache-control', '')
                            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
                        run.check(profile + ': 真实 HTTPS 三类事项、模式筛选及私有响应', overview)

                        def reminders():
                            initial = api('GET', '/daily/reminders')
                            settings = {k: initial[k] for k in ['enabled', 'includePlans', 'includeTodos', 'showOverdue', 'lookAheadDays', 'version']}
                            page.goto(web.ORIGIN + '/overview')
                            enabled = page.get_by_label('启用站内提醒', exact=True)
                            enabled.check()
                            page.get_by_role('button', name='保存提醒设置', exact=True).click()
                            expect(page.get_by_text('站内提醒设置已保存，其他设备联网读取后生效。', exact=True)).to_be_visible()
                            page.reload()
                            expect(enabled).to_be_checked()
                            saved = api('GET', '/daily/reminders')
                            assert saved['enabled'] is True
                            settings['version'] = saved['version']
                            api('PUT', '/daily/reminders', settings)
                            page.reload()
                            expect(enabled).to_be_checked() if settings['enabled'] else expect(enabled).not_to_be_checked()
                        run.check(profile + ': 站内提醒主动开启、重开读取及恢复原偏好', reminders)

                        def weekly():
                            path = '/weekly/reviews/' + monday.isoformat()
                            initial = api('GET', path)
                            outsider_before = web.api(other, outsider, 'GET', path)
                            page.goto(web.ORIGIN + '/weekly-review')
                            expect(page.get_by_label('本周收获', exact=True)).to_be_visible()
                            marker = prefix + '-weekly'
                            page.get_by_label('本周收获', exact=True).fill(marker)
                            page.get_by_role('button', name='保存本周复盘', exact=True).click()
                            expect(page.get_by_text('本周复盘已保存，其他设备联网后可读取。', exact=True)).to_be_visible()
                            page.reload()
                            expect(page.get_by_label('本周收获', exact=True)).to_have_value(marker)
                            saved = api('GET', path)
                            assert web.api(other, outsider, 'GET', path) == outsider_before
                            stale = {k: initial[k] for k in ['wins', 'blockers', 'nextFocus', 'version']}
                            api('PUT', path, stale, 409)
                            stale['version'] = saved['version']
                            api('PUT', path, stale)
                            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
                            page.screenshot(path=str(run.out / (profile + '-weekly.png')), full_page=True)
                        run.check(profile + ': 周复盘保存重开、真实冲突、普通账号隔离并恢复旧笔记', weekly)
                    finally:
                        for ctx in (context, other):
                            try:
                                ctx.request.post(web.ORIGIN + '/api/auth/logout')
                            finally:
                                ctx.close()
            finally:
                browser.close()
    except Exception as error:
        print(run.redact(error), flush=True)
        if not run.results or run.results[-1]['status'] != 'FAIL':
            run.results.append({'name': 'Production daily prerequisites', 'status': 'FAIL', 'seconds': 0, 'error': type(error).__name__})
        code = 1
    finally:
        run.report()
    return code


if __name__ == '__main__':
    raise SystemExit(main())
