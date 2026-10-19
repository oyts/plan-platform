#!/usr/bin/env python3
"""Activate only accepted 0.1.16 bytes; retain all data and guarded 0.1.15 rollback."""
import argparse, hashlib, json, os, re, shutil, subprocess, time
from pathlib import Path


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def run(args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(result.returncode == 0, 'Command failed; private output withheld: ' + args[0])
    return result.stdout.decode().strip()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--directory', required=True)
    p.add_argument('--drill', required=True)
    p.add_argument('--overlay', required=True)
    p.add_argument('--check-only', action='store_true')
    a = p.parse_args()
    os.umask(0o077)
    release = Path(a.directory).resolve()
    require(re.fullmatch(r'/home/[A-Za-z0-9_-]+/plan-platform/releases/0\.1\.16-[a-f0-9]+', str(release)), 'Unexpected release.')
    root = release.parent.parent
    previous = (root / 'current').resolve()
    require(previous.parent == release.parent and previous != release, 'Previous managed release required.')
    drill = Path(a.drill).resolve()
    require(drill.parent == root / 'release-drills', 'Unexpected drill directory.')
    proof = json.loads((drill / 'report.json').read_text())
    require(proof.get('status') == 'PASS' and proof.get('migration') == 'V23 -> V24' and
            proof.get('previousImage') == 'plan-platform-api:0.1.15', 'Fresh snapshot rehearsal required.')
    require(0 <= time.time() - (drill / 'report.json').stat().st_mtime <= 3600, 'Drill proof is stale.')
    hashes = {'release.tar.gz': 'f83fe9f4c067de0c27480eb3410f4013d6ddc1b3213892c3cccb45e5af1eaed6',
              'backend/api.jar': '869daca03f6f60b94b5fd09f382e90791687aeef23b17c6b9dc931f72904106c'}
    for name, expected in hashes.items():
        require(hashlib.sha256((release / name).read_bytes()).hexdigest() == expected, 'Accepted bytes changed.')
    require(proof.get('apiSha256') == hashes['backend/api.jar'], 'Drill used a different API.')
    overlay = Path(a.overlay)
    require(not overlay.is_symlink() and overlay.resolve().parent == root and
            re.fullmatch(r'production-operations\.candidate-[0-9TZ]+\.yml', overlay.name), 'Expected inactive managed overlay.')
    active = root / 'production-operations.yml'
    require(not active.exists() and not active.is_symlink(), 'Existing active overlay requires separate review.')
    require((overlay.stat().st_mode & 0o777) == 0o600, 'Restricted candidate required.')
    body = overlay.read_text()
    require('OPERATIONS_MAIL_ALERTS_ENABLED: "false"' in body and 'MEMBER_GOVERNANCE_ENABLED: "true"' in body and
            'EMAIL_VERIFICATION_ENABLED: "true"' in body, 'Unapproved overlay flags.')
    metadata = root / 'operations/status.json'
    require(not metadata.is_symlink() and metadata.is_file() and metadata.stat().st_size <= 65536,
            'Bounded operations metadata missing.')
    require(0 <= time.time() - metadata.stat().st_mtime <= 3600 and
            (metadata.stat().st_mode & 0o777) == 0o640 and metadata.stat().st_gid == 101,
            'Fresh restricted operations metadata required.')
    require(run(['docker', 'inspect', '--format', '{{.Config.Image}}', 'plan-platform-backend-1']) ==
            'plan-platform-api:0.1.15', 'Production image changed.')
    require(not (release / 'activation-started').exists(), 'Activation already attempted; inspect retained evidence.')
    if a.check_only:
        print('Preflight PASS. No files, containers, messages or production entry changed.')
        return

    def compose(directory):
        return ['sh', str(release / 'scripts/production-compose.sh'), str(directory), 'up', '-d', '--no-deps', 'backend', 'web']

    # Check active jobs without printing database credentials or environment.
    mysql = json.loads(run(['docker', 'inspect', 'plan-platform-mysql-1']))[0]
    password = next(v.split('=', 1)[1] for v in mysql['Config']['Env'] if v.startswith('MYSQL_ROOT_PASSWORD='))
    def idle():
        result = subprocess.run(['docker', 'exec', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysql', '-uroot', '-N',
                                 'plan_platform', '-e', "SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');"],
                                env={**os.environ, 'MYSQL_PWD': password}, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        require(result.returncode == 0 and result.stdout.strip() == b'0', 'Active jobs or database check failure: postpone.')
    idle()
    with (release / 'activation-backup.txt').open('x') as stream:
        stream.write(run(['sh', str(previous / 'scripts/mysql-backup.sh')]) + '\n')
    idle()
    require(not (release / '.env').exists(), 'Existing candidate environment requires inspection.')
    environment = (previous / '.env').read_text()
    require(re.search(r'^PLAN_VERSION=0\.1\.15$', environment, re.M), 'Previous release version differs.')
    (release / '.env').write_text(re.sub(r'^PLAN_VERSION=.*$', 'PLAN_VERSION=0.1.16', environment, flags=re.M))
    (release / 'previous-release').write_text(str(previous) + '\n')
    (release / 'activation-started').write_text(str(time.time()))
    try:
        shutil.copyfile(overlay, active)
        os.chmod(active, 0o600)
        run(compose(release))
        deadline = time.monotonic() + 180
        while True:
            result = subprocess.run(['curl', '--max-time', '5', '-fsS', 'https://plan.zijun701.cn/api/health'],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            try:
                health = json.loads(result.stdout)
                if result.returncode == 0 and health.get('ok') and health.get('version') == '0.1.16':
                    break
            except ValueError:
                pass
            require(time.monotonic() < deadline, 'Public health failed.')
            time.sleep(2)
        config = json.loads(run(['curl', '--max-time', '15', '-fsS', 'https://plan.zijun701.cn/api/auth/config']))
        require(config.get('serverVersion') == '0.1.16' and not config.get('registrationEnabled') and
                config.get('invitationEnabled') and config.get('passwordResetEnabled'), 'Public account configuration failed.')
        container = json.loads(run(['docker', 'inspect', 'plan-platform-backend-1']))[0]
        values = dict(v.split('=', 1) for v in container['Config']['Env'] if '=' in v)
        for name in ['EMAIL_VERIFICATION_ENABLED', 'MEMBER_GOVERNANCE_ENABLED', 'OPERATIONS_ENABLED', 'AI_ENABLED', 'REFRESH_COOKIE_SECURE']:
            require(values.get(name) == 'true', 'Expected approved flag: ' + name)
        require(values.get('OPERATIONS_MAIL_ALERTS_ENABLED') == 'false', 'Alert email must remain disabled.')
        require(any(m['Destination'] == '/run/secrets/deepseek-api-key' and not m['RW'] for m in container['Mounts']), 'AI mount differs.')
        require(any(m['Destination'] == '/run/plan-operations' and not m['RW'] for m in container['Mounts']), 'Operations mount differs.')
        require(not (root / 'current.new').exists(), 'Existing pending entry requires inspection.')
        (root / 'current.new').symlink_to(release)
        os.replace(root / 'current.new', root / 'current')
        (release / 'activation-completed').write_text(str(time.time()))
        print('Web/API 0.1.16 healthy. Email verification/governance enabled; alert mail and public signup OFF. Native feeds unchanged.')
    except BaseException:
        if active.exists():
            os.replace(active, release / 'failed-operations-overlay.yml')
        run(['python3', str(release / 'scripts/check-governance-rollback.py'), '--target-supports-generation', 'yes'])
        run(compose(previous))
        old = json.loads(run(['curl', '--retry', '5', '--retry-delay', '2', '--max-time', '15', '-fsS',
                              'https://plan.zijun701.cn/api/health']))
        require(old.get('ok') and old.get('version') == '0.1.15', 'Rollback health needs inspection; all data retained.')
        print('Activation failed; previous generation-aware application restored. New database, backups and failed overlay retained.')
        raise


if __name__ == '__main__':
    main()
