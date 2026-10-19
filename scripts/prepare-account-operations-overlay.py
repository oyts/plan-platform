#!/usr/bin/env python3
"""Prepare a restricted INACTIVE overlay. No restart, SMTP, database or deletion."""
import argparse, datetime, json, os, re
from pathlib import Path

def render(directory, recipient):
    canonical=directory.as_posix()
    if not re.fullmatch(r'/home/[A-Za-z0-9_-]+/plan-platform/operations', canonical):
        raise ValueError('Use the managed operations metadata directory.')
    if not re.fullmatch(r"[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", recipient):
        raise ValueError('Use one approved mailbox, not a list or header.')
    # No credentials, production restart or active file replacement.
    return f'''services:
  backend:
    environment:
      OPERATIONS_ENABLED: "true"
      OPERATIONS_STATUS_FILE: /run/plan-operations/status.json
      EMAIL_VERIFICATION_ENABLED: "true"
      MEMBER_GOVERNANCE_ENABLED: "true"
      OPERATIONS_MAIL_ALERTS_ENABLED: "false"
      OPERATIONS_MAIL_TO: "{recipient}"
    volumes:
      - type: bind
        source: {canonical}
        target: /run/plan-operations
        read_only: true
'''

def main():
    p=argparse.ArgumentParser();p.add_argument('--directory',required=True);p.add_argument('--recipient',required=True)
    args=p.parse_args();directory=Path(args.directory)
    body=render(directory,args.recipient)
    if directory.is_symlink() or not directory.is_dir() or directory.resolve()!=directory:
        raise ValueError('Existing canonical metadata directory required; no symlinks.')
    metadata=directory/'status.json'
    if metadata.is_symlink() or not metadata.is_file() or metadata.stat().st_size>65536:
        raise ValueError('Collect bounded status evidence first.')
    data=json.loads(metadata.read_text(encoding='utf-8'))
    # The ECS host ships Python 3.6; fromisoformat requires 3.7.
    stamp=data['observedAt']
    if not re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?(?:Z|\+00:00)',stamp):
        raise ValueError('UTC observation timestamp required.')
    stamp=stamp.removesuffix('Z') if hasattr(stamp,'removesuffix') else stamp.rstrip('Z')
    stamp=stamp.replace('+00:00','')
    observed=datetime.datetime.strptime(stamp,'%Y-%m-%dT%H:%M:%S.%f' if '.' in stamp else '%Y-%m-%dT%H:%M:%S').replace(tzinfo=datetime.timezone.utc)
    age=(datetime.datetime.now(datetime.timezone.utc)-observed).total_seconds()
    if data.get('schemaVersion')!=1 or not -300<=age<=3600:
        raise ValueError('Fresh schema-1 evidence required; unknown checks may remain unknown.')
    if directory.stat().st_gid!=101 or metadata.stat().st_gid!=101 or (metadata.stat().st_mode&0o777)!=0o640:
        raise ValueError('Verify backend group 101 and 0640 metadata access; permissions were not changed.')
    target=directory.parent/('production-operations.candidate-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.yml')
    descriptor=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(descriptor,'w',encoding='utf-8',newline='\n') as stream:stream.write(body)
    print('Inactive candidate saved: '+str(target))
    print('No service restarted, verification/governance not activated, alert email still disabled. Validate migration, backup, sender test and rollback before activation.')

if __name__=='__main__':main()
