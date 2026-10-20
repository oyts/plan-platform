#!/usr/bin/env python3
"""Activate the accepted 0.1.17 candidate, preserving approved runtime overlays."""
import argparse
import hashlib
import json
import os
import re
import signal
import subprocess
import time
from pathlib import Path

HASHES = {'release.tar.gz': 'cd635586c759db28be0cd3b6747de34631bc268199f52cd08d656cfb72608d0b',
          'backend/api.jar': 'b009c2b9035aaa01fda02b2bb692ec8185d39016afd25ab53ea238aff57332eb'}
NEW_VERSION, OLD_VERSION, MIGRATION = '0.1.17', '0.1.16', 'V24 -> V25'
FLAGS = ['AI_ENABLED', 'REFRESH_COOKIE_SECURE', 'EMAIL_VERIFICATION_ENABLED',
         'MEMBER_GOVERNANCE_ENABLED', 'OPERATIONS_ENABLED', 'OPERATIONS_MAIL_ALERTS_ENABLED']
OVERLAYS = ['production-web.yml', 'production-operations.yml', 'production-mail.yml',
            'production-ai.yml', 'production-timezone.yml', 'android-private/compose.yml', 'client-public/compose.yml']


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def run(args, data=None, env=None):
    result = subprocess.run(args, input=data, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(result.returncode == 0, 'Command failed; private output withheld: ' + args[0])
    return result.stdout.decode().strip()


def validate_proof(proof, modified_at, now):
    require(proof.get('status') == 'PASS' and proof.get('migration') == MIGRATION
            and proof.get('previousImage') == 'plan-platform-api:' + OLD_VERSION
            and proof.get('targetImage') == 'plan-platform-api:' + NEW_VERSION
            and proof.get('productionApplicationChanged') is False
            and proof.get('apiSha256') == HASHES['backend/api.jar'], 'Matching isolated rehearsal required.')
    require(0 <= now - modified_at <= 3600, 'Rehearsal proof is stale or future-dated.')


def next_environment(body):
    require(re.findall(r'^PLAN_VERSION=(.*)$', body, re.M) == [OLD_VERSION], 'Expected one previous version.')
    return re.sub(r'^PLAN_VERSION=.*$', 'PLAN_VERSION=' + NEW_VERSION, body, flags=re.M)


def approved_runtime(container):
    values = dict(value.split('=', 1) for value in container['Config']['Env'] if '=' in value)
    require(all(values.get(flag) == 'true' for flag in FLAGS), 'Approved runtime flag differs; inspect without printing secrets.')
    for target in ['/run/secrets/deepseek-api-key', '/run/plan-operations']:
        require(any(m['Destination'] == target and not m['RW'] for m in container['Mounts']), 'Read-only mount missing.')


def interrupted(signum, frame):
    raise RuntimeError('Activation interrupted; entering guarded application rollback.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True)
    parser.add_argument('--drill', required=True)
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    release = Path(args.directory).resolve()
    require(re.fullmatch(r'/home/[A-Za-z0-9_-]+/plan-platform/releases/' + re.escape(NEW_VERSION + '-' + HASHES['release.tar.gz'][:8]), str(release)), 'Expected final managed candidate.')
    root = release.parent.parent
    previous = (root / 'current').resolve()
    require(previous.parent == root / 'releases' and re.fullmatch(re.escape(OLD_VERSION) + '-[a-f0-9]+', previous.name), 'Expected retained previous release.')
    drill = Path(args.drill).resolve()
    require(drill.parent == root / 'release-drills', 'Unexpected rehearsal path.')
    proof_path = drill / 'report.json'
    validate_proof(json.loads(proof_path.read_text()), proof_path.stat().st_mtime, time.time())
    for name, expected in HASHES.items():
        require(hashlib.sha256((release / name).read_bytes()).hexdigest() == expected, 'Accepted candidate bytes changed.')
    require(not (release / '.env').exists() and not (release / 'activation-started').exists(), 'Earlier activation state exists; inspect it first.')
    require(not (root / 'current.new').exists() and not (root / 'current.new').is_symlink(), 'Pending entry exists.')
    before = json.loads(run(['docker', 'inspect', 'plan-platform-backend-1']))[0]
    require(before['Config']['Image'] == 'plan-platform-api:' + OLD_VERSION, 'Production image changed.')
    approved_runtime(before)
    overlays = {}
    for name in OVERLAYS:
        path = root / name
        require(not path.is_symlink(), 'Overlay must not be a symlink.')
        overlays[name] = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None
    environment = next_environment((previous / '.env').read_text())
    if args.check_only:
        print('Preflight PASS. No files, containers, messages or production entry changed.')
        return

    mysql = json.loads(run(['docker', 'inspect', 'plan-platform-mysql-1']))[0]
    password = next(v.split('=', 1)[1] for v in mysql['Config']['Env'] if v.startswith('MYSQL_ROOT_PASSWORD='))

    def idle():
        count = run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', 'plan-platform-mysql-1', 'mysql', '-uroot', '-N',
                     'plan_platform', '-e', "SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');"],
                    env=dict(os.environ, MYSQL_PWD=password))
        require(count == '0', 'Active AI jobs: postpone upgrade.')

    def compose(directory):
        return ['sh', str(release / 'scripts/production-compose.sh'), str(directory), 'up', '-d', '--no-deps', 'backend', 'web']

    def healthy(version):
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                h = json.loads(run(['curl', '--max-time', '5', '-fsS', 'https://plan.zijun701.cn/api/health']))
                if h.get('version') == version and h.get('ok') and h.get('service') == 'plan-platform-api':
                    return
            except (RuntimeError, ValueError):
                pass
            time.sleep(2)
        raise RuntimeError('Public health failed for expected version.')

    idle()
    with (release / 'activation-backup.txt').open('x') as stream:
        stream.write(run(['sh', str(previous / 'scripts/mysql-backup.sh')]) + '\n')
    idle()
    with (release / '.env').open('x') as stream:
        stream.write(environment)
    (release / 'previous-release').write_text(str(previous) + '\n')
    (release / 'activation-started').write_text(str(time.time()))
    try:
        for name in ['SIGTERM', 'SIGHUP']:
            if hasattr(signal, name):
                signal.signal(getattr(signal, name), interrupted)
        run(compose(release))
        healthy(NEW_VERSION)
        config = json.loads(run(['curl', '--max-time', '15', '-fsS', 'https://plan.zijun701.cn/api/auth/config']))
        require(config.get('serverVersion') == NEW_VERSION and config.get('registrationEnabled') is False
                and config.get('invitationEnabled') and config.get('passwordResetEnabled'), 'Account configuration differs.')
        approved_runtime(json.loads(run(['docker', 'inspect', 'plan-platform-backend-1']))[0])
        for name, expected in overlays.items():
            path = root / name
            require((hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None) == expected, 'Runtime overlay changed during activation.')
        (root / 'current.new').symlink_to(release)
        os.replace(root / 'current.new', root / 'current')
        (release / 'activation-completed').write_text(str(time.time()))
        print('Web/API ' + NEW_VERSION + ' healthy. Approved flags/overlays retained; native feeds unchanged.')
    except BaseException:
        run(['python3', str(release / 'scripts/check-governance-rollback.py'), '--target-supports-generation', 'yes'])
        run(compose(previous))
        healthy(OLD_VERSION)
        if (root / 'current').resolve() == release:
            require(not (root / 'current.new').exists() and not (root / 'current.new').is_symlink(), 'Rollback entry needs inspection.')
            (root / 'current.new').symlink_to(previous)
            os.replace(root / 'current.new', root / 'current')
        print('Activation failed; retained generation-aware ' + OLD_VERSION + ' restored. Database and backups kept.')
        raise


if __name__ == '__main__':
    main()
