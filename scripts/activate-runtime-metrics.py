"""Enable bounded sanitized metrics on an already accepted0.1.24 deployment.
No raw log reads, mail, model requests, database changes or root commands.
"""
import argparse,hashlib,json,os,re,subprocess,time
from pathlib import Path

def require(ok,message):
    if not ok:raise RuntimeError(message)

def run(args,data=None,allow_failure=False):
    p=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=240)
    require(allow_failure or p.returncode==0,'Command failed; private output withheld: '+args[0])
    return p.stdout.decode()

def require_idle():
    # Values remain only in this process and docker exec's private environment.
    mysql=json.loads(run(['docker','inspect','plan-platform-mysql-1']))[0]
    password=next(v.split('=',1)[1] for v in mysql['Config']['Env'] if v.startswith('MYSQL_ROOT_PASSWORD='))
    result=subprocess.run(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N','plan_platform'],input=b"SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING');",env=dict(os.environ,MYSQL_PWD=password),stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15)
    require(result.returncode==0 and result.stdout.strip()==b'0','Active AI work or unreadable state: postpone collector activation.')

def validate_templates(files):
    require(set(files)=={'nginx.conf','nginx-base.conf','compose.yml','collect.sh','logrotate.conf'},'Exact bounded templates required.')
    conf=files['nginx.conf'];base=files['nginx-base.conf']
    require(conf.count('log_format plan_private_metrics')==1 and 'access_log /var/log/plan-metrics/plan-metrics.log plan_private_metrics' in conf,'Dedicated sanitized log missing.')
    fmt=re.search(r"log_format plan_private_metrics ([^;]+);",conf)
    require(fmt and not re.search('remote_addr|request_uri|user_agent|args|referer|authorization|cookie',fmt[1],re.I),'Private source fields refused.')
    require('location = /client-updates/android/latest.json { return 404; }' in conf and 'location = /downloads/manifest.json' in conf,'Windows-only static paths must remain.')
    require('TELEMETRY_MAIL_ALERTS_ENABLED: "false"' in files['compose.yml'],'New mail must stay disabled.')
    require('flock -n 9' in files['collect.sh'] and '/usr/sbin/logrotate -s' in files['collect.sh'],'Collector must be serialized with bounded rotation.')
    require('rotate 2' in files['logrotate.conf'] and 'size 4M' in files['logrotate.conf'] and 'su oyts oyts' in files['logrotate.conf'],'Bounded owner-only rotation required.')
    require('plan_private_metrics' not in base,'Unexpected existing metrics overlay.')

