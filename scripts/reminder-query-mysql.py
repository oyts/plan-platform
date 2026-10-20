#!/usr/bin/env python3
"""Bounded final reminder-query check on a clone of the synthetic V27 database."""
import datetime as dt, gzip, hashlib, json, os, pathlib, re, secrets, subprocess, time, urllib.request, urllib.error

root = pathlib.Path('/home/oyts/plan-platform')
directory = pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root) + r'/reminder-query-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}', str(directory))
os.umask(0o077)
with (directory / 'started.json').open('x') as f:
    json.dump({'at': dt.datetime.now(dt.timezone.utc).isoformat()}, f)
env = dict(os.environ)
for line in (root / 'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):
        env['MYSQL_PWD'] = line.split('=', 1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD'), 'Restricted database authentication unavailable'
base = root / 'reflection-candidates/20261020T061810Z-3c32df69/report.json'
prior = json.loads(base.read_text())
assert prior['status'] == 'PASS' and prior['tablesRestored'] == 47
source = prior['database']
assert re.fullmatch(r'plan_acceptance_[0-9]{14}_[a-f0-9]{8}', source)
suffix = directory.name[-8:]
database, restored, user = 'reminder_query_' + suffix, 'reminder_restore_' + suffix, 'rq_' + suffix
container, port = 'plan-reminder-query-' + suffix, 43159
report = {'status': 'FAIL', 'productionWrites': 0, 'paidAiCalls': 0, 'realEmailsSent': 0, 'cases': []}

def process(args, body=None, check=True):
    r = subprocess.run(args, input=body, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, timeout=180)
    if check and r.returncode:
        raise RuntimeError('Isolated command failed; private command output was not printed')
    return r

def sql(statement, db=None):
    return process(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysql', '-uroot', '-N', '--batch', *([db] if db else [])], statement.encode()).stdout.decode()

def api(path, data=None, token=None, method=None, expected=200):
    req = urllib.request.Request(f'http://127.0.0.1:{port}/api' + path, None if data is None else json.dumps(data).encode(), {'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})}, method=method)
    try: r = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as e: r = e
    assert r.code == expected, (path, r.code, expected)
    body = r.read()
    return json.loads(body) if body else None

def poll(key):
    req = urllib.request.Request(f'http://127.0.0.1:{port}/api/reminders/poll', b'', {'X-Reminder-Key': key}, method='POST')
    with urllib.request.urlopen(req, timeout=20) as r: return json.load(r)

def checksums(db):
    names = sql(f"select table_name from information_schema.tables where table_schema='{db}' order by table_name").split()
    result = {}
    for table in names:
        assert re.fullmatch('[a-zA-Z0-9_]+', table)
        result[table] = sql(f'CHECKSUM TABLE {db}.{table} EXTENDED').split()[-1]
        assert result[table] != 'NULL'
    return result

def dump(db):
    data = process(['docker', 'exec', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysqldump', '-uroot', '--single-transaction', '--no-tablespaces', '--skip-lock-tables', db]).stdout
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)', data, re.I | re.M)
    return data

try:
    expected = (directory / 'api.jar.sha256').read_text().split()[0]
    assert hashlib.sha256((directory / 'api.jar').read_bytes()).hexdigest() == expected
    report['jarSha256'] = expected
    live = json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health', timeout=15))
    baseline = checksums(source)
    assert len(baseline) == 47
    sql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '{user}'@'%' IDENTIFIED BY '{secrets.token_hex(24)}';")
    # A separate bounded user, never the production database account.
    password = secrets.token_hex(24)
    sql(f"ALTER USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {database}.* TO '{user}'@'%';")
    sql(dump(source).decode(), database)
    assert baseline == checksums(database)
    report['cases'].append('Synthetic V27 clone: all 47 original table checksums match')
    assert not re.search(r':' + str(port) + r'\s', process(['ss', '-lnt']).stdout.decode())
    config = {'SPRING_PROFILES_ACTIVE': 'mysql', 'DB_URL': f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai', 'DB_USERNAME': user, 'DB_PASSWORD': password, 'JWT_SECRET': secrets.token_hex(32), 'REGISTRATION_ENABLED': 'true', 'EMAIL_VERIFICATION_ENABLED': 'false', 'PASSWORD_RESET_ENABLED': 'false', 'AI_ENABLED': 'false', 'MEMBER_GOVERNANCE_ENABLED': 'false', 'OPERATIONS_ENABLED': 'false', 'OPERATIONS_MAIL_ALERTS_ENABLED': 'false', 'REFRESH_COOKIE_SECURE': 'false'}
    for zone in ['UTC', 'Asia/Shanghai']:
        config['JAVA_TOOL_OPTIONS'] = '-Xms64m -Xmx240m -XX:MaxMetaspaceSize=128m -Dapp.operations.scheduler-enabled=false -Duser.timezone=' + zone
        (directory / 'candidate.env').write_text(''.join(k + '=' + v + '\n' for k, v in config.items()))
        name = container + ('-utc' if zone == 'UTC' else '-asia')
        process(['docker', 'run', '-d', '--name', name, '--network', 'plan-platform_default', '--memory', '480m', '--cpus', '0.6', '-p', f'127.0.0.1:{port}:8080', '--env-file', str(directory / 'candidate.env'), '--entrypoint', 'java', '-v', str(directory / 'api.jar') + ':/candidate/api.jar:ro', 'plan-platform-api:0.1.17', '-jar', '/candidate/api.jar'])
        for _ in range(100):
            try:
                if api('/health')['service'] == 'plan-platform-api': break
            except (OSError, AssertionError): time.sleep(2)
        else: raise RuntimeError('Isolated candidate did not start')
        a = api('/auth/register', {'email': f'rq-{zone.split("/")[-1].lower()}-{suffix}@example.test', 'password': 'SyntheticQuery123!', 'displayName': '合成提醒查询'}, expected=201)
        token = a['accessToken']
        now = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
        day, hm = now.date().isoformat(), (now + dt.timedelta(minutes=3)).strftime('%H:%M')
        assert (now + dt.timedelta(minutes=3)).date() == now.date(), 'Retry on a new run away from midnight'
        p = api('/plans', {'name': '合成提醒查询', 'startDate': day, 'endDate': day, 'restDays': []}, token, expected=201)
        def task(title): return api('/plans/' + p['id'] + '/tasks', {'date': day, 'startTime': hm, 'plannedMinutes': 15, 'title': title, 'category': 'STUDY', 'required': True, 'sortOrder': 0, 'acceptance': []}, token, expected=201)
        eligible, completed, paused = task('应提醒'), task('完成不提醒'), task('暂停不提醒')
        api('/tasks/' + completed['id'] + '/complete', {'note': '', 'acceptanceChecks': [], 'version': 0}, token)
        running = api('/tasks/' + paused['id'] + '/timer/start?version=0', token=token, method='POST')
        api('/tasks/' + paused['id'] + '/timer/pause?version=' + str(running['version']), token=token, method='POST')
        prefs = api('/reminders/preferences', token=token)
        prefs.update(advanceEnabled=True, advanceMinutes=5)
        api('/reminders/preferences', prefs, token, 'PUT')
        device = api('/reminders/devices', {'platform': 'WINDOWS', 'label': '合成查询设备'}, token)
        first = poll(device['token'])
        assert len(first['notices']) == 1 and first['notices'][0]['path'] == f"/plans/{p['id']}?date={day}"
        again = poll(device['token'])
        assert not again['notices'] and len(again['activeKeys']) == 1
        api('/tasks/' + eligible['id'] + '/complete', {'note': '', 'acceptanceChecks': [], 'version': 0}, token)
        assert not poll(device['token'])['activeKeys']
        other = api('/auth/register', {'email': f'other-{zone.split("/")[-1].lower()}-{suffix}@example.test', 'password': 'SyntheticQuery123!', 'displayName': '合成其他用户'}, expected=201)
        other_device = api('/reminders/devices', {'platform': 'ANDROID', 'label': '合成隔离设备'}, other['accessToken'])
        assert not poll(other_device['token'])['notices']
        report['cases'].append(zone + ': logical time / completed-paused suppression / replay dedup / completion revocation / owner isolation')
        process(['docker', 'stop', name])
    snap = checksums(database)
    backup = directory / (database + '.sql.gz')
    backup.write_bytes(gzip.compress(dump(database)))
    sql(f'CREATE DATABASE {restored} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    sql(gzip.decompress(backup.read_bytes()).decode(), restored)
    assert snap == checksums(restored)
    assert baseline == checksums(source)
    assert json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health', timeout=15)) == live
    report.update(status='PASS', tablesRestored=len(snap), database=database, restoreDatabase=restored, productionVersion=live['version'])
    report['cases'].append('47-table post-query restore and unchanged source clone / production health')
finally:
    for ending in ['-utc', '-asia']: process(['docker', 'stop', container + ending], check=False)
    report['at'] = dt.datetime.now(dt.timezone.utc).isoformat()
    (directory / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
