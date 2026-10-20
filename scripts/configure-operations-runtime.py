#!/usr/bin/env python3
"""Wire approved private operational collection. No mail, deletion or app restart."""
import argparse,datetime,os,re,shutil,subprocess
from pathlib import Path

MARKER='# plan-platform bounded operations collection'

def validate(root,backups):
    if not re.fullmatch(r'/home/[A-Za-z0-9_-]+/plan-platform',root.as_posix()):
        raise ValueError('Managed production root required.')
    if not re.fullmatch(re.escape(root.as_posix())+r'/releases/[0-9.]+-[a-f0-9]+/backups',backups.as_posix()):
        raise ValueError('Existing retained managed backup catalog required.')

def collector_body(root,backups):
    validate(root,backups)
    return '''#!/bin/sh
set -eu
PATH=/usr/local/bin:/usr/bin:/bin
export PATH
umask 077
root='ROOT'
test -f "$root/production-operations.yml" || exit 0
exec 9>"$root/.operations-collect.lock"
flock -n 9 || exit 0
release=$(readlink -f "$root/current")
case "$release" in "$root"/releases/*) ;; *) exit 1;; esac
case "${1:-}" in '') set -- ;; --verify-oss) set -- --verify-oss ;; *) exit 1;; esac
python3 "$release/scripts/operations-status.py" collect --backup-directory 'BACKUPS' --output "$root/operations/status.json" --drill-evidence "$root/operations/drill.json" --failure-evidence "$root/operations/failures.json" --group-readable "$@"
'''.replace('ROOT',root.as_posix()).replace('BACKUPS',backups.as_posix())

def merged_cron(existing,root):
    matches=[line for line in existing.splitlines() if MARKER in line]
    if len(matches)>1:raise ValueError('Duplicate managed collection entries; inspect first.')
    expected='*/5 * * * * /bin/sh '+root.as_posix()+'/collect-operations.sh >/dev/null 2>&1 '+MARKER
    if matches and matches[0]!=expected:raise ValueError('Existing managed entry differs; inspect first.')
    if matches:return existing
    return existing.rstrip()+'\n'+expected+'\n'

def main():
    p=argparse.ArgumentParser();p.add_argument('--root',required=True);p.add_argument('--backup-directory',required=True);a=p.parse_args()
    root,backups=Path(a.root),Path(a.backup_directory);validate(root,backups)
    if root.resolve()!=root or backups.is_symlink() or not backups.is_dir():raise ValueError('Canonical existing directories required.')
    release=(root/'current').resolve()
    if release.parent!=root/'releases' or not (root/'production-operations.yml').is_file():raise ValueError('Approved active operations required.')
    if (root/'operations').stat().st_gid!=101:raise ValueError('Backend group not verified.')
    os.umask(0o077)
    run=root/('operations-runtime-'+datetime.datetime.utcnow().strftime('%Y%m%dT%H%M%S%fZ'));run.mkdir()
    prior=subprocess.run(['crontab','-l'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if prior.returncode not in (0,1):raise RuntimeError('Crontab read failed.')
    old=prior.stdout.decode();new=merged_cron(old,root)
    (run/'crontab-before.txt').write_text(old)
    env=release/'.env';body=env.read_text()
    if re.search(r'^PLAN_MYSQL_BACKUP_DIR=',body,re.M):
        configured=re.search(r'^PLAN_MYSQL_BACKUP_DIR=(.*)$',body,re.M).group(1)
        if configured!=str(backups):raise ValueError('Existing backup directory differs; do not override.')
    else:
        shutil.copy2(env,run/'previous.env');env.write_text(body.rstrip()+'\nPLAN_MYSQL_BACKUP_DIR='+str(backups)+'\n');os.chmod(env,0o600)
    collector=root/'collect-operations.sh'
    if collector.is_symlink():raise ValueError('Linked helper refused.')
    if collector.exists():shutil.copy2(collector,run/'previous-collector.sh')
    collector.write_text(collector_body(root,backups));os.chmod(collector,0o700)
    helper=root/'daily-mysql-backup.sh'
    if helper.is_symlink():raise ValueError('Linked backup helper refused.')
    if helper.exists():shutil.copy2(helper,run/'previous-daily-mysql-backup.sh')
    shutil.copyfile(release/'scripts/daily-mysql-backup.sh',helper);os.chmod(helper,0o700)
    result=subprocess.run(['sh',str(collector)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode:raise RuntimeError('Collection failed; old cron retained; private helpers retained for inspection.')
    subprocess.run(['crontab','-'],input=new.encode(),check=True)
    if subprocess.check_output(['crontab','-l']).decode()!=new:raise RuntimeError('Crontab readback differs.')
    (run/'completed').write_text('Collection configured, no mail or deletion. Existing cron entries retained.\n')
    print('Bounded five-minute production collection configured; daily backup keeps the same catalog and verifies OSS. No Codex automation, mail, cleanup or application restart.')
    print('Private rollback inputs retained: '+str(run))
if __name__=='__main__':main()
