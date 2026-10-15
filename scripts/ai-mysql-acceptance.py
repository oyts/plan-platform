"""Synthetic AI acceptance on an isolated ECS candidate, never the public API.

Default: no paid calls. --real-max-calls 5 explicitly permits a SINGLE five-turn run.
Private account material and model replies stay on ECS in mode-0600 files.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://127.0.0.1:43144/api'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def env_file(path):
    result = {}
    for line in path.read_text().splitlines():
        if line and not line.startswith('#'):
            key, value = line.split('=', 1)
            result[key] = value
    return result


def invitation_token(url):
    query = urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)
    values = query.get('invite', [])
    require(len(values) == 1 and values[0], 'Invitation URL token not found')
    return values[0]


def private_write(path, data, exclusive=False):
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if exclusive else os.O_TRUNC)
    fd = os.open(str(path), flags, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as out:
        json.dump(data, out, ensure_ascii=False, indent=2)


def api(path, token=None, data=None, method=None, status=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(BASE + path, headers=headers,
        data=None if data is None else json.dumps(data).encode(),
        method=method or ('GET' if data is None else 'POST'))
    try:
        response = urllib.request.urlopen(request, timeout=75)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        require(response.code == status, '%s %s: expected %s, got %s' % (request.method, path, status, response.code))
        body = response.read(512001)
        require(len(body) <= 512000, 'Response too large')
        if status >= 400 or status == 204:
            return None
        return json.loads(body)


class Acceptance:
    def __init__(self, directory):
        self.directory = directory.resolve()
        require(self.directory.parent.name == 'ai-candidates', 'Managed isolated candidate directory required')
        self.env = env_file(self.directory / '.env')
        self.state = env_file(self.directory / 'candidate.env')
        require(re.fullmatch(r'plan_acceptance_[0-9]+_[a-f0-9]+', self.env['PLAN_MYSQL_DATABASE']), 'Not an acceptance database')
        name = self.state['AI_CANDIDATE_NAME']
        owned = subprocess.check_output(['docker', 'inspect', '-f', '{{index .Config.Labels "plan.ai.candidate"}}', name], universal_newlines=True).strip()
        require(owned == str(self.directory), 'Candidate container ownership mismatch')
        self.private_path = self.directory / 'ai-acceptance-private.json'
        self.private = json.loads(self.private_path.read_text())
        self.admin = None
        self.owner = None
        self.other = None

    def sql(self, query):
        environment = dict(os.environ, MYSQL_PWD=self.env['MYSQL_ROOT_PASSWORD'])
        result = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', self.state['AI_MYSQL_CONTAINER'],
            'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', self.env['PLAN_MYSQL_DATABASE']], input=query, universal_newlines=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=environment)
        code = re.search(r'ERROR ([0-9]+)', result.stderr)
        require(result.returncode == 0, 'Isolated MySQL '+query.split()[0]+' failed (code '+(code.group(1) if code else 'unavailable')+'); details withheld')
        return result.stdout.strip()

    def login(self):
        require(api('/health')['version'] == self.env['PLAN_VERSION'], 'Candidate version mismatch')
        admin = self.private['admin']
        if not self.private.get('adminRegistered'):
            api('/auth/register', data=dict(admin, displayName='AI 隔离验收管理员'), status=201)
            self.private['adminRegistered'] = True
            private_write(self.private_path, self.private)
        self.admin = api('/auth/login', data={'email': admin['email'], 'password': admin['password']})['accessToken']
        for name in ('owner', 'other'):
            account = self.private.get(name)
            if not account:
                account = {'email': 'ai-' + secrets.token_hex(10) + '@example.test', 'password': secrets.token_hex(24)+'A1'}
                self.private[name] = account
                private_write(self.private_path, self.private)
            if not account.get('registered'):
                invite = api('/admin/invitations', self.admin, {'email': account['email'], 'expiresInHours': 24}, status=201)
                token = invitation_token(invite['registrationUrl'])
                api('/auth/register', data={'email': account['email'], 'password': account['password'],
                    'displayName': 'AI 合成验收 ' + name, 'invitationToken': token}, status=201)
                account['registered'] = True
                private_write(self.private_path, self.private)
            result = api('/auth/login', data={'email': account['email'], 'password': account['password']})
            account['id'] = result['user']['id']
            setattr(self, name, result['accessToken'])
        private_write(self.private_path, self.private)

    def session(self, mode):
        return api('/ai/sessions', self.owner, {'mode': mode, 'consent': True})

    def save(self, session, draft):
        return api('/ai/sessions/'+session['id']+'/draft', self.owner,
            {'version': session['version'], 'draftJson': json.dumps(draft, ensure_ascii=False)}, method='PUT')

    def offline(self):
        require(api('/health')['version'] == self.env['PLAN_VERSION'], 'Candidate version mismatch')
        require(not api('/auth/config')['registrationEnabled'], 'Candidate must use invitations')
        migrations = self.sql("SELECT COUNT(*),MAX(CAST(version AS UNSIGNED)),SUM(success) FROM flyway_schema_history WHERE version IS NOT NULL;").split('\t')
        require(int(migrations[1]) >= 16 and migrations[0] == migrations[2], 'Flyway V14/V15/V16 not successful')
        require(not api('/ai/capabilities', self.owner)['available'], 'Default suite must run with AI disabled')
        api('/ai/quota', status=401)
        api('/admin/ai/settings', self.owner, status=403)
        api('/ai/sessions', self.owner, {'mode': 'TODO', 'consent': False}, status=400)
        quota = api('/ai/quota', self.owner)
        require(quota['used'] == 0 and quota['pending'] == 0 and quota['dailyLimit'] == 0, 'Unexpected initial quota')
        today = dt.date.today().isoformat()
        large = {'schemaVersion':1, 'mode':'TODO', 'revision':0, 'title':'Large synthetic draft',
            'startDate':None, 'endDate':None, 'restDays':[], 'stages':[],
            'items':[{'localId':'large'+str(i),'title':'Synthetic '+str(i),'note':'x'*900,
                'dueDate':today,'plannedMinutes':0,'stageId':None,'prerequisites':[]} for i in range(80)]}
        large_session = self.save(self.session('TODO'), large)
        require(len(large_session['draft']['items']) == 80, 'Large valid draft did not round-trip')
        api('/ai/sessions/'+large_session['id']+'?version='+str(large_session['version']), self.owner, method='DELETE', status=204)
        for mode in ('PLAN', 'ROUTE', 'TODO'):
            session = self.session(mode)
            path = '/ai/sessions/' + session['id']
            api(path, self.other, status=404)
            api(path + '/jobs', self.owner, {'message': '不应调用模型', 'operationKey':secrets.token_hex(16), 'version':0}, status=503)
            draft = {'schemaVersion': 1, 'mode': mode, 'revision': 0, 'title': 'MySQL 合成 ' + mode,
                'startDate':today if mode == 'PLAN' else None, 'endDate':today if mode == 'PLAN' else None,
                'restDays': [], 'stages':[{'localId':'s1','title':'基础'}] if mode == 'ROUTE' else [],
                'items':[{'localId':'i'+str(i), 'title':'MySQL 步骤 '+str(i), 'note':'合成验收',
                    'dueDate':None if mode == 'ROUTE' else today, 'plannedMinutes':15,
                    'stageId':'s1' if mode == 'ROUTE' else None,
                    'prerequisites':['i1'] if mode == 'ROUTE' and i == 2 else []} for i in (1,2)]}
            failure_title = 'AI rollback ' + secrets.token_hex(12)
            if mode == 'TODO':
                # Do not reject retained synthetic rows from a previous failed run.
                draft['items'][1]['title'] = failure_title
            saved = self.save(session, draft)
            api(path+'/draft', self.owner, {'draftJson':json.dumps(draft),'version':0}, method='PUT', status=409)
            api(path+'/apply', self.owner, {'version':saved['version'], 'confirmed':False}, status=400)
            api(path+'/apply', self.other, {'version':saved['version'], 'confirmed':True}, status=404)
            owner_id = self.private['owner']['id']
            require(re.fullmatch(r'[a-f0-9-]{36}', owner_id), 'Unexpected synthetic owner ID')
            table = {'PLAN':'plans','ROUTE':'mainlines','TODO':'todos'}[mode]
            count_query = "SELECT COUNT(*) FROM %s WHERE owner_id='%s';" % (table, owner_id)
            original_count = int(self.sql(count_query))
            if mode == 'TODO':
                before = self.sql("SELECT COUNT(*) FROM todos WHERE owner_id='%s';" % owner_id)
                self.sql("ALTER TABLE todos ADD CONSTRAINT ai_candidate_rollback CHECK (owner_id <> '%s' OR title <> '%s');" % (owner_id, failure_title))
                try:
                    api(path+'/apply', self.owner, {'version':saved['version'], 'confirmed':True}, status=500)
                    require(self.sql("SELECT COUNT(*) FROM todos WHERE owner_id='%s';" % owner_id) == before, 'Batch partially committed')
                    require(api(path, self.owner)['applied'] is None, 'Failed apply incorrectly marked committed')
                finally:
                    self.sql('ALTER TABLE todos DROP CHECK ai_candidate_rollback;')
            applied = api(path+'/apply', self.owner, {'version':saved['version'], 'confirmed':True})
            require(applied['count'] == 2 and applied['mode'] == mode, 'Wrong creation result')
            require(api(path+'/apply', self.owner, {'version':saved['version'], 'confirmed':True}) == applied, 'Apply replay duplicated data')
            require(int(self.sql(count_query)) == original_count + (2 if mode == 'TODO' else 1), 'Creation/replay business row count mismatch')
            if mode == 'ROUTE':
                routes = api('/mainlines', self.owner)
                line = next(x for x in routes if x['id'] in applied['path'])
                require(len(line['stages']) == 1 and len(line['items']) == 2, 'Stages/items missing')
                require(sorted(x['unlocked'] for x in line['items']) == [False, True], 'Route dependency not preserved')
        for resource in ('plans', 'mainlines', 'todos?from='+today+'&to='+today):
            require(api('/'+resource, self.other) == [], 'Synthetic users not isolated')
        require(api('/ai/quota', self.owner)['used'] == 0, 'Manual import charged quota')
        private_write(self.directory/'mysql-acceptance-result.json', {'passed':True, 'version':self.env['PLAN_VERSION'],
            'migrationCount':int(migrations[0]), 'checks':['V14/V15/V16','large draft','invitation-only','disabled fallback','three-mode creation','ownership','409','apply replay','whole-batch rollback','route dependencies'], 'paidCalls':0})
        print('PASS: MySQL AI migration, fallback, three modes, ownership, 409, replay and full transaction rollback; paid calls=0.')

    def real(self, max_calls):
        require(max_calls == 5, 'This acceptance scenario permits exactly five submitted requests at most')
        require(api('/ai/capabilities', self.owner)['available'], 'Enable only the isolated candidate first')
        marker = self.directory/'real-acceptance-started.json'
        private_write(marker, {'maxCalls':max_calls, 'submitted':0, 'results':[]}, exclusive=True)
        state = json.loads(marker.read_text())
        owner_id = self.private['owner']['id']
        quota = api('/ai/quota', self.owner)
        require(quota['used'] == 0 and quota['pending'] == 0, 'Fresh synthetic quota required')
        api('/admin/ai/users/'+owner_id, self.admin, {'dailyLimit':5,'paused':False,'version':quota['version']}, method='PUT')
        try:
            session = self.session(None)
            session = self.turn(session, '我想从零学吉他，只在空闲时练习，每次约15分钟，不确定结束日期。请先推荐最适合的模式，不要生成草稿。', state, marker)
            require(session['draft'] is None and session['recommendedMode'] == 'ROUTE', 'Mode recommendation incorrect')
            session = api('/ai/sessions/'+session['id']+'/mode', self.owner, {'mode':'ROUTE','version':session['version']}, method='PUT')
            session = self.turn(session, '我确认使用目标路线。只生成3个循序渐进的入门步骤，分为认识吉他和入门演奏两个阶段；每步15分钟，最后一步依赖前一步，不安排日期。', state, marker)
            require(session['draft'] is not None and len(session['draft']['stages']) >= 1, 'Route draft missing')
            session = self.turn(session, '保持阶段和步骤不变，把每一步时间调整为10分钟，备注提醒我手指疼就休息。', state, marker)
            require(all(x['plannedMinutes'] == 10 for x in session['draft']['items']), 'Conversational revision not applied')
            self.confirm(session)
            today = dt.date.today()
            start = today + dt.timedelta(days=1)
            end = start + dt.timedelta(days=6)
            session = self.turn(self.session('PLAN'), '我确认周期计划。从%s到%s，每周星期日休息，其余每个计划日安排一次15分钟英语单词复习。请直接生成不超过7个任务的草稿，不要在星期日安排。' % (start,end), state, marker)
            require(session['draft'] is not None and session['draft']['restDays'] == [7], 'Plan/rest days not respected')
            self.confirm(session)
            session = self.turn(self.session('TODO'), '我确认今日待办。日期是%s，需要买牛奶和整理书桌，只生成这两条，不做长期计划。' % today, state, marker)
            require(session['draft'] is not None and len(session['draft']['items']) == 2, 'TODO draft not correct')
            self.confirm(session)
            usage = api('/ai/quota', self.owner)
            require(usage['used'] == 5 and usage['pending'] == 0, 'Five successful requests not accounted exactly')
            fresh = self.session('TODO')
            api('/ai/sessions/'+fresh['id']+'/jobs', self.owner, {'message':'额度耗尽后不应调用模型','version':0,'operationKey':secrets.token_hex(16)}, status=429)
            state['passed'] = True
            print('PASS: five bounded official calls, recommendation, three modes, revision, confirm creation, quota exhaustion. No automatic retries.')
        finally:
            usage = api('/ai/quota', self.owner)
            api('/admin/ai/users/'+owner_id, self.admin, {'dailyLimit':0,'paused':True,'version':usage['version']}, method='PUT')
            private_write(marker, state)

    def turn(self, session, message, state, marker):
        require(state['submitted'] < state['maxCalls'], 'Paid-call bound reached')
        state['submitted'] += 1
        private_write(marker, state)  # Persist BEFORE submission; an uncertain response is never retried.
        path = '/ai/sessions/'+session['id']
        body = {'message':message,'version':session['version'],'operationKey':secrets.token_hex(16)}
        started = time.monotonic()
        job = api(path+'/jobs', self.owner, body, status=202)
        # Concurrent replays have the identical operation key, so cannot incur new calls.
        with ThreadPoolExecutor(max_workers=2) as executor:
            replays = list(executor.map(lambda _: api(path+'/jobs', self.owner, body, status=202), range(2)))
        require(all(x['id'] == job['id'] for x in replays), 'Concurrent admission replay changed job')
        for _ in range(90):
            result = api(path, self.owner)
            turn = next(x for x in result['turns'] if x['id'] == job['id'])
            if turn['state'] not in ('QUEUED', 'RUNNING'):
                state['results'].append({'state':turn['state'],'error':turn['error'],'seconds':round(time.monotonic()-started,1)})
                private_write(marker, state)
                require(turn['state'] == 'SUCCEEDED', 'Official call ended '+turn['state']+' ('+str(turn['error'])+'); not retried')
                private_write(self.directory/('real-reply-'+str(state['submitted'])+'.json'), result, exclusive=True)
                return result
            time.sleep(1)
        raise RuntimeError('Official call did not settle; retained for investigation, not retried')

    def confirm(self, session):
        require(session['applied'] is None, 'Draft wrote business data before confirmation')
        path = '/ai/sessions/'+session['id']+'/apply'
        body = {'version':session['version'],'confirmed':True}
        applied = api(path, self.owner, body)
        require(api(path, self.owner, body) == applied, 'Real draft apply replay was not idempotent')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--real-max-calls', type=int, default=0)
    args = parser.parse_args()
    os.umask(0o077)
    acceptance = Acceptance(Path(__file__).resolve().parent.parent)
    acceptance.login()
    if args.real_max_calls:
        acceptance.real(args.real_max_calls)
    else:
        acceptance.offline()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, StopIteration) as error:
        # Never print HTTP bodies, process stderr, private accounts or provider responses.
        print('Acceptance stopped: '+(str(error) if isinstance(error, RuntimeError) else type(error).__name__))
        raise SystemExit(1)
