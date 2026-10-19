#!/usr/bin/env python3
"""Enable approved operational mail only after actual administrator receipt."""
import argparse,datetime,json,os,re,shutil,subprocess,time
from pathlib import Path

def require(ok,message):
    if not ok:raise RuntimeError(message)

def next_overlay(body):
    before='OPERATIONS_MAIL_ALERTS_ENABLED: "false"'
    require(body.count(before)==1,'Expected the disabled alert flag exactly once.')
    recipients=re.findall(r'^\s+OPERATIONS_MAIL_TO: "([^"\r\n]+)"$',body,re.M)
    require(len(recipients)==1 and re.fullmatch(r"[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}",recipients[0]),'One previously approved recipient required.')
    require('OPERATIONS_ENABLED: "true"' in body and 'read_only: true' in body,'Approved read-only operations overlay required.')
    return body.replace(before,'OPERATIONS_MAIL_ALERTS_ENABLED: "true"'),recipients[0]

def run(args):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    require(result.returncode==0,'Command failed; private output withheld: '+args[0]);return result.stdout.decode().strip()

def health():
    result=subprocess.run(['curl','--max-time','5','-fsS','https://plan.zijun701.cn/api/health'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    try:return result.returncode==0 and json.loads(result.stdout).get('version')=='0.1.16'
    except ValueError:return False

def wait_health():
    until=time.monotonic()+180
    while not health():
        require(time.monotonic()<until,'Health failed; inspect retained configuration.');time.sleep(2)

def main():
    p=argparse.ArgumentParser();p.add_argument('--root',required=True);p.add_argument('--enable',action='store_true');p.add_argument('--received-test-email',action='store_true');a=p.parse_args()
    root=Path(a.root)
    require(re.fullmatch(r'/home/[A-Za-z0-9_-]+/plan-platform',str(root)) and root.resolve()==root,'Canonical managed root required.')
    release=(root/'current').resolve();require(release.parent==root/'releases','Managed current release required.')
    require(health(),'Expected healthy 0.1.16 production.')
    target=root/'production-operations.yml'
    require(not target.is_symlink() and (target.stat().st_mode&0o777)==0o600,'Restricted active overlay required.')
    body,recipient=next_overlay(target.read_text())
    current=json.loads(run(['docker','inspect','plan-platform-backend-1']))[0]
    env=dict(v.split('=',1) for v in current['Config']['Env'] if '=' in v)
    require(env.get('OPERATIONS_ENABLED')=='true' and env.get('OPERATIONS_MAIL_ALERTS_ENABLED')=='false','Unexpected live alert flags.')
    require(env.get('SPRING_MAIL_USERNAME')==recipient and env.get('MAIL_FROM')==recipient,'Previously approved administrator sender/recipient differs.')
    require(any(m['Destination']=='/run/secrets/mail' and not m['RW'] for m in current['Mounts']),'Read-only existing SMTP mount required.')
    metadata=root/'operations/status.json'
    require(not metadata.is_symlink() and metadata.stat().st_size<=65536 and 0<=time.time()-metadata.stat().st_mtime<=600,'Fresh bounded operational evidence required.')
    status=json.loads(metadata.read_text());require(status.get('schemaVersion')==1 and status.get('oss',{}).get('verified') and status.get('drill',{}).get('matched'),'Actual OSS and restore evidence required.')
    if not a.enable:
        print('Preflight PASS. Alert mail remains disabled; no email, restart or file write.');return
    require(a.received_test_email,'Actual administrator receipt must be confirmed first.')
    mysql=json.loads(run(['docker','inspect','plan-platform-mysql-1']))[0]
    secret=next(v.split('=',1)[1] for v in mysql['Config']['Env'] if v.startswith('MYSQL_ROOT_PASSWORD='))
    jobs=subprocess.run(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N','plan_platform','-e',"select count(*) from ai_jobs where state in ('QUEUED','RUNNING');"],env={**os.environ,'MYSQL_PWD':secret},stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    require(jobs.returncode==0 and jobs.stdout.strip()==b'0','Active jobs or failed check: postpone mail activation.')
    os.umask(0o077);folder=root/('operations-mail-activation-'+datetime.datetime.utcnow().strftime('%Y%m%dT%H%M%S%fZ'));folder.mkdir()
    shutil.copy2(target,folder/'previous.yml');(folder/'receipt-confirmed.txt').write_text('Maintainer confirmed actual administrator test-email receipt in this chat.\n')
    compose=['sh',str(release/'scripts/production-compose.sh'),str(release),'up','-d','--no-deps','backend']
    try:
        (folder/'next.yml').write_text(body);shutil.copyfile(folder/'next.yml',target);os.chmod(target,0o600)
        run(compose);wait_health()
        info=json.loads(run(['docker','inspect','plan-platform-backend-1']))[0]
        require('OPERATIONS_MAIL_ALERTS_ENABLED=true' in info['Config']['Env'],'Live flag did not activate.')
        (folder/'completed').write_text(str(time.time()))
        print('Operational alert mail ENABLED after confirmed receipt; legitimate incident/recovery events may now send merged notifications. No fabricated fault or manual event was created.')
    except BaseException:
        shutil.copyfile(folder/'previous.yml',target);os.chmod(target,0o600);run(compose);wait_health()
        print('Previous disabled alert configuration restored; data and evidence retained.');raise
    print('Private rollback configuration: '+str(folder/'previous.yml'))
if __name__=='__main__':main()
