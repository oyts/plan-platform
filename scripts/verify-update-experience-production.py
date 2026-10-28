"""Accepted0.1.25 post-cutover backup/OSS/restore and retained runtime checks.

Runs on ECS. No native application, actual cleanup, mail or AI provider request.
"""
import datetime as dt,hashlib,importlib.util,json,os,re,subprocess
from pathlib import Path,PurePosixPath
spec=importlib.util.spec_from_file_location('update_snapshot',Path(__file__).with_name('roadmap-release-drill.py'))
drill=importlib.util.module_from_spec(spec);spec.loader.exec_module(drill)

def runtime_facts(value,now):
    stamp=value.get('observedAt')
    drill.require(isinstance(stamp,str) and stamp.endswith('+00:00'),'Expected UTC runtime timestamp.')
    observed=dt.datetime.strptime(stamp[:19],'%Y-%m-%dT%H:%M:%S').replace(tzinfo=dt.timezone.utc)
    age=(now-observed).total_seconds()
    drill.require(value.get('schemaVersion')==1 and 0<=age<150 and value.get('healthy') is True
        and value.get('logComplete') is True and isinstance(value.get('samples'),list)
        and len(value['samples'])>=3,'Scheduled runtime evidence incomplete, unhealthy or stale.')
    return dict(scheduledSamples=len(value['samples']),ageSeconds=round(age),healthy=True,complete=True)

def backup_location(value):
    p=PurePosixPath(value)
    drill.require(str(p).startswith('/home/oyts/plan-platform/releases/') and p.parent.name=='backups'
        and p.parent.parent.parent==PurePosixPath('/home/oyts/plan-platform/releases')
        and re.fullmatch(r'\d+\.\d+\.\d+-[a-f0-9]+',p.parent.parent.name)
        and re.fullmatch(r'plan_platform-mysql-\d{8}-\d{6}\.sql\.gz',p.name),'Unexpected retained backup path.')
    return Path(str(p))

def main():
    root=Path('/home/oyts/plan-platform');release=(root/'current').resolve()
    drill.require(release==root/'releases/0.1.25-4be0a01f','Expected accepted0.1.25 release.')
    os.umask(0o077);out=release/'post-release-verification.json'
    drill.require(not out.exists(),'Existing proof retained; inspect before any follow-up.')
    report=dict(status='FAIL',version='0.1.25',productionBusinessWrites=0,nativeDeviceTested=False,paidAiCalls=0,realTestEmails=0)
    command=drill.command
    mysql=json.loads(command(['docker','inspect','plan-platform-mysql-1']))[0]
    key=next(s.split('=',1)[1] for s in mysql['Config']['Env'] if s.startswith('MYSQL_ROOT_PASSWORD='))
    env=dict(os.environ,MYSQL_PWD=key)
    def sql(q):return command(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N'],q.encode(),env)
    try:
        health=json.loads(command(['curl','--max-time','10','-fsS','https://plan.zijun701.cn/api/health']))
        drill.require(health.get('version')=='0.1.25' and health.get('ok'),'Public health mismatch.')
        backend=json.loads(command(['docker','inspect','plan-platform-backend-1']))[0]
        values=dict(v.split('=',1) for v in backend['Config']['Env'] if '=' in v)
        drill.require(all(values.get(k)=='true' for k in ['METRICS_ENABLED','TELEMETRY_ENABLED','AI_ENABLED','EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED','MEMBER_GOVERNANCE_ENABLED'])
            and values.get('TELEMETRY_MAIL_ALERTS_ENABLED')=='false','Approved runtime flags differ.')
        cron=command(['crontab','-l'])
        drill.require(cron.count('# plan-platform bounded runtime metrics')==1 and 'daily-mysql-backup.sh' in cron and 'collect-operations.sh' in cron,'Schedules differ.')
        runtime_file=root/'operations/runtime.json'
        report['runtime']=runtime_facts(json.loads(runtime_file.read_text()),dt.datetime.now(dt.timezone.utc))
        drill.require(runtime_file.stat().st_gid==101 and runtime_file.stat().st_mode&0o777==0o640,'Runtime metadata permissions differ.')
        conf=command(['docker','exec','plan-platform-web-1','cat','/etc/nginx/conf.d/default.conf'])
        fmt=re.search(r'log_format plan_private_metrics ([^;]+);',conf)
        drill.require(fmt and not re.search('remote_addr|request_uri|user_agent|args|referer|authorization|cookie',fmt[1],re.I),'Missing or unsafe five-column log format.')
        command(['python3',str(release/'scripts/check-governance-rollback.py'),'--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','yes','--target-supports-metrics','yes'])
        output=command(['sh',str(release/'scripts/mysql-backup.sh')]);match=re.search(r'^Verified MySQL backup: (.+)$',output,re.M)
        drill.require(match is not None,'Verified backup path missing.')
        backup=backup_location(str(Path(match[1]).resolve() if Path(match[1]).is_absolute() else (release/match[1]).resolve()))
        command(['sh',str(release/'scripts/mysql-oss-upload.sh'),str(backup)])
        command(['sh',str(root/'collect-operations.sh'),'--verify-oss'])
        oss=json.loads((root/'operations/oss.json').read_text())
        digest=hashlib.sha256(backup.read_bytes()).hexdigest()
        drill.require(oss.get('verified') is True and oss.get('sha256')==digest,'Actual private OSS roundtrip differs.')
        database,proof=drill.snapshot_restore(backup,sql,env)
        drill.require(proof['tablesChecked']==62,'Expected complete V34 restore.')
        command(['python3',str(release/'scripts/operations-status.py'),'record-drill','--backup',str(backup),'--output',str(root/'operations/drill.json'),'--group-readable'])
        report.update(status='PASS',postUpgradeSnapshot=str(backup),backupSha256=digest,restoredDatabase=database,snapshotRestore=proof,
            ossRoundtripVerified=True,ossVerifiedAt=oss.get('verifiedAt'),rollbackGuardAccepted=True,retainedSchedules=True,
            collectorSha256=hashlib.sha256((release/'scripts/runtime-telemetry.py').read_bytes()).hexdigest())
    except Exception as e:
        report['errorType']=type(e).__name__
        raise
    finally:
        with out.open('x') as f:json.dump(report,f,indent=2)
        print(json.dumps(report))
if __name__=='__main__':main()
