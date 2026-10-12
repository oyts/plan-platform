"""Real HTTPS browser acceptance with invited USER accounts and synthetic records.

No admin password, auth bypass, production SQL, HAR or credential-bearing traces.
Invitations and generated test credentials stay in the ignored, restricted directory.
Browser checks use the shipped website; API calls only seed/read the same test owner.
"""
from __future__ import annotations
import argparse
import html
import json
import re
import secrets
import sys
import time
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from playwright.sync_api import sync_playwright, expect
sys.stdout.reconfigure(encoding='utf-8')

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / '.desktop-tools/public-web/private'
ORIGIN = 'https://plan.zijun701.cn'
EMAILS = ['web-acceptance-a-20261012@example.test', 'web-acceptance-b-20261012@example.test']
PROFILES = [('desktop', {'width': 1280, 'height': 900}), ('mobile-web', {'width': 390, 'height': 844})]


class Run:
    def __init__(self):
        self.tag = datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S') + '-' + secrets.token_hex(3)
        self.out = ROOT / 'acceptance-results/public-web' / self.tag
        self.out.mkdir(parents=True, exist_ok=False)
        self.results = []
        self.secrets = []

    def redact(self, value):
        text = str(value)
        for secret in self.secrets:
            text = text.replace(secret, '[REDACTED]')
        return re.sub(r'([?&](?:invite|token)=)[^&\s]+', r'\1[REDACTED]', text)

    def check(self, title, action):
        started = time.monotonic()
        try:
            action()
            result = {'name': title, 'status': 'PASS', 'seconds': round(time.monotonic()-started, 2)}
        except Exception as error:
            result = {'name': title, 'status': 'FAIL', 'seconds': round(time.monotonic()-started, 2), 'error': self.redact(error)}
        self.results.append(result)
        print(f"[{result['status']}] {title}", flush=True)
        if result['status'] == 'FAIL':
            print(result['error'][:1000], flush=True)
            raise RuntimeError('Browser acceptance stopped at the first real failure.') from None

    def report(self):
        failed = sum(row['status'] == 'FAIL' for row in self.results)
        record = {'origin': ORIGIN, 'tests': len(self.results), 'failed': failed, 'results': self.results,
                  'scope': 'Real HTTPS website; synthetic ordinary accounts; mobile viewport is not APK verification.'}
        (self.out / 'report.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
        rows = ''.join(f"<tr><td>{html.escape(row['name'])}</td><td>{row['status']}</td><td>{row['seconds']}</td></tr>" for row in self.results)
        content = '<!doctype html><meta charset="utf-8"><title>正式网站自动验收</title><h1>计划舱正式网站自动验收</h1>'
        content += f'<p>{len(self.results)-failed} passed / {failed} failed · {ORIGIN}</p>'
        content += '<p>真实 HTTPS、普通验收账号。手机视口结果不代表 APK 真机通过。邀请、密码和会话不进入报告或 Git。</p>'
        content += f'<table border="1" cellpadding="8"><tr><th>测试</th><th>结果</th><th>秒</th></tr>{rows}</table>'
        (self.out / 'report.html').write_text(content, encoding='utf-8')
        latest = self.out.parent / 'latest.html'
        latest.write_text(content, encoding='utf-8')
        print(f'Report: {latest}', flush=True)


def public_checks(browser, run, profiles=PROFILES):
    for name, viewport in profiles:
        def check(name=name, viewport=viewport):
            context = browser.new_context(viewport=viewport, locale='zh-CN', timezone_id='Asia/Shanghai', service_workers='block')
            try:
                page = context.new_page()
                config = context.request.get(ORIGIN + '/api/auth/config').json()
                assert config['serverVersion'] == '0.1.4' and config['apiVersion'] == 1
                assert config['registrationEnabled'] is False and config['todoEnabled'] is True and config['planRestDaysEnabled'] is True
                assert context.request.get(ORIGIN + '/api/health').json()['service'] == 'plan-platform-api'
                errors = []
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.goto(ORIGIN + '/welcome', wait_until='networkidle')
                expect(page.get_by_role('heading', level=1)).to_contain_text('把今天安排好')
                for label in ['周期计划', '目标路线', '今日待办']:
                    tab = page.get_by_role('tab', name=label, exact=True)
                    tab.click()
                    expect(tab).to_have_attribute('aria-selected', 'true')
                expect(page.get_by_role('link', name='鄂ICP备2026050813号-1')).to_have_attribute('href', 'https://beian.miit.gov.cn/')
                expect(page.get_by_role('button', name='正式安装包尚未发布', exact=True)).to_be_disabled()
                page.get_by_role('button', name='查看 Android 发布状态').click()
                expect(page.get_by_role('dialog')).to_contain_text('正式签名包尚未发布')
                page.get_by_role('button', name='关闭面板').click()
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
                page.screenshot(path=str(run.out / f'{name}-homepage.png'), full_page=True)
                page.get_by_role('button', name='开始使用网页版', exact=True).click()
                expect(page).to_have_url(ORIGIN + '/login')
                expect(page.get_by_role('button', name='登录', exact=True)).to_be_enabled()
                expect(page.get_by_text('当前未开放公开注册。', exact=True)).to_be_visible()
                assert not errors, 'Uncaught public page error'
            finally:
                context.close()
        run.check(f'{name}: 官网、三模式预览、备案、下载禁用和 HTTPS 登录连接', check)


def accounts(browser, run):
    file = PRIVATE / 'accounts.json'
    entries = None
    if file.exists():
        entries = json.loads(file.read_text(encoding='utf-8'))
        assert set(entries) == set(EMAILS), 'Saved test account emails do not match the dedicated scope.'
        for data in entries.values():
            run.secrets.append(data['password'])
        if all(data.get('registered') for data in entries.values()):
            return entries
    invitations = PRIVATE / 'invitations.txt'
    if not invitations.exists():
        raise RuntimeError('Invitation file missing. Save two links privately; do not paste them into chat.')
    links = {}
    for line in invitations.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip():
            continue
        parsed = urlparse(line.strip())
        assert parsed.scheme == 'https' and parsed.netloc == 'plan.zijun701.cn' and parsed.path == '/register', 'Unexpected invitation origin or path.'
        values = parse_qs(parsed.query)
        assert len(values.get('email', [])) == 1 and len(values.get('invite', [])) == 1, 'Malformed invitation query.'
        email, token = values['email'][0], values['invite'][0]
        assert email in EMAILS and email not in links and token, 'Expected two distinct dedicated test emails.'
        links[email] = line.strip()
        run.secrets.append(token)
    assert set(links) == set(EMAILS), 'Need exactly two dedicated test invitations.'
    # Persist generated credentials before registration, so a partial run does
    # not lose the test password or need the administrator to resend a used link.
    if entries is None:
        entries = {}
        for index, email in enumerate(EMAILS):
            entries[email] = {'password': 'WebAcceptanceA1-' + secrets.token_urlsafe(24), 'name': f'网页自动验收{index+1}', 'registered': False}
            run.secrets.append(entries[email]['password'])
    file.write_text(json.dumps(entries, ensure_ascii=False), encoding='utf-8')
    for email, data in entries.items():
        if data.get('registered'):
            continue
        context = browser.new_context(locale='zh-CN', timezone_id='Asia/Shanghai')
        try:
            page = context.new_page()
            page.goto(links[email])
            page.get_by_label('显示名称', exact=True).fill(data['name'])
            expect(page.get_by_label('邮箱', exact=True)).to_have_value(email)
            page.get_by_label('密码', exact=True).fill(data['password'])
            with page.expect_response(lambda response: response.url == ORIGIN+'/api/auth/register' and response.request.method == 'POST') as response:
                page.get_by_role('button', name='创建账号', exact=True).click()
            if response.value.status == 409:
                # Recover a registration that committed before the prior run
                # could save its local marker. Never reset or replace a password.
                page.close()
                page, _ = login(context, email, data)
            else:
                assert response.value.status == 201, f'Invitation registration returned HTTP {response.value.status}'
                assert response.value.json()['user']['role'] == 'USER', 'Test registration must never create administrators.'
                expect(page.locator('.site-header')).to_be_visible()
            data['registered'] = True
            file.write_text(json.dumps(entries, ensure_ascii=False), encoding='utf-8')
        finally:
            context.close()
    return entries


def login(context, email, data):
    page = context.new_page()
    page.goto(ORIGIN + '/login')
    page.get_by_label('邮箱', exact=True).fill(email)
    page.get_by_label('密码', exact=True).fill(data['password'])
    with page.expect_response(lambda response: response.url == ORIGIN+'/api/auth/login' and response.request.method == 'POST') as response:
        page.get_by_role('button', name='登录', exact=True).click()
    assert response.value.status == 200, f'Test login returned HTTP {response.value.status}'
    body = response.value.json()
    assert body['user']['role'] == 'USER', 'Only dedicated normal accounts may be tested.'
    expect(page.locator('.site-header')).to_be_visible()
    return page, body['accessToken']


def api(context, token, method, path, data=None, status=200):
    response = context.request.fetch(ORIGIN+'/api'+path, method=method, data=data, headers={'Authorization': 'Bearer '+token})
    assert response.status == status, f'{method} {path.split("?")[0]} returned HTTP {response.status}, expected {status}'
    return response.json() if status < 400 and status != 204 else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--chromium', help='Optional existing Chromium executable; otherwise use Playwright Chromium.')
    parser.add_argument('--public-only', action='store_true')
    parser.add_argument('--profile', choices=['all', 'desktop', 'mobile-web'], default='all')
    args = parser.parse_args()
    profiles = [item for item in PROFILES if args.profile == 'all' or item[0] == args.profile]
    run = Run()
    exit_code = 0
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=True, **({'executable_path': args.chromium} if args.chromium else {}))
            try:
                public_checks(browser, run, profiles)
                if not args.public_only:
                    users = None
                    def register():
                        nonlocal users
                        users = accounts(browser, run)
                    run.check('邀请制注册或复用两个普通验收账号，不使用管理员密码', register)
                    for name, viewport in profiles:
                        business_checks(browser, run, users, name, viewport)
            finally:
                browser.close()
    except Exception as error:
        if not run.results or run.results[-1]['status'] != 'FAIL':
            run.results.append({'name':'验收前置条件或会话准备', 'status':'FAIL', 'seconds':0, 'error':run.redact(error)})
        print(run.redact(error), flush=True)
        exit_code = 1
    finally:
        run.report()
    return exit_code


