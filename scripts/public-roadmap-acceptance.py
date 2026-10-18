"""Targeted 0.1.15 HTTPS checks with existing invited synthetic USER accounts.

No administrator credential, paid AI, real mail, production SQL or real plan edits.
"""
import argparse
import importlib.util
import json
import os
from datetime import date, timedelta
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('public_web', Path(__file__).with_name('public-web-acceptance.py'))
web = importlib.util.module_from_spec(spec)
spec.loader.exec_module(web)
os.environ.setdefault('PLAYWRIGHT_BROWSERS_PATH', str(ROOT / '.desktop-tools/playwright'))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--profile', choices=['all', 'desktop', 'mobile-web'], default='all')
    parser.add_argument('--section', choices=['all', 'feedback', 'data', 'organize', 'help'], default='all')
    args = parser.parse_args()
    users = json.loads((web.PRIVATE / 'accounts.json').read_text(encoding='utf-8'))
    assert set(users) == set(web.EMAILS) and all(v.get('registered') for v in users.values())
    run = web.Run()
    run.secrets.extend(v['password'] for v in users.values())
    code = 0
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            try:
                for profile, viewport in web.PROFILES:
                    if args.profile != 'all' and args.profile != profile:
                        continue
                    context = browser.new_context(viewport=viewport, locale='zh-CN', timezone_id='Asia/Shanghai', service_workers='block', accept_downloads=True)
                    other = browser.new_context(locale='zh-CN', timezone_id='Asia/Shanghai')
                    context.set_default_timeout(20000)
                    try:
                        page, token = web.login(context, web.EMAILS[0], users[web.EMAILS[0]])
                        _, outsider = web.login(other, web.EMAILS[1], users[web.EMAILS[1]])
                        run.secrets.extend([token, outsider])
                        api = lambda method, path, data=None, status=200: web.api(context, token, method, path, data, status)
                        assert api('GET', '/health')['version'] == '0.1.15'
                        config = api('GET', '/auth/config')
                        assert not config['registrationEnabled'] and config['invitationEnabled'] and config['passwordResetEnabled']
                        cookies = context.cookies()
                        assert any(c['name'].startswith('plan_refresh') and c['secure'] and c['httpOnly'] for c in cookies)
                        prefix = 'roadmap-public-' + run.tag + '-' + profile
                        today = date.fromisoformat(api('GET', '/dashboard/today')['date'])
                        shift = lambda n: (today + timedelta(days=n)).isoformat()
                        plan = api('POST', '/plans', dict(name=prefix, startDate=shift(0), endDate=shift(30), restDays=[7]), 201)
                        tasks = api('POST', '/plans/' + plan['id'] + '/tasks/recurring', dict(task=dict(title=prefix+'-repeat', date=shift(1), plannedMinutes=20, category='STUDY', acceptance=[], required=True, sortOrder=0), mode='DAILY', until=shift(3)), 201)
                        todo = api('POST', '/todos', dict(title=prefix+'-backup', dueDate=shift(0)), 201)

                        def feedback():
                            page.goto(web.ORIGIN + '/feedback')
                            expect(page.get_by_role('button', name='管理员处理', exact=True)).to_have_count(0)
                            for label, value in [('问题摘要', prefix), ('操作步骤', '合成上线验收'), ('期望的结果', '确认后私有发送'), ('实际的结果', '无真实用户内容')]:
                                page.get_by_label(label, exact=True).fill(value)
                            page.get_by_role('button', name='生成并查看诊断摘要', exact=True).click()
                            diagnostic = json.loads(page.get_by_test_id('diagnostic-preview').inner_text())
                            assert diagnostic['platform'] == 'WEB' and diagnostic['clientVersion'] == '0.1.15'
                            assert not set(diagnostic).intersection({'email', 'accessToken', 'serverUrl', 'title', 'deviceId', 'logs'})
                            page.get_by_role('button', name='预览待发送内容', exact=True).click()
                            expect(page.get_by_role('button', name='确认发送反馈', exact=True)).to_be_disabled()
                            page.get_by_label('我已核对内容不含敏感信息，同意将以上内容发送给管理员。', exact=True).check()
                            with page.expect_response(lambda r: r.request.method == 'POST' and r.url.endswith('/api/feedback')) as submitted:
                                page.get_by_role('button', name='确认发送反馈', exact=True).click()
                            assert submitted.value.status == 201
                            detail = submitted.value.json()
                            expect(page.get_by_text('反馈已发送。可在下方查看处理状态与管理员回复。', exact=True)).to_be_visible()
                            ticket_id = detail.get('ticket', detail)['id']
                            web.api(other, outsider, 'GET', '/feedback/' + ticket_id, status=404)
                            response = context.request.get(web.ORIGIN+'/api/feedback/'+ticket_id, headers={'Authorization':'Bearer '+token})
                            assert 'no-store' in response.headers.get('cache-control', '')
                        if args.section in ['all', 'feedback']:
                            run.check(profile + ': 新版反馈同意、脱敏和普通账号隔离', feedback)

                        def personal_data():
                            backup = api('GET', '/data/backup')
                            # Only this run's one synthetic Todo is submitted, not the account's full history.
                            sample = {**backup, 'records': {k: [r for r in rows if r.get('id') == todo['id']] if k == 'todos' else [] for k, rows in backup['records'].items()}}
                            before = api('GET', '/data/restores')['totalElements']
                            page.goto(web.ORIGIN + '/exports')
                            page.get_by_label('选择私有 JSON 备份', exact=True).set_input_files({'name':'synthetic-roadmap.json', 'mimeType':'application/json', 'buffer':json.dumps(sample).encode()})
                            expect(page.get_by_role('button', name='只读预检，不恢复数据', exact=True)).to_be_disabled()
                            page.get_by_label('我确认这是获准使用的私有备份，同意将文件发送到当前登录服务器进行只读预检。', exact=True).check()
                            page.get_by_role('button', name='只读预检，不恢复数据', exact=True).click()
                            expect(page.get_by_role('heading', name='预检通过 · 1 条记录', exact=True)).to_be_visible()
                            consent = page.get_by_label('我已核对以上数量和限制，确认恢复为新副本。周复盘保存在下方只读副本中，不覆盖当前周。', exact=True)
                            for _ in range(2):
                                consent.check()
                                with page.expect_response(lambda r: r.url.endswith('/api/data/restore') and r.request.method=='POST') as response:
                                    page.get_by_role('button', name='确认恢复为新副本', exact=True).click()
                                assert response.value.status == 200
                            assert api('GET', '/data/restores')['totalElements'] == before + 1
                            with page.expect_download() as downloaded:
                                page.get_by_role('button', name='导出今日待办 CSV', exact=True).click()
                            assert downloaded.value.suggested_filename.endswith('.csv')
                            content = Path(downloaded.value.path()).read_text(encoding='utf-8-sig')
                            assert todo['title'] in content
                            page.screenshot(path=str(run.out/(profile+'-data.png')), full_page=True)
                        if args.section in ['all', 'data']:
                            run.check(profile + ': 新版私有预检、恢复防重及待办 CSV 下载', personal_data)

                        def orchestration():
                            page.goto(web.ORIGIN + '/organize')
                            page.get_by_label('来源', exact=True).select_option(plan['id'])
                            page.get_by_role('button', name='预览结构复制', exact=True).click()
                            expect(page.get_by_role('button', name='确认创建副本', exact=True)).to_be_disabled()
                            page.get_by_label('我已核对预览，确认应用以上变更。', exact=True).check()
                            page.get_by_role('button', name='确认创建副本', exact=True).click()
                            expect(page.get_by_role('status')).to_contain_text('结构副本已创建')
                            page.get_by_role('button', name='未来批量调整', exact=True).click()
                            page.get_by_label('选择计划', exact=True).select_option(plan['id'])
                            page.get_by_label('重复批次', exact=True).select_option(tasks[0]['recurrenceId'])
                            page.get_by_label('统一平移天数', exact=True).fill('2')
                            page.get_by_role('button', name='预览批量差异', exact=True).click()
                            expect(page.locator('.difference')).to_have_count(3)
                            api('POST', '/tasks/'+tasks[0]['id']+'/manual-time', dict(seconds=5, version=tasks[0]['version']))
                            page.get_by_label('我已核对预览，确认应用以上变更。', exact=True).check()
                            page.get_by_role('button', name='确认应用批量调整', exact=True).click()
                            expect(page.get_by_role('alert')).to_contain_text('预览已过期')
                            assert all(t['date'] == original['date'] for t, original in zip(sorted(api('GET','/plans/'+plan['id']+'/tasks'), key=lambda x:x['id']), sorted(tasks,key=lambda x:x['id'])))
                            web.api(other, outsider, 'GET', '/plans/'+plan['id']+'/tasks', status=404)
                        if args.section in ['all', 'organize']:
                            run.check(profile + ': 结构复制、未来批量真实409和历史保护', orchestration)

                        def help_and_gates():
                            page.goto(web.ORIGIN + '/offline')
                            page.get_by_label('预加载计划', exact=True).select_option(plan['id'])
                            page.get_by_label('日程起始日期', exact=True).fill(shift(0))
                            page.get_by_role('button', name='预加载这 7 天', exact=True).click()
                            expect(page.get_by_role('status')).to_contain_text('已核对')
                            page.goto(web.ORIGIN + '/help')
                            expect(page.get_by_role('heading', name='帮助与版本', exact=True)).to_be_visible()
                            expect(page.get_by_text('0.1.15', exact=True).first).to_be_visible()
                            for path in ['/admin/feedback', '/admin/operations/status', '/admin/governance/members']:
                                api('GET', path, status=403)
                            manifest = context.request.get(web.ORIGIN+'/downloads/manifest.json').json()
                            assert manifest['android'] is None
                            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
                            page.screenshot(path=str(run.out/(profile+'-help.png')), full_page=True)
                        if args.section in ['all', 'help']:
                            run.check(profile + ': 七天预加载、帮助版本与管理员边界', help_and_gates)
                    finally:
                        for ctx in [context, other]:
                            try:
                                ctx.request.post(web.ORIGIN + '/api/auth/logout')
                            finally:
                                ctx.close()
            finally:
                browser.close()
    except Exception as error:
        print(run.redact(error)[:1000], flush=True)
        if not run.results or run.results[-1]['status'] != 'FAIL':
            run.results.append(dict(name='Roadmap HTTPS prerequisites', status='FAIL', seconds=0, error=type(error).__name__))
        code = 1
    finally:
        run.report()
    return code


if __name__ == '__main__':
    raise SystemExit(main())
