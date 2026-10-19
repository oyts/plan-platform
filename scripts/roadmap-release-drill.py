#!/usr/bin/env python3
"""Rehearse a selected migration on a fresh private production restore, never live.

Retain snapshots, isolated users and stopped containers. No model/mail keys,
public ports, user login, cleanup or production application switch.
"""
import argparse, datetime, hashlib, json, os, re, secrets, subprocess, time
from pathlib import Path


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def command(args, data=None, env=None):
    result = subprocess.run(args, input=data, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(result.returncode == 0, 'Command failed; private output withheld: ' + str(args[0]))
    return result.stdout.decode().strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True)
    parser.add_argument('--profile', choices=['roadmap', 'mail-operations', 'ai-workbench'], default='roadmap')
    parser.add_argument('--archive-sha256')
    parser.add_argument('--api-sha256')
    args = parser.parse_args()
    mail = args.profile == 'mail-operations'
    workbench = args.profile == 'ai-workbench'
    new_version, old_version = ('0.1.16', '0.1.15') if mail else ('0.1.15', '0.1.14')
    source_schema, target_schema = (23, 24) if mail else (19, 23)
    if workbench:
        new_version, old_version, source_schema, target_schema = '0.1.17', '0.1.16', 24, 25
        require(all(re.fullmatch('[a-f0-9]{64}', value or '') for value in [args.archive_sha256, args.api_sha256]), 'Accepted archive and API hashes required.')
    release = Path(args.directory).resolve()
    require(re.fullmatch(r'/home/[a-zA-Z0-9_-]+/plan-platform/releases/' + re.escape(new_version) + r'-[a-f0-9]+', str(release)), 'Expected selected managed release.')
    root = release.parent.parent
    previous = (root / 'current').resolve()
    require(previous.parent == release.parent and previous != release, 'An existing previous release is required.')
    if workbench:
        require(hashlib.sha256((release / 'release.tar.gz').read_bytes()).hexdigest() == args.archive_sha256, 'Accepted archive differs.')
        require(hashlib.sha256((release / 'backend/api.jar').read_bytes()).hexdigest() == args.api_sha256, 'Accepted API differs.')
    if mail:
        require(hashlib.sha256((release / 'release.tar.gz').read_bytes()).hexdigest() ==
                'f83fe9f4c067de0c27480eb3410f4013d6ddc1b3213892c3cccb45e5af1eaed6', 'Accepted archive differs.')
        require(hashlib.sha256((release / 'backend/api.jar').read_bytes()).hexdigest() ==
                '869daca03f6f60b94b5fd09f382e90791687aeef23b17c6b9dc931f72904106c', 'Accepted API differs.')
    os.umask(0o077)
    run = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S') + '-' + secrets.token_hex(4)
    folder = root / 'release-drills' / ('roadmap-' + run)
    folder.mkdir(parents=True, exist_ok=False)
    report = dict(status='FAIL', productionApplicationChanged=False, paidAiCalls=0, realEmailsSent=0)
    running = None
    mysql = json.loads(command(['docker', 'inspect', 'plan-platform-mysql-1']))[0]
    password = next(v.split('=', 1)[1] for v in mysql['Config']['Env'] if v.startswith('MYSQL_ROOT_PASSWORD='))
    environment = {**os.environ, 'MYSQL_PWD': password}

    def sql(statement):
        return command(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysql', '-uroot', '--default-character-set=utf8mb4', '--batch', '--skip-column-names'], data=statement.encode(), env=environment)

    def public_health():
        return json.loads(command(['curl', '--max-time', '15', '-fsS', 'https://plan.zijun701.cn/api/health']))

    def version(database):
        return int(sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM ' + database + '.flyway_schema_history WHERE success=1;'))

    try:
        old_image = command(['docker', 'inspect', '--format', '{{.Config.Image}}', 'plan-platform-backend-1'])
        require(old_image == 'plan-platform-api:' + old_version, 'Unexpected production image.')
        require(version('plan_platform') == source_schema, 'Unexpected production schema.')
        require(sql("SELECT COUNT(*) FROM plan_platform.ai_jobs WHERE state IN ('QUEUED','RUNNING');") == '0', 'Active AI jobs: postpone rehearsal.')
        before_health = public_health()
        backup_output = command(['sh', str(previous / 'scripts/mysql-backup.sh')])
        (folder / 'backup.txt').write_text(backup_output + '\n')
        backup_name = re.search(r'^Verified MySQL backup: (.+)$', backup_output, re.M)
        require(backup_name is not None, 'Verified backup path missing.')
        backup = Path(backup_name[1])
        if not backup.is_absolute():
            backup = previous / backup
        backup = backup.resolve()
        # Production deliberately retains its original managed backup directory
        # across application upgrades. Do not require it to move with current.
        require(backup.parent.name == 'backups' and backup.parent.parent.parent == root / 'releases'
                and re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+-[a-f0-9]+', backup.parent.parent.name)
                and re.fullmatch(r'plan_platform-mysql-[0-9]{8}-[0-9]{6}\.sql\.gz', backup.name)
                and backup.is_file(), 'Unexpected backup path.')
        restore_output = command(['sh', str(previous / 'scripts/mysql-restore-drill.sh'), str(backup)])
        (folder / 'restore.txt').write_text(restore_output + '\n')
        match = re.search(r'Restore validated in isolated database (plan_restore_[0-9_]+);', restore_output)
        require(match is not None, 'Verified isolated restore missing.')
        database = match[1]
        require(version(database) == source_schema, 'Restored migration version differs.')
        tables = sql("SELECT table_name FROM information_schema.tables WHERE table_schema='" + database + "' ORDER BY table_name;").splitlines()
        require(all(re.fullmatch(r'[a-z0-9_]+', t) for t in tables), 'Invalid table name.')
        columns = {}
        for table in ([] if mail or workbench else ['users', 'task_templates', 'mainlines']):
            columns[table] = sql("SELECT column_name FROM information_schema.columns WHERE table_schema='" + database + "' AND table_name='" + table + "' ORDER BY ordinal_position;").splitlines()
            require(columns[table] and all(re.fullmatch('[a-z0-9_]+', c) for c in columns[table]), 'Invalid column projection.')

        def fingerprints():
            output = {}
            for table in tables:
                if table == 'flyway_schema_history':
                    continue
                if table in columns:
                    # Private original fields stay in process memory and are only hashed.
                    rows = sql('SELECT JSON_ARRAY(' + ','.join('`' + c + '`' for c in columns[table]) + ') FROM ' + database + '.' + table + ' ORDER BY 1;')
                    output[table] = hashlib.sha256(rows.encode()).hexdigest()
                else:
                    output[table] = sql('CHECKSUM TABLE ' + database + '.' + table + ' EXTENDED;').split()[-1]
                    require(output[table] != 'NULL', 'Checksum unavailable.')
            return output

        before = fingerprints()
        user, key = 'drill_' + secrets.token_hex(8), secrets.token_hex(32)
        sql("CREATE USER '" + user + "'@'%' IDENTIFIED BY '" + key + "'; GRANT ALL ON " + database + ".* TO '" + user + "'@'%';")
        values = dict(SPRING_PROFILES_ACTIVE='mysql', DB_USERNAME=user, DB_PASSWORD=key,
                      DB_URL='jdbc:mysql://mysql:3306/' + database + '?serverTimezone=Asia/Shanghai&characterEncoding=utf8',
                      JWT_SECRET=secrets.token_hex(48), JAVA_TOOL_OPTIONS='-Xms48m -Xmx192m -XX:MaxMetaspaceSize=160m -Duser.timezone=Asia/Shanghai',
                      SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='2', AI_ENABLED='false', AI_API_KEY_FILE='',
                      REGISTRATION_ENABLED='false', INVITATION_ENABLED='false', PASSWORD_RESET_ENABLED='false',
                      EMAIL_VERIFICATION_ENABLED='false', BOOTSTRAP_ADMIN_EMAIL='', BOOTSTRAP_ADMIN_TOKEN_HASH='',
                      BOOTSTRAP_ADMIN_EXPIRES_AT='', OPERATIONS_ENABLED='false', MEMBER_GOVERNANCE_ENABLED='false',
                      OPERATIONS_MAIL_ALERTS_ENABLED='false', OPERATIONS_MAIL_TO='',
                      SPRING_AUTOCONFIGURE_EXCLUDE='org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration')
        (folder / 'app.env').write_text(''.join(k + '=' + v + '\n' for k, v in values.items()))

        def boot(image, suffix, expected):
            nonlocal running
            running = 'plan-roadmap-drill-' + run + suffix
            command(['docker', 'run', '-d', '--name', running, '--network', 'plan-platform_default', '--memory', '480m', '--cpus', '0.6', '--pids-limit', '256', '--env-file', str(folder / 'app.env'), image])
            deadline = time.monotonic() + 240
            healthy = False
            while time.monotonic() < deadline:
                status = subprocess.run(['docker', 'exec', running, 'sh', '-c', 'wget -qO- http://127.0.0.1:8080/actuator/health/readiness'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                if status.returncode == 0 and json.loads(status.stdout).get('status') == 'UP':
                    healthy = True
                    break
                time.sleep(2)
            require(healthy, 'Isolated app readiness failed; container and restore retained.')
            health = json.loads(command(['docker', 'exec', running, 'sh', '-c', 'wget -qO- http://127.0.0.1:8080/api/health']))
            require(health.get('version') == expected, 'Isolated application version mismatch.')
            command(['docker', 'stop', '--time', '30', running])
            running = None

        boot('plan-platform-api:' + new_version, '', new_version)
        require(version(database) == target_schema, 'Target migration missing.')
        require(before == fingerprints(), 'Existing private business fields changed during migration.')
        if workbench:
            require(sql('SELECT COUNT(*) FROM ' + database + '.ai_saved_drafts;') == '0', 'Unexpected saved-draft mutation.')
        elif not mail:
            require(sql('SELECT COUNT(*) FROM ' + database + '.users WHERE auth_generation<>0;') == '0', 'Unexpected generation mutation.')
        else:
            require(sql('SELECT COUNT(*) FROM ' + database + '.operations_mail_delivery;') == '0', 'Unexpected mail mutation.')
        boot(old_image, '-rollback', old_version)
        require(before == fingerprints(), 'Original fields changed during previous-image rehearsal.')
        require(version('plan_platform') == source_schema and public_health() == before_health, 'Production changed during isolated rehearsal.')
        report.update(status='PASS', backup=str(backup), restoredDatabase=database, previousImage=old_image,
                      originalTablesChecked=len(before), originalProjectionTables=list(columns), migration='V19 -> V23',
                      rollback='Actual previous image; governance not exercised, rollback still requires generation guard.',
                      targetImage='plan-platform-api:' + new_version,
                      apiSha256=hashlib.sha256((release / 'backend/api.jar').read_bytes()).hexdigest())
        report['migration'] = 'V' + str(source_schema) + ' -> V' + str(target_schema)
        print('PASS: private production snapshot migration and previous-image rehearsal; original business fields unchanged.')
    except Exception as error:
        report['failure'] = str(error)
        raise
    finally:
        if running:
            command(['docker', 'stop', '--time', '30', running])
        (folder / 'report.json').write_text(json.dumps(report, indent=2))
        print('Private evidence: ' + str(folder))


if __name__ == '__main__':
    main()
