#!/usr/bin/env python3
"""Synthetic-only V28->V30, legacy supplementation, mailbox proofs and full restore.
No production database writes, real mail, AI requests or production container changes.
"""
import datetime as dt, gzip, hashlib, json, os, pathlib, re, secrets, subprocess, sys, time, urllib.request, urllib.error, uuid

root = pathlib.Path('/home/oyts/plan-platform')
directory = pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root) + r'/historical-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}', str(directory))
os.umask(0o077)
resume = bool(sys.argv[1:]) and sys.argv[1] in ['--resume-imported','--resume-migrated']
resumed_migration=resume and sys.argv[1]=='--resume-migrated'
attempt='initial'
if len(sys.argv)==3:
    assert resume and re.fullmatch(r'--attempt=[a-z0-9-]{1,24}',sys.argv[2]);attempt=sys.argv[2].split('=',1)[1]
assert not sys.argv[1:] or resume and len(sys.argv) in [2,3], 'Only guarded resume attempts are supported'
if resume:
    assert json.loads((directory/'report.json').read_text())['status']=='FAIL'
with (directory / (f'resume-{attempt}-started.json' if resume else 'started.json')).open('x') as f: json.dump({'at': dt.datetime.now(dt.timezone.utc).isoformat()}, f)
env = dict(os.environ)
for line in (root / 'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='): env['MYSQL_PWD'] = line.split('=', 1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD'), 'Restricted database authentication unavailable'
suffix = directory.name[-8:]
database, restored, user = 'historical_acceptance_' + suffix, 'historical_restore_' + suffix, 'hc_' + suffix
names = {kind: 'plan-historical-' + kind + '-' + suffix + ('-'+attempt if resume else '') for kind in ['old', 'new', 'rollback']}
port = 43169
report = {'status': 'FAIL', 'productionWrites': 0, 'paidAiCalls': 0, 'realEmailsSent': 0, 'cases': []}

def process(args, body=None, check=True):
    report['lastCommand'] = next((x for x in ['mysqldump','mysql','docker stop','docker run'] if x in ' '.join(args)),args[0])
    result = subprocess.run(args, input=body, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, timeout=180)
    if check and result.returncode:
        report['commandExitCode']=result.returncode
        report['commandErrorCodes']=re.findall(r'(?:ERROR|Error|error)\s*:?\s*\d{3,5}',result.stderr.decode(errors='replace'))
        raise RuntimeError('Isolated command failed; private output was not printed')
    return result

def sql(statement, db=None):
    return process(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', '--batch', *([db] if db else [])], statement.encode()).stdout.decode()

def api(path, data=None, token=None, expected=200, method=None):
    report['lastRequest'] = (method or ('POST' if data is not None else 'GET')) + ' ' + re.sub(r'[a-f0-9]{8}-[a-f0-9-]{27,}', '{synthetic-id}', path)
    (directory / 'progress.json').write_text(json.dumps({'at':dt.datetime.now(dt.timezone.utc).isoformat(),'cases':report['cases'],'lastRequest':report['lastRequest']}))
    request = urllib.request.Request(f'http://127.0.0.1:{port}/api' + path, None if data is None else json.dumps(data).encode(), {'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})}, method=method)
    try: response = urllib.request.urlopen(request, timeout=60)
    except urllib.error.HTTPError as error: response = error
    assert response.code == expected, (path, response.code, expected)
    body = response.read();response.close()
    return json.loads(body) if body else None

def checksums(db):
    result = {}
    for table in sql(f"select table_name from information_schema.tables where table_schema='{db}' order by table_name").split():
        assert re.fullmatch('[a-zA-Z0-9_]+', table)
        result[table] = sql(f'CHECKSUM TABLE {db}.{table} EXTENDED').split()[-1]
        assert result[table] != 'NULL'
    return result

def dump(db):
    body = process(['docker', 'exec', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysqldump', '--default-character-set=utf8mb4', '-uroot', '--single-transaction', '--no-tablespaces', '--skip-lock-tables', db]).stdout
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)', body, re.I | re.M)
    return body

def start(kind, candidate):
    assert not re.search(r':' + str(port) + r'\s', process(['ss', '-lnt']).stdout.decode()), 'Test port occupied; nothing stopped'
    (directory / 'candidate.env').write_text(''.join(k + '=' + v + '\n' for k, v in config.items()))
    args = ['docker', 'run', '-d', '--name', names[kind], '--network', 'plan-platform_default', '--memory', '480m', '--cpus', '0.6', '-p', f'127.0.0.1:{port}:8080', '--env-file', str(directory / 'candidate.env')]
    args += ['--entrypoint', 'java', '-v', str(directory / 'api.jar') + ':/candidate/api.jar:ro', 'plan-platform-api:0.1.19', '-jar', '/candidate/api.jar'] if candidate else ['plan-platform-api:0.1.19']
    process(args)
    for _ in range(100):
        if process(['docker', 'inspect', '--format', '{{.State.Running}}', names[kind]]).stdout.strip() != b'true': raise RuntimeError('Isolated container exited; inspect private logs')
        try:
            if api('/health')['service'] == 'plan-platform-api': return
        except (OSError, AssertionError): pass
        time.sleep(2)
    raise RuntimeError('Isolated service did not become ready')

def payload():
    return json.loads('''{"schemaVersion":6,"planVersion":"1.10.1","progress":{},"reflections":{},"workspace":{
      "customPlans":[{"id":"live","name":"合成在用计划","startDate":"2026-10-01","endDate":"2026-10-31","tags":[{"id":"keep","name":"保留标签","color":"#002FA7"}],"tasks":[{"id":"live-task","title":"合成在用任务","date":"2026-10-02","tagId":"keep","plannedMinutes":30}]}],
      "customMainlines":[{"id":"live-route","name":"合成路线","items":[{"id":"live-step","title":"在用步骤","plannedMinutes":20}]}],
      "trash":[
        {"id":"trash:plan:removed","type":"plan","deletedAt":"2026-10-20T01:00:00Z","payload":{"plan":{"id":"removed","name":"合成已删除计划","startDate":"2026-10-01","endDate":"2026-10-31","tags":[],"tasks":[{"id":"removed-task","title":"历史任务","date":"2026-10-02","plannedMinutes":30}]},"progress":{"removed-task":{"status":"completed","accumulatedSeconds":120,"completedAt":"2026-10-02T09:00:00Z"}},"reflections":{}}},
        {"id":"trash:mainline:removed-route","type":"mainline","deletedAt":"2026-10-20T02:00:00Z","payload":{"track":{"id":"removed-route","name":"合成已删除路线","items":[{"id":"removed-route-step","title":"历史路线步骤","plannedMinutes":20}]},"progress":{}}},
        {"id":"trash:mainline-item:removed-step","type":"mainline-item","deletedAt":"2026-10-20T03:00:00Z","payload":{"mainlineId":"live-route","index":0,"task":{"id":"removed-step","title":"删除步骤","plannedMinutes":10},"progress":{"status":"running","accumulatedSeconds":60,"startedAt":"2026-10-19T00:00:00Z"}}},
        {"id":"trash:tag:removed-tag","type":"tag","deletedAt":"2026-10-20T04:00:00Z","payload":{"planId":"live","index":0,"tag":{"id":"removed-tag","name":"已删除标签","color":"#FF0000"},"reassignedTaskIds":["live-task"],"replacementTagId":"keep"}}
      ],"deletionMarkers":[],"taskTemplates":[]}}''')

try:
    expected = (directory / 'api.jar.sha256').read_text().split()[0]
    assert not (directory/'api.jar').is_symlink()
    assert hashlib.sha256((directory / 'api.jar').read_bytes()).hexdigest() == expected
    # Non-secret binary must be readable by the image's unprivileged UID100.
    # Candidate directory and all env/backup files stay owner-only.
    (directory/'api.jar').chmod(0o644)
    report['jarSha256'] = expected
    live = json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health', timeout=15)); assert live['version'] == '0.1.19'
    if resume:
        config=dict(line.split('=',1) for line in (directory/'candidate.env').read_text().splitlines())
        assert config['DB_URL']==f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai' and config['DB_USERNAME']==user
        assert config['AI_ENABLED']=='false' and config['MAIL_FROM']==''
        assert sql('select max(cast(version as unsigned)) from flyway_schema_history where success=1',database).strip()==('30' if resumed_migration else '28'), 'Resume schema stage must match'
        assert sql('select count(*) from users',database).strip()=='2'
        batches=[];accounts=[]
        for x in ['a','b']:
            rows=sql(f"select u.id,l.report_json from users u join legacy_imports l on l.owner_id=u.id where u.email='historical-{x}-{suffix}@example.test' and l.status='COMPLETED'",database).strip().split('\t',1)
            assert len(rows)==2
            accounts.append({'user':{'id':rows[0],'email':f'historical-{x}-{suffix}@example.test'}})
            batches.append(json.loads(rows[1]))
        assert sql(f"select name from plans where id='{batches[0]['createdPlanIds'][0]}'",database).strip()=='合成迁移后本人编辑'
        assert sql('select count(*) from plans',database).strip()=='2'
        if resumed_migration:
            assert sql('select count(*) from legacy_recycling_supplements',database).strip()=='0', 'No supplement replay is allowed in this mode'
            assert sql('select count(*) from email_change_requests',database).strip()=='0'
            evidence=json.loads((root/'historical-candidates/20261022T130600Z-45280943/report.json').read_text())
            assert evidence['jarSha256']==expected and evidence['cases']==['Actual 0.1.19 V28 -> V30; 48 original business tables unchanged']
            report['migrationReference']='20261022T130600Z-45280943/report.json (same final JAR, separate retained synthetic database)'
    else:
        password = secrets.token_hex(24)
        sql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {database}.* TO '{user}'@'%';")
        config = {'SPRING_PROFILES_ACTIVE': 'mysql', 'DB_URL': f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai', 'DB_USERNAME': user, 'DB_PASSWORD': password, 'JWT_SECRET': secrets.token_hex(32), 'REGISTRATION_ENABLED': 'true', 'EMAIL_VERIFICATION_ENABLED': 'false', 'EMAIL_CHANGE_ENABLED': 'true', 'PASSWORD_RESET_ENABLED': 'false', 'AI_ENABLED': 'false', 'MEMBER_GOVERNANCE_ENABLED': 'false', 'OPERATIONS_ENABLED': 'false', 'OPERATIONS_MAIL_ALERTS_ENABLED': 'false', 'REFRESH_COOKIE_SECURE': 'false', 'MAIL_FROM': '', 'JAVA_TOOL_OPTIONS': '-Xms64m -Xmx240m -XX:MaxMetaspaceSize=128m -Dapp.operations.scheduler-enabled=false -Duser.timezone=UTC'}
        start('old', False)
        accounts = [api('/auth/register', {'email': f'historical-{x}-{suffix}@example.test', 'password': 'SyntheticClosure123!', 'displayName': '合成历史收口'}, expected=201) for x in ['a', 'b']]
        token, other = [a['accessToken'] for a in accounts]
        batches = [api('/migrations/legacy/import', payload(), key, 201) for key in [token, other]]
    assert all(len(b['createdPlanIds']) == 1 and 'recyclingVersion' not in b for b in batches)
    for account in accounts: assert str(uuid.UUID(account['user']['id'])) == account['user']['id']
    for row in batches:
        for value in [row['importId'], *row['createdPlanIds'], *row['createdMainlineIds']]: assert str(uuid.UUID(value)) == value
    plan, batch = batches[0]['createdPlanIds'][0], batches[0]['importId']
    if not resume:sql(f"update plans set name='合成迁移后本人编辑',version=version+1 where id='{plan}'", database)
    assert sql(f"select name from plans where id='{plan}'", database).strip() == '合成迁移后本人编辑', 'Synthetic UTF-8 fixture must round-trip before migration'
    baseline = checksums(database); assert len(baseline) == (51 if resumed_migration else 49), 'Unexpected baseline table count'
    if not resumed_migration:
        assert sql('select max(cast(version as unsigned)) from flyway_schema_history where success=1', database).strip() == '28'
        (directory / (f'before-v30-{attempt}.sql.gz' if resume else 'before-v30.sql.gz')).write_bytes(gzip.compress(dump(database)))
    if not resume:process(['docker', 'stop', names['old']])
    start('new', True)
    migrated = checksums(database); assert len(migrated) == 51
    assert all(migrated[k] == v for k, v in baseline.items() if k != 'flyway_schema_history'), 'Original table checksum drift before login'
    report['cases'].append('Retained V30 synthetic dataset unchanged before login; original V28 migration reference separately retained' if resumed_migration else 'Actual 0.1.19 V28 -> V30; 48 original business tables unchanged')
    # Login creates refresh/session records. Do it only AFTER the unchanged-table comparison.
    if resume:token,other=[api('/auth/login',{'email':a['user']['email'],'password':'SyntheticClosure123!'})['accessToken'] for a in accounts]
    api('/auth/me', token=token)
    api(f'/migrations/legacy/{batch}/recycling-preview', token=other, expected=404)
    preview = api(f'/migrations/legacy/{batch}/recycling-preview', token=token)
    assert not preview['alreadyIncluded'] and preview['writeSupported'] and len(preview['entries']) == 4
    body = {'operationKey': str(uuid.uuid4()), 'fingerprint': preview['fingerprint'], 'confirmed': True}
    sql(f"update plans set version=version+1 where id='{plan}'", database)
    api(f'/migrations/legacy/{batch}/recycling-supplement', body, token, 409)
    body['fingerprint'] = api(f'/migrations/legacy/{batch}/recycling-preview', token=token)['fingerprint']
    result = api(f'/migrations/legacy/{batch}/recycling-supplement', body, token)
    assert result == api(f'/migrations/legacy/{batch}/recycling-supplement', body, token)
    assert sql(f"select name from plans where id='{plan}'", database).strip() == '合成迁移后本人编辑'
    assert len(api('/plans', token=token)) == 1
    assert sql(f"select count(*) from legacy_recycling_supplements where import_id='{batch}'", database).strip() == '1'
    assert sql(f"select status from mainline_items where mainline_id='{batches[0]['createdMainlineIds'][0]}' and title='删除步骤'", database).strip() == 'PAUSED'
    assert api(f'/migrations/legacy/{batch}/recycling-preview', token=token)['alreadyIncluded']
    batch_b = batches[1]['importId']; p = api(f'/migrations/legacy/{batch_b}/recycling-preview', token=other)
    api(f'/migrations/legacy/{batch_b}/recycling-supplement', {'operationKey': str(uuid.uuid4()), 'fingerprint': p['fingerprint'], 'confirmed': True}, other)
    api(f'/migrations/legacy/{batch_b}/rollback', {}, other)
    assert not api('/plans', token=other) and not api('/mainlines', token=other)
    report['cases'].append('Actual old batches: 4 types, current edits, owner404, stale409, durable deduplication, stopped timer and whole-batch rollback')
    status = api('/auth/email-change/status', token=token); assert status['enabled'] and not status['configured']
    api('/auth/email-change/request', {'currentPassword': 'SyntheticClosure123!', 'newEmail': f'new-{suffix}@example.test'}, token, 503)
    assert sql('select count(*) from email_change_requests', database).strip() == '0'
    # Seed synthetic proofs directly for relational/transaction checks. No SMTP is configured or called.
    owner = accounts[0]['user']['id']; old_email = accounts[0]['user']['email']; new_email = f'new-{suffix}@example.test'
    change_id = str(uuid.uuid4()); old_raw, new_raw = secrets.token_hex(36), secrets.token_hex(36)
    hash_text = lambda value: hashlib.sha256(value.encode()).hexdigest()
    password_hash = sql(f"select password_hash from users where id='{owner}'", database).strip()
    sql(f"insert into email_change_requests (id,owner_id,old_email,new_email,password_fingerprint,auth_generation,old_token_hash,new_token_hash,expires_at,created_at) values ('{change_id}','{owner}','{old_email}','{new_email}','{hash_text(password_hash)}',0,'{hash_text(old_raw)}','{hash_text(new_raw)}',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 MINUTE),UTC_TIMESTAMP(6))", database)
    device = api('/reminders/devices', {'platform': 'WINDOWS', 'label': '合成旧提醒'}, token)
    finish = {'requestId': change_id, 'currentPassword': 'SyntheticClosure123!', 'confirmed': True}
    proof_status = api('/auth/email-change/status', token=token)
    assert not proof_status['oldVerified'] and not proof_status['newVerified']
    for side, raw in [('old', old_raw), ('new', new_raw)]: api('/auth/email-change/verify', {'requestId': change_id, 'side': side, 'token': raw}, expected=204)
    api('/auth/email-change/complete', finish, token, 204); api('/auth/me', token=token, expected=401)
    assert sql(f"select count(*) from native_reminder_devices where owner_id='{owner}' and enabled=1", database).strip() == '0'
    fresh = api('/auth/login', {'email': new_email, 'password': 'SyntheticClosure123!'})['accessToken']
    assert len(api('/plans', token=fresh)) == 1
    report['cases'].append('No-SMTP failure is honest; synthetic dual proofs + final confirmation revoke old JWT/reminder and retain plans')
    process(['docker', 'stop', names['new']]); start('rollback', False)
    api('/auth/me', token=token, expected=401)
    assert len(api('/plans', token=api('/auth/login', {'email': new_email, 'password': 'SyntheticClosure123!'})['accessToken'])) == 1
    process(['docker', 'stop', names['rollback']])
    report['cases'].append('Actual prior 0.1.19 image reads V30 and still rejects the revoked generation')
    snap = checksums(database); backup = directory / 'after-v30.sql.gz'; backup.write_bytes(gzip.compress(dump(database)))
    sql(f'CREATE DATABASE {restored} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    sql(gzip.decompress(backup.read_bytes()).decode(), restored); assert snap == checksums(restored)
    assert json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health', timeout=15)) == live
    assert not json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/auth/config', timeout=15))['registrationEnabled']
    report.update(status='PASS', database=database, restoreDatabase=restored, tablesRestored=len(snap), backupSha256=hashlib.sha256(backup.read_bytes()).hexdigest(), productionVersion=live['version'])
    report['cases'].append('51-table full synthetic restore checksums equal; production entry and closed registration unchanged')
except Exception as error:
    report['failureType'] = type(error).__name__
    report['failureCommand'] = report.get('lastCommand')
    # Only assertion tuples contain fixed endpoint/status values; never persist command output or credentials.
    if isinstance(error, AssertionError): report['failureStage'] = str(error)[:240]
    raise
finally:
    for name in names.values(): process(['docker', 'stop', name], check=False)
    report['at'] = dt.datetime.now(dt.timezone.utc).isoformat()
    (directory / (f'resume-{attempt}-report.json' if resume else 'report.json')).write_text(json.dumps(report, indent=2))
    print(json.dumps(report))
