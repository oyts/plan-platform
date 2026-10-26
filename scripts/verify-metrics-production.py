"""Read-only production safety checks plus isolated restore of accepted V34 dump."""
import datetime as dt,hashlib,importlib.util,json,os,re,subprocess,time
from pathlib import Path
spec=importlib.util.spec_from_file_location('metric_snapshot',Path(__file__).with_name('roadmap-release-drill.py'));drill=importlib.util.module_from_spec(spec);spec.loader.exec_module(drill)

def main():
    root=Path('/home/oyts/plan-platform');release=(root/'current').resolve();drill.require(release==root/'releases/0.1.24-a4a0d911','Expected accepted release.')
    os.umask(0o077);out=release/'post-release-verification-v2.json';drill.require(not out.exists(),'Existing proof retained; do not repeat blindly.')
    report=dict(status='FAIL',version='0.1.24',productionWrites=0,androidTouched=False)
    def command(args,data=None,env=None):return drill.command(args,data,env)
    mysql=json.loads(command(['docker','inspect','plan-platform-mysql-1']))[0];key=next(s.split('=',1)[1] for s in mysql['Config']['Env'] if s.startswith('MYSQL_ROOT_PASSWORD='));env=dict(os.environ,MYSQL_PWD=key)
    def sql(q):return command(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N'],q.encode(),env)
    try:
        import urllib.request
        health=json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=10));drill.require(health['version']=='0.1.24' and health['ok'],'Public health mismatch.')
        runtime=json.loads((root/'operations/runtime.json').read_text());drill.require(runtime['observedAt'].endswith('+00:00'),'Expected UTC collector timestamp.');observed=dt.datetime.strptime(runtime['observedAt'][:19],'%Y-%m-%dT%H:%M:%S').replace(tzinfo=dt.timezone.utc);age=(dt.datetime.now(dt.timezone.utc)-observed).total_seconds()
        drill.require(0<=age<150 and runtime['healthy'] and runtime['logComplete'] and len(runtime['samples'])>=3,'Actual scheduled runtime evidence incomplete or stale.')
        drill.require(runtime['schemaVersion']==1 and (root/'operations/runtime.json').stat().st_gid==101 and (root/'operations/runtime.json').stat().st_mode&0o777==0o640,'Runtime private metadata permissions differ.')
        running=json.loads(command(['docker','inspect','plan-platform-backend-1']))[0];values=dict(s.split('=',1) for s in running['Config']['Env'] if '=' in s)
        drill.require(all(values.get(k)=='true' for k in ['METRICS_ENABLED','TELEMETRY_ENABLED','AI_ENABLED','EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED','MEMBER_GOVERNANCE_ENABLED']),'Approved runtime flags changed.')
        drill.require(values.get('TELEMETRY_MAIL_ALERTS_ENABLED')=='false','Metric mail must remain disabled.')
        cron=command(['crontab','-l']);drill.require(cron.count('# plan-platform bounded runtime metrics')==1 and 'daily-mysql-backup.sh' in cron and 'collect-operations.sh' in cron,'Schedules differ.')
        conf=command(['docker','exec','plan-platform-web-1','cat','/etc/nginx/conf.d/default.conf']);fmt=re.search(r'log_format plan_private_metrics ([^;]+);',conf)
        drill.require(fmt and not re.search('remote_addr|request_uri|user_agent|args|referer|authorization|cookie',fmt[1],re.I),'Unsafe or missing sanitized log format.')
        report['runtime']=dict(scheduledSamples=len(runtime['samples']),ageSeconds=round(age),healthy=True,complete=True,mailEnabled=False,collectorSha256=hashlib.sha256((release/'scripts/runtime-telemetry.py').read_bytes()).hexdigest())
        # The old server must now be refused because actual anonymous days exist.
        check=subprocess.run(['python3',str(release/'scripts/check-governance-rollback.py'),'--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','yes','--target-supports-metrics','no'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        drill.require(check.returncode!=0 and b'Rollback refused: metrics retention' in check.stderr,'Expected retention-aware rollback refusal missing.')
        command(['python3',str(release/'scripts/check-governance-rollback.py'),'--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','yes','--target-supports-metrics','yes'])
        backup=root/'releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261026-114509.sql.gz'
        database,proof=drill.snapshot_restore(backup,sql,env);drill.require(proof['tablesChecked']==62,'Expected complete V34 restore.')
        report.update(status='PASS',postUpgradeSnapshot=str(backup),restoredDatabase=database,snapshotRestore=proof,oldWriterRefused=True,newWriterGuardAccepted=True)
        # Only save freshness evidence after all62 table comparisons succeeded.
        command(['python3',str(release/'scripts/operations-status.py'),'record-drill','--backup',str(backup),'--output',str(root/'operations/drill.json'),'--group-readable'])
    finally:
        with out.open('x') as f:json.dump(report,f,indent=2)
        print(json.dumps(report))
if __name__=='__main__':main()
