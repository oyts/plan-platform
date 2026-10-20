#!/usr/bin/env python3
"""V27 -> V28 synthetic-only migration, template and full restore rehearsal."""
import datetime as dt, gzip, hashlib, json, os, pathlib, re, secrets, subprocess, time, urllib.request, urllib.error, uuid

root = pathlib.Path('/home/oyts/plan-platform')
directory = pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root) + r'/template-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}', str(directory))
os.umask(0o077)
with (directory / 'started.json').open('x') as f: json.dump({'at': dt.datetime.now(dt.timezone.utc).isoformat()}, f)
env = dict(os.environ)
for line in (root / 'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='): env['MYSQL_PWD'] = line.split('=', 1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD'), 'Restricted database authentication unavailable'
suffix = directory.name[-8:]
database, restored, user = 'template_acceptance_' + suffix, 'template_restore_' + suffix, 'tpl_' + suffix
names = {k: 'plan-template-' + k + '-' + suffix for k in ['old', 'utc', 'asia', 'rollback']}
port = 43160
report = {'status': 'FAIL', 'productionWrites': 0, 'paidAiCalls': 0, 'realEmailsSent': 0, 'cases': []}

def process(args, body=None, check=True):
    r = subprocess.run(args, input=body, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, timeout=180)
    if check and r.returncode: raise RuntimeError('Isolated command failed; private output was not printed')
    return r

def sql(statement, db=None):
    return process(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysql', '-uroot', '-N', '--batch', *([db] if db else [])], statement.encode()).stdout.decode()

def api(path, data=None, token=None, expected=200, method=None):
    req = urllib.request.Request(f'http://127.0.0.1:{port}/api' + path, None if data is None else json.dumps(data).encode(), {'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})}, method=method)
    try: r = urllib.request.urlopen(req, timeout=25)
    except urllib.error.HTTPError as e: r = e
    assert r.code == expected, (path, r.code, expected)
    body = r.read()
    return json.loads(body) if body else None

def start(kind, candidate, zone='UTC'):
    assert not re.search(r':' + str(port) + r'\s', process(['ss', '-lnt']).stdout.decode()), 'Port occupied; no process stopped'
    config['JAVA_TOOL_OPTIONS'] = '-Xms64m -Xmx240m -XX:MaxMetaspaceSize=128m -Dapp.operations.scheduler-enabled=false -Duser.timezone=' + zone
    (directory / 'candidate.env').write_text(''.join(k + '=' + v + '\n' for k, v in config.items()))
    args = ['docker', 'run', '-d', '--name', names[kind], '--network', 'plan-platform_default', '--memory', '480m', '--cpus', '0.6', '-p', f'127.0.0.1:{port}:8080', '--env-file', str(directory / 'candidate.env')]
    args += ['--entrypoint', 'java', '-v', str(directory / 'api.jar') + ':/candidate/api.jar:ro', 'plan-platform-api:0.1.18', '-jar', '/candidate/api.jar'] if candidate else ['plan-platform-api:0.1.18']
    process(args)
    for _ in range(100):
        if process(['docker', 'inspect', '--format', '{{.State.Running}}', names[kind]]).stdout.strip() != b'true': raise RuntimeError('Isolated container exited; inspect private logs')
        try:
            if api('/health')['service'] == 'plan-platform-api': return
        except (OSError, AssertionError): pass
        time.sleep(2)
    raise RuntimeError('Isolated service did not become ready')

def checksums(db):
    result = {}
    for table in sql(f"select table_name from information_schema.tables where table_schema='{db}' order by table_name").split():
        assert re.fullmatch('[a-zA-Z0-9_]+', table)
        result[table] = sql(f'CHECKSUM TABLE {db}.{table} EXTENDED').split()[-1]
        assert result[table] != 'NULL'
    return result

def confirmation(request, preview): return {'request': request, 'checksum': preview['checksum'], 'operationKey': str(uuid.uuid4()), 'confirmed': True}

try:
    expected = (directory / 'api.jar.sha256').read_text().split()[0]
    assert hashlib.sha256((directory / 'api.jar').read_bytes()).hexdigest() == expected
    report['jarSha256'] = expected
    live = json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health', timeout=15)); assert live['version'] == '0.1.18'
    password = secrets.token_hex(24)
    sql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {database}.* TO '{user}'@'%';")
    config = {'SPRING_PROFILES_ACTIVE': 'mysql', 'DB_URL': f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai', 'DB_USERNAME': user, 'DB_PASSWORD': password, 'JWT_SECRET': secrets.token_hex(32), 'REGISTRATION_ENABLED': 'true', 'EMAIL_VERIFICATION_ENABLED': 'false', 'PASSWORD_RESET_ENABLED': 'false', 'AI_ENABLED': 'false', 'MEMBER_GOVERNANCE_ENABLED': 'false', 'OPERATIONS_ENABLED': 'false', 'OPERATIONS_MAIL_ALERTS_ENABLED': 'false', 'REFRESH_COOKIE_SECURE': 'false'}
    start('old', False)
    accounts = [api('/auth/register', {'email': f'template-{x}-{suffix}@example.test', 'password': 'SyntheticTemplate123!', 'displayName': '合成模板验收'}, expected=201) for x in ['a', 'b']]
    token, other = [a['accessToken'] for a in accounts]
    plan = api('/plans', {'name': '合成迁移结构', 'startDate': '2026-10-21', 'endDate': '2026-11-21', 'restDays': [7]}, token, 201)
    task = api('/plans/' + plan['id'] + '/tasks', {'date': '2026-10-23', 'startTime': '09:30', 'endTime': '10:00', 'plannedMinutes': 30, 'title': '合成结构任务', 'category': 'STUDY', 'required': True, 'sortOrder': 0, 'acceptance': ['结构验收']}, token, 201)
    api('/tasks/' + task['id'] + '/manual-time', {'seconds': 60, 'version': 0}, token)
    baseline = checksums(database); assert len(baseline) == 47
    assert sql('select max(cast(version as unsigned)) from flyway_schema_history where success=1', database).strip() == '27'
    process(['docker', 'stop', names['old']])
    for kind, zone in [('utc', 'UTC'), ('asia', 'Asia/Shanghai')]:
        start(kind, True, zone)
        if kind == 'utc':
            migrated = checksums(database)
            assert all(migrated[k] == v for k, v in baseline.items() if k != 'flyway_schema_history')
            assert len(migrated) == 49
            report['cases'].append('Actual 0.1.18 V27 -> V28; 46 existing business tables unchanged')
        # Seed through the normal mapped API in each JVM zone, then compare template semantics.
        source = api('/plans', {'name': '合成时区结构' + kind, 'startDate': '2026-10-21', 'endDate': '2026-11-21', 'restDays': [7]}, token, 201)
        api('/plans/' + source['id'] + '/tasks', {'date': '2026-10-23', 'startTime': '09:30', 'endTime': '10:00', 'plannedMinutes': 30, 'title': '合成时区任务', 'category': 'STUDY', 'required': True, 'sortOrder': 0, 'acceptance': []}, token, 201)
        assert api('/plans/' + source['id'] + '/tasks', token=token)[0]['startTime'].startswith('09:30')
        req = {'source': 'PLAN', 'sourceId': source['id'], 'name': '个人方法' + kind, 'description': '合成说明', 'category': '学习', 'favorite': True, 'targetId': None, 'version': None}
        preview = api('/structure-templates/capture/preview', req, token)
        row = preview['blueprint']['rows']['plan_tasks'][0]
        assert row['start_time'].startswith('09:30'), ('Logical time shifted', zone, row['start_time'])
        body = confirmation(req, preview)
        saved = api('/structure-templates/capture', body, token)
        assert api('/structure-templates/capture', body, token) == saved
        api('/structure-templates/' + saved['id'], token=other, expected=404)
        use = {'name': '新副本' + kind, 'startDate': '2026-11-01', 'endDate': '2026-12-02', 'restDaysMask': 0, 'version': 0}
        p = api('/structure-templates/' + saved['id'] + '/preview', use, token)
        apply = confirmation(use, p)
        copied = api('/structure-templates/' + saved['id'] + '/apply', apply, token)
        assert api('/structure-templates/' + saved['id'] + '/apply', apply, token) == copied
        items = api('/plans/' + copied['id'] + '/tasks', token=token)
        assert items[0]['date'] == '2026-11-03' and items[0]['startTime'].startswith('09:30')
        assert items[0]['progress']['accumulatedSeconds'] == 0 and items[0]['progress']['status'] == 'PENDING'
        edit = {'name': '资料变化', 'description': '', 'category': '学习', 'favorite': False, 'version': 0}
        api('/structure-templates/' + saved['id'], edit, token, method='PUT')
        api('/structure-templates/' + saved['id'] + '/preview', use, token, 409)
        report['cases'].append(zone + ': 09:30 preserved; detached copy, owner isolation, idempotency, metadata409, no execution history')
        process(['docker', 'stop', names[kind]])
    start('rollback', False)
    assert len(api('/plans', token=token)) == 5
    process(['docker', 'stop', names['rollback']])
    report['cases'].append('Actual previous 0.1.18 image reads V28 without removal or rollback migration')
    snap = checksums(database)
    dump = process(['docker', 'exec', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysqldump', '-uroot', '--single-transaction', '--no-tablespaces', '--skip-lock-tables', database]).stdout
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)', dump, re.I | re.M)
    backup = directory / (database + '.sql.gz'); backup.write_bytes(gzip.compress(dump))
    sql(f'CREATE DATABASE {restored} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    sql(gzip.decompress(backup.read_bytes()).decode(), restored)
    assert snap == checksums(restored)
    assert json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health', timeout=15)) == live
    assert not json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/auth/config', timeout=15))['registrationEnabled']
    report.update(status='PASS', tablesRestored=len(snap), database=database, restoreDatabase=restored, backupSha256=hashlib.sha256(backup.read_bytes()).hexdigest(), productionVersion=live['version'])
    report['cases'].append('49-table full restore checksums equal; production health and closed registration unchanged')
finally:
    for name in names.values(): process(['docker', 'stop', name], check=False)
    report['at'] = dt.datetime.now(dt.timezone.utc).isoformat()
    (directory / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