def business_checks(browser, run, users, profile, viewport):
    context = browser.new_context(viewport=viewport, locale='zh-CN', timezone_id='Asia/Shanghai', service_workers='block')
    other = browser.new_context(locale='zh-CN', timezone_id='Asia/Shanghai', service_workers='block')
    context.set_default_timeout(15000)
    state = {}
    prefix = f'自动验收-{run.tag}-{profile}'
    try:
        page, token = login(context, EMAILS[0], users[EMAILS[0]])
        other_page, outsider = login(other, EMAILS[1], users[EMAILS[1]])
        run.secrets.extend([token, outsider])
        page.on('dialog', lambda dialog: dialog.accept())
        today = date.fromisoformat(api(context, token, 'GET', '/dashboard/today')['date'])
        saturday = today + timedelta(days=(5-today.weekday()) % 7)
        sunday = saturday + timedelta(days=1)

        def shot(name):
            page.screenshot(path=str(run.out / f'{profile}-{name}.png'), full_page=True)

        def plans():
            return api(context, token, 'GET', '/plans')

        def lines():
            return api(context, token, 'GET', '/mainlines')

        def schedule():
            # The page first loads plans and selects a default, which itself
            # replaces the route. Wait for that initial navigation to settle
            # before choosing our own plan rather than racing the mounted hook.
            page.goto(ORIGIN+'/schedule', wait_until='networkidle')
            page.get_by_role('combobox', name=re.compile(r'^当前计划')).select_option(state['plan']['id'])
            expect(page).to_have_url(ORIGIN+'/schedule?plan='+state['plan']['id'])
            page.wait_for_load_state('networkidle')
            expect(page.locator('.rail-day')).to_have_count(7)

        def day(target):
            marker = target.strftime('%m.%d')
            expect(page.locator('.rail-day')).to_have_count(7)
            for _ in range(3):
                element = page.locator('.rail-day').filter(has_text=marker)
                if element.count():
                    return element
                previous = page.locator('.rail-day header strong').first.inner_text()
                page.get_by_role('button', name='下一周', exact=True).click()
                expect(page.locator('.rail-day header strong').first).not_to_have_text(previous)
            raise AssertionError('Requested test date is not visible in the date rail.')

        def initial_session():
            cookies = [cookie for cookie in context.cookies() if cookie['name'].startswith('plan_refresh')]
            assert cookies and all(cookie['secure'] and cookie['httpOnly'] for cookie in cookies), 'Refresh cookie security flags missing.'
            page.reload()
            expect(page.locator('.site-header')).to_be_visible()
            api(context, token, 'GET', '/admin/invitations', status=403)
        run.check(f'{profile}: 普通账号登录、Secure HttpOnly Cookie、刷新保持登录及权限', initial_session)

        def create_plan():
            page.get_by_label('工作模式').select_option('plan')
            page.goto(ORIGIN+'/plans')
            expect(page.get_by_role('checkbox', name='星期六', exact=True)).not_to_be_checked()
            expect(page.get_by_role('checkbox', name='星期日', exact=True)).not_to_be_checked()
            page.get_by_label('计划名称', exact=True).fill(prefix+'-周期计划')
            page.get_by_label('说明', exact=True).fill('独立普通账号的合成网页验收，不涉及管理员数据')
            page.get_by_label('开始日期', exact=True).fill(today.isoformat())
            page.get_by_label('结束日期', exact=True).fill((today+timedelta(days=13)).isoformat())
            page.get_by_role('checkbox', name='星期日', exact=True).check()
            page.get_by_role('button', name='保存计划', exact=True).click()
            expect(page.get_by_role('heading', name=prefix+'-周期计划', exact=True)).to_be_visible()
            state['plan'] = next(row for row in plans() if row['name'] == prefix+'-周期计划')
            assert state['plan']['restDays'] == [7]
        run.check(f'{profile}: 创建周期计划，仅星期日休息、不默认绑定周末', create_plan)

        def edit_task():
            title = prefix+'-周六任务'
            page.get_by_role('button', name='添加任务', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_label('任务标题', exact=True).fill(title)
            dialog.get_by_label('日期', exact=True).fill(saturday.isoformat())
            dialog.get_by_label('开始', exact=True).fill('09:00')
            dialog.get_by_label('结束', exact=True).fill('09:20')
            dialog.get_by_label('计划分钟', exact=True).fill('20')
            dialog.get_by_role('button', name='保存任务', exact=True).click()
            expect(dialog).not_to_be_visible()
            row = page.locator('.task-row').filter(has_text=title)
            row.get_by_role('button', name='编辑', exact=True).click()
            dialog = page.get_by_role('dialog')
            expect(dialog.get_by_label('任务标题', exact=True)).to_have_value(title)
            expect(dialog.get_by_label('计划分钟', exact=True)).to_have_value('20')
            dialog.get_by_label('任务标题', exact=True).fill(title+'-已修改')
            dialog.get_by_role('button', name='保存修改', exact=True).click()
            expect(dialog).not_to_be_visible()
            expect(page.locator('.task-row').filter(has_text=title+'-已修改')).to_be_visible()
            state['task'] = next(row for row in api(context, token, 'GET', f"/plans/{state['plan']['id']}/tasks") if row['title'] == title+'-已修改')
            shot('plan-task-editor-result')
        run.check(f'{profile}: 任务新增、侧边面板回显、修改及保存', edit_task)

        def rest_days():
            schedule()
            expect(day(saturday).locator('header small')).to_have_text('计划日')
            expect(day(saturday)).to_contain_text(state['task']['title'])
            expect(day(sunday).locator('header small')).to_contain_text('休息日')
            page.goto(ORIGIN+f"/plans/{state['plan']['id']}")
            page.get_by_role('button', name='编辑计划设置', exact=True).click()
            form = page.get_by_role('form', name='编辑计划设置', exact=True)
            form.get_by_role('checkbox', name='星期日', exact=True).uncheck()
            form.get_by_role('checkbox', name='星期六', exact=True).check()
            form.get_by_role('button', name='保存计划设置', exact=True).click()
            expect(form).not_to_be_visible()
            schedule()
            expect(day(saturday).locator('header small')).to_contain_text('休息日')
            expect(day(saturday)).to_contain_text(state['task']['title'])
            expect(day(sunday).locator('header small')).to_have_text('计划日')
            page.reload()
            expect(page.locator('.site-header')).to_be_visible()
            assert next(row for row in plans() if row['id']==state['plan']['id'])['restDays'] == [6]
            shot('schedule-rest-days')
        run.check(f'{profile}: 休息日二次编辑、准确日程标记、刷新与原任务保留', rest_days)

        def pagination_and_recovery():
            plan_id = state['plan']['id']
            for i in range(11):
                api(context, token, 'POST', f'/plans/{plan_id}/tasks', {'title':prefix+f'-分页任务-{i:02d}', 'date':saturday.isoformat(), 'category':'CUSTOM', 'plannedMinutes':15, 'acceptance':[], 'required':True, 'sortOrder':i+1}, status=201)
            page.goto(ORIGIN+f'/plans/{plan_id}')
            expect(page.locator('.task-row')).to_have_count(10)
            page.get_by_role('button', name='下一页', exact=True).click()
            expect(page.locator('.task-row')).to_have_count(2)
            page.get_by_label('搜索任务', exact=True).fill(state['task']['title'])
            row = page.locator('.task-row').filter(has_text=state['task']['title'])
            row.get_by_role('button', name='删除', exact=True).click()
            expect(row).to_have_count(0)
            page.goto(ORIGIN+'/records')
            trash = page.locator('.trash-row').filter(has_text=state['task']['title'])
            trash.get_by_role('button', name='恢复', exact=True).click()
            expect(trash).to_have_count(0)
            page.goto(ORIGIN+f'/plans/{plan_id}')
            page.get_by_label('搜索任务', exact=True).fill(state['task']['title'])
            expect(page.locator('.task-row')).to_have_count(1)
        run.check(f'{profile}: 任务 10/2 分页、搜索、软删除及恢复', pagination_and_recovery)

        def route_stages():
            state['before_route'] = api(context, token, 'GET', '/dashboard/today')
            page.get_by_label('工作模式').select_option('route')
            page.goto(ORIGIN+'/mainlines', wait_until='networkidle')
            if not page.get_by_label('路线名称', exact=True).is_visible():
                page.locator('.route-create summary').click()
            page.get_by_label('路线名称', exact=True).fill(prefix+'-目标路线')
            page.get_by_role('button', name='新建路线', exact=True).click()
            expect(page.get_by_role('heading', name=prefix+'-目标路线', exact=True)).to_be_visible()
            state['line'] = next(row for row in lines() if row['name']==prefix+'-目标路线')
            assert state['line']['executionMode'] == 'FLEXIBLE'
            page.get_by_role('button', name='管理阶段', exact=True).click()
            dialog = page.get_by_role('dialog')
            dialog.get_by_label('阶段名称', exact=True).fill('验收基础阶段')
            dialog.get_by_role('button', name='添加阶段', exact=True).click()
            expect(dialog.locator('.route-stage-row')).to_contain_text('验收基础阶段')
            dialog.get_by_role('button', name='关闭面板', exact=True).click()
            state['stage'] = next(row for row in lines() if row['id']==state['line']['id'])['stages'][0]
            for i, title in enumerate(['认识吉他','和弦练习']):
                page.get_by_role('button', name='添加步骤', exact=True).click()
                dialog = page.get_by_role('dialog')
                dialog.get_by_label('步骤标题', exact=True).fill(prefix+'-'+title)
                dialog.get_by_role('combobox', name=re.compile(r'^所属阶段')).select_option(state['stage']['id'])
                if i:
                    dialog.get_by_role('combobox', name=re.compile(r'^前置条件')).select_option('EXPLICIT')
                    dialog.get_by_label(prefix+'-认识吉他', exact=True).check()
                dialog.get_by_role('button', name='保存步骤', exact=True).click()
                expect(dialog).not_to_be_visible()
            second = page.locator('.mainline-step').filter(has=page.get_by_role('heading', name=prefix+'-和弦练习', exact=True))
            expect(second.get_by_role('button', name='开始', exact=True)).to_be_disabled()
            second.get_by_role('button', name='编辑步骤', exact=True).click()
            dialog = page.get_by_role('dialog')
            expect(dialog.get_by_label('步骤标题', exact=True)).to_have_value(prefix+'-和弦练习')
            expect(dialog.get_by_label(prefix+'-认识吉他', exact=True)).to_be_checked()
            dialog.get_by_label('步骤标题', exact=True).fill(prefix+'-和弦练习-修订')
            dialog.get_by_role('button', name='保存步骤', exact=True).click()
            expect(dialog).not_to_be_visible()
            page.locator('.mainline-step').filter(has=page.get_by_role('heading', name=prefix+'-认识吉他', exact=True)).get_by_role('button', name='完成', exact=True).click()
            expect(page.locator('.mainline-step').filter(has=page.get_by_role('heading', name=prefix+'-和弦练习-修订', exact=True)).get_by_role('button', name='开始', exact=True)).to_be_enabled()
            after = api(context, token, 'GET', '/dashboard/today')
            for key in ['total','completed','plannedMinutes']:
                assert after[key] == state['before_route'][key], 'Route must not affect the cycle-plan totals.'
            shot('route-stages')
        run.check(f'{profile}: 自由目标路线、阶段、新步骤、编辑回显及指定前置解锁', route_stages)

        def route_paging():
            for i in range(12):
                api(context, token, 'POST', f"/mainlines/{state['line']['id']}/items", {'title':prefix+f'-长步骤-{i:02d}', 'plannedMinutes':15, 'sortOrder':20+i, 'acceptance':[], 'routing':{'stageId':state['stage']['id'], 'dependencyMode':'NONE', 'prerequisiteIds':[]}}, status=201)
            page.reload()
            page.get_by_label('搜索步骤', exact=True).fill(prefix+'-长步骤')
            expect(page.locator('.mainline-step')).to_have_count(10)
            page.get_by_role('button', name='下一页', exact=True).click()
            expect(page.locator('.mainline-step')).to_have_count(2)
            page.get_by_role('button', name='折叠全部', exact=True).click()
            expect(page.locator('.mainline-step')).to_have_count(0)
            page.get_by_role('button', name='展开全部', exact=True).click()
            page.get_by_label('搜索步骤', exact=True).fill(prefix+'-长步骤-11')
            expect(page.locator('.mainline-step')).to_have_count(1)
        run.check(f'{profile}: 长路线阶段折叠、搜索及 10/2 分页', route_paging)

        def todos():
            page.get_by_label('工作模式').select_option('todo')
            page.goto(ORIGIN+'/todos')
            page.get_by_label('查看日期', exact=True).fill(today.isoformat())
            page.get_by_label('快速记录', exact=True).fill(prefix+'-临时待办')
            page.get_by_role('button', name='记一件事', exact=True).click()
            page.get_by_label('搜索', exact=True).fill(prefix+'-临时待办')
            row = page.locator('.todo-row').filter(has_text=prefix+'-临时待办')
            expect(row).to_be_visible()
            row.get_by_role('button', name='编辑', exact=True).click()
            dialog = page.get_by_role('dialog')
            expect(dialog.get_by_label('待办标题', exact=True)).to_have_value(prefix+'-临时待办')
            dialog.get_by_label('待办标题', exact=True).fill(prefix+'-临时待办-修改')
            dialog.get_by_label('备注', exact=True).fill('真实 HTTPS 自动验收备注')
            dialog.get_by_role('button', name='保存待办', exact=True).click()
            expect(dialog).not_to_be_visible()
            row = page.locator('.todo-row').filter(has_text=prefix+'-临时待办-修改')
            row.get_by_role('checkbox').check()
            expect(row).to_have_count(0)
            page.get_by_role('combobox', name=re.compile(r'^状态')).select_option('DONE')
            expect(row).to_be_visible()
            row.get_by_role('button', name='删除', exact=True).click()
            expect(row).to_have_count(0)
            page.get_by_role('combobox', name=re.compile(r'^日期范围')).select_option('TRASH')
            expect(row).to_be_visible()
            row.get_by_role('button', name='恢复待办', exact=True).click()
            expect(row).to_have_count(0)
            page.get_by_role('combobox', name=re.compile(r'^日期范围')).select_option('DAY')
            expect(row).to_be_visible()
            shot('todo-crud')
        run.check(f'{profile}: 今日待办新增、编辑、完成筛选、删除及恢复', todos)

        def todo_paging_and_offline():
            for i in range(12):
                api(context, token, 'POST', '/todos', {'title':prefix+f'-待办分页-{i:02d}', 'dueDate':today.isoformat()}, status=201)
            page.reload()
            page.get_by_role('combobox', name=re.compile(r'^状态')).select_option('OPEN')
            page.get_by_label('搜索', exact=True).fill(prefix+'-待办分页')
            expect(page.locator('.todo-row')).to_have_count(10)
            page.get_by_role('button', name='下一页', exact=True).click()
            expect(page.locator('.todo-row')).to_have_count(2)
            page.get_by_label('搜索', exact=True).fill(prefix+'-离线待办')
            expect(page.locator('.todo-row')).to_have_count(0)
            # Interrupt actual API transport while keeping page delivery online.
            # This is cached API/offline queue coverage, not full PWA network loss.
            page.route('**/api/**', lambda route: route.abort('failed'))
            page.get_by_label('快速记录', exact=True).fill(prefix+'-离线待办')
            page.get_by_role('button', name='记一件事', exact=True).click()
            expect(page.locator('.todo-row')).to_contain_text('待同步')
            page.reload()
            page.get_by_label('搜索', exact=True).fill(prefix+'-离线待办')
            expect(page.locator('.todo-row')).to_contain_text(prefix+'-离线待办')
            page.unroute('**/api/**')
            page.goto(ORIGIN+'/sync')
            expect(page.locator('.sync-summary')).to_contain_text('1')
            page.get_by_role('button', name='同步等待项', exact=True).click()
            expect(page.get_by_text('队列为空', exact=True)).to_be_visible()
            items = api(context, token, 'GET', f'/todos?from={today}&to={today}')
            assert sum(row['title']==prefix+'-离线待办' for row in items) == 1, 'Offline replay duplicated the TODO.'
            state['todo'] = next(row for row in items if row['title']==prefix+'-离线待办')
        run.check(f'{profile}: 待办 10/2 分页、API 断网新增、刷新保留及无重复重放', todo_paging_and_offline)

        def cached_plan_and_route_edits():
            page.goto(ORIGIN+f"/plans/{state['plan']['id']}", wait_until='networkidle')
            page.get_by_label('搜索任务', exact=True).fill(state['task']['title'])
            expect(page.locator('.task-row')).to_have_count(1)
            page.route('**/api/**', lambda route: route.abort('failed'))
            page.reload()
            page.get_by_label('搜索任务', exact=True).fill(state['task']['title'])
            page.locator('.task-row').get_by_role('button', name='编辑', exact=True).click()
            dialog = page.get_by_role('dialog')
            new_title = state['task']['title']+'-离线修订'
            dialog.get_by_label('任务标题', exact=True).fill(new_title)
            dialog.get_by_role('button', name='保存修改', exact=True).click()
            expect(dialog).not_to_be_visible()
            page.reload()
            page.get_by_label('搜索任务', exact=True).fill(new_title)
            expect(page.locator('.task-row')).to_contain_text('待同步')
            page.goto(ORIGIN+f"/mainlines?line={state['line']['id']}")
            title = prefix+'-和弦练习-修订'
            row = page.locator('.mainline-step').filter(has=page.get_by_role('heading', name=title, exact=True))
            expect(row).to_be_visible()
            row.get_by_role('button', name='编辑步骤', exact=True).click()
            dialog = page.get_by_role('dialog')
            expect(dialog.get_by_role('combobox', name=re.compile(r'^前置条件'))).to_be_disabled()
            dialog.get_by_label('步骤标题', exact=True).fill(title+'-离线修订')
            dialog.get_by_role('button', name='保存步骤', exact=True).click()
            expect(dialog).not_to_be_visible()
            page.reload()
            expect(page.get_by_role('heading', name=title+'-离线修订', exact=True)).to_be_visible()
            page.unroute('**/api/**')
            page.goto(ORIGIN+'/sync')
            expect(page.locator('.sync-summary strong')).to_have_text('2')
            page.get_by_role('button', name='同步等待项', exact=True).click()
            expect(page.get_by_text('队列为空', exact=True)).to_be_visible()
            tasks = api(context, token, 'GET', f"/plans/{state['plan']['id']}/tasks")
            assert sum(row['id']==state['task']['id'] and row['title']==new_title for row in tasks) == 1
            steps = next(line for line in lines() if line['id']==state['line']['id'])['items']
            edited = next(step for step in steps if step['title']==title+'-离线修订')
            assert edited['dependencyMode'] == 'EXPLICIT' and len(edited['prerequisiteIds']) == 1
            shot('offline-plan-route-synced')
        run.check(f'{profile}: 已缓存任务与路线离线编辑、刷新保留、两项顺序同步及依赖保留', cached_plan_and_route_edits)

        def stale_todo_conflict():
            page.goto(ORIGIN+'/todos', wait_until='networkidle')
            title = state['todo']['title']
            page.get_by_label('搜索', exact=True).fill(title)
            row = page.locator('.todo-row').filter(has_text=title)
            expect(row).to_be_visible()
            current = next(item for item in api(context, token, 'GET', f'/todos?from={today}&to={today}') if item['id']==state['todo']['id'])
            changed = {key:current[key] for key in ['title','note','dueDate','done','deleted','version']}
            changed['title'] = title+'-另一设备修改'
            api(context, token, 'PUT', f"/todos/{current['id']}", changed)
            with page.expect_response(lambda response: response.url == ORIGIN+f"/api/todos/{current['id']}" and response.request.method == 'PUT') as response:
                row.get_by_role('checkbox').click()
            assert response.value.status == 409, 'Stale write must be rejected, not silently overwrite another device.'
            expect(page.get_by_role('alert')).to_contain_text('其他设备修改')
            expect(row.get_by_role('checkbox')).not_to_be_checked()
            saved = next(item for item in api(context, token, 'GET', f'/todos?from={today}&to={today}') if item['id']==current['id'])
            assert saved['title']==changed['title'] and saved['done'] is False
            state['todo'] = saved
            shot('todo-conflict-rejected')
        run.check(f'{profile}: 多设备过期版本返回 409、勾选回退且不覆盖新数据', stale_todo_conflict)

        def mode_and_owner_isolation():
            page.get_by_label('工作模式').select_option('plan')
            page.goto(ORIGIN+f"/plans/{state['plan']['id']}")
            expect(page.get_by_role('heading', name=state['plan']['name'], exact=True)).to_be_visible()
            page.get_by_label('工作模式').select_option('route')
            page.goto(ORIGIN+f"/mainlines?line={state['line']['id']}")
            expect(page.get_by_role('heading', name=state['line']['name'], exact=True)).to_be_visible()
            expect(page.locator('.todo-row')).to_have_count(0)
            page.reload()
            expect(page.get_by_label('工作模式')).to_have_value('route')
            page.get_by_label('工作模式').select_option('plan')
            expect(page).to_have_url(ORIGIN+f"/plans/{state['plan']['id']}")
            expect(page.get_by_role('heading', name=state['plan']['name'], exact=True)).to_be_visible()
            assert all(row['id'] != state['plan']['id'] for row in api(other, outsider, 'GET', '/plans'))
            assert all(row['id'] != state['line']['id'] for row in api(other, outsider, 'GET', '/mainlines'))
            assert all(row['id'] != state['todo']['id'] for row in api(other, outsider, 'GET', f'/todos?from={today}&to={today}'))
            api(other, outsider, 'GET', f"/plans/{state['plan']['id']}/tasks", status=404)
            api(other, outsider, 'PUT', f"/todos/{state['todo']['id']}", {'title':'越权验收', 'dueDate':today.isoformat(), 'done':False, 'deleted':False, 'version':state['todo']['version']}, status=404)
            other_page.goto(ORIGIN+'/plans')
            expect(other_page.get_by_text(state['plan']['name'], exact=True)).to_have_count(0)
        run.check(f'{profile}: 三模式独立记忆、刷新保持、跨用户读取与写入隔离', mode_and_owner_isolation)
    finally:
        # Only these fresh synthetic sessions are revoked; original admin sessions
        # and browser profiles are never loaded or touched.
        if run.results and run.results[-1]['status'] == 'FAIL' and 'page' in locals():
            # Business pages contain only our synthetic records. Never capture
            # the registration/login form or a secret-bearing URL on failure.
            if urlparse(page.url).path not in ['/login', '/register']:
                page.screenshot(path=str(run.out / f'{profile}-failure.png'), full_page=True)
        for item in [context, other]:
            try:
                item.request.post(ORIGIN+'/api/auth/logout')
            except Exception:
                pass
            item.close()


if __name__ == '__main__':
    sys.exit(main())