def main():
    p=argparse.ArgumentParser();p.add_argument('--directory',required=True);p.add_argument('--check-only',action='store_true');args=p.parse_args();os.umask(0o027)
    root=Path('/home/oyts/plan-platform');stage=Path(args.directory).resolve()
    require(stage.parent==root/'runtime-metrics' and re.fullmatch('candidate-[A-Za-z0-9_-]+',stage.name),'Expected managed telemetry candidate.')
    names=['nginx.conf','nginx-base.conf','compose.yml','collect.sh','logrotate.conf']
    files={name:(stage/name).read_text() for name in names};validate_templates(files)
    require(all(not (stage/name).is_symlink() for name in names),'Linked template refused.')
    current=(root/'current').resolve();require(current.parent==root/'releases' and current.name.startswith('0.1.24-'),'Accepted0.1.24 required first.')
    require(not (root/'production-telemetry.yml').exists() and not (stage/'activation-started').exists(),'Earlier telemetry state exists; inspect instead of repeating.')
    before=run(['docker','exec','plan-platform-web-1','cat','/etc/nginx/conf.d/default.conf'])
    require(before==files['nginx-base.conf'],'Actual static configuration differs; do not overwrite.')
    runtime=json.loads(run(['docker','inspect','plan-platform-backend-1']))[0]
    require(runtime['Config']['Image']=='plan-platform-api:0.1.24','Unexpected API image.')
    require(any(m['Destination']=='/run/plan-operations' and not m['RW'] for m in runtime['Mounts']),'Retained operations read-only mount required.')
    ops=root/'operations';require(not ops.is_symlink() and ops.stat().st_gid==101 and ops.stat().st_mode&0o2000,'Approved setgid operations directory required.')
    cron=run(['crontab','-l']);require('plan-platform bounded runtime metrics' not in cron,'Duplicate metrics schedule refused.')
    if args.check_only:print('Runtime metrics preflight PASS; no activation or raw log read.');return
    require_idle()
    telemetry=root/'telemetry';require(not telemetry.exists(),'Do not overwrite a previous telemetry directory.')
    telemetry.mkdir(mode=0o750);(telemetry/'logs').mkdir(mode=0o750)
    (telemetry/'logs/plan-metrics.log').touch(mode=0o640)
    for name in ['collect.sh','logrotate.conf']:(telemetry/name).write_text(files[name])
    (stage/'previous-nginx.conf').write_text(before);(stage/'previous-crontab').write_text(cron);(stage/'activation-started').write_text(str(time.time()))
    # Syntax check the exact config inside the actual container network first.
    run(['docker','run','--rm','--network','plan-platform_default','--memory','64m','--entrypoint','nginx','-v',str(stage/'nginx.conf')+':/etc/nginx/conf.d/default.conf:ro','-v',str(telemetry/'logs')+':/var/log/plan-metrics','plan-platform-web:0.1.24','-t'])
    installed=False
    try:
        require_idle()
        (root/'production-telemetry.yml').write_text(files['compose.yml']);installed=True
        (root/'client-public/nginx.conf').write_text(files['nginx.conf'])
        run(['sh',str(current/'scripts/production-compose.sh'),str(current),'up','-d','--no-deps','backend','web'])
        deadline=time.monotonic()+180
        while time.monotonic()<deadline:
            try:
                health=json.loads(run(['curl','--max-time','5','-fsS','https://plan.zijun701.cn/api/health']))
                if health.get('version')=='0.1.24' and health.get('ok'):break
            except (RuntimeError,ValueError):pass
            time.sleep(2)
        else:raise RuntimeError('Public readiness failed after enabling telemetry.')
        conf=json.loads(run(['curl','--max-time','10','-fsS','https://plan.zijun701.cn/api/public/analytics/config']));require(conf.get('enabled') is True,'Opt-in service not enabled.')
        run(['sh',str(telemetry/'collect.sh')]);meta=json.loads((ops/'runtime.json').read_text());require(meta.get('schemaVersion')==1 and meta.get('healthy') is True,'Collector metadata or local readiness missing.')
        after=json.loads(run(['docker','inspect','plan-platform-backend-1']))[0];values=dict(s.split('=',1) for s in after['Config']['Env'] if '=' in s)
        require(values.get('TELEMETRY_MAIL_ALERTS_ENABLED')=='false','New mail unexpectedly enabled.')
        for destination in ['/run/plan-operations','/run/secrets/mail','/run/secrets/deepseek-api-key']:
            require(any(m['Destination']==destination and not m['RW'] for m in after['Mounts']),'Approved read-only mount changed.')
        line='* * * * * /bin/sh /home/oyts/plan-platform/telemetry/collect.sh >/dev/null 2>&1 # plan-platform bounded runtime metrics\n'
        run(['crontab','-'],(cron.rstrip()+'\n'+line).encode())
        (stage/'activation-completed').write_text(str(time.time()));print('Runtime metrics active; owner opt-in remains off by default; new mail disabled. No raw logs read.')
    except BaseException:
        if installed:
            # Keep0.1.24 retention-aware API; restore only this overlay/config.
            (root/'production-telemetry.yml').rename(stage/'failed-production-telemetry.yml')
            (root/'client-public/nginx.conf').write_text(before)
            run(['sh',str(current/'scripts/production-compose.sh'),str(current),'up','-d','--no-deps','backend','web'])
        raise
if __name__=='__main__':main()
