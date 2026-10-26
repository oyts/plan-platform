#!/usr/bin/env python3
"""Approved isolated V33->V34 metrics exercise. No production DB or secret mounts."""
import concurrent.futures,datetime as dt,gzip,hashlib,io,json,os,pathlib,re,secrets,subprocess,sys,time,traceback,urllib.request,urllib.error,uuid

def main():
    root=pathlib.Path('/home/oyts/plan-platform');folder=pathlib.Path.cwd().resolve()
    assert re.fullmatch(str(root)+r'/metrics-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(folder))
    assert sys.argv[1:] in [[],['--resume-after-migration']];resume=bool(sys.argv[1:]);prior=json.loads((folder/'report.json').read_text()) if resume else None
    if resume:assert prior['status']=='FAIL' and prior['errorType']=='JSONDecodeError' and len(prior['cases'])==1
    os.umask(0o077)
    with (folder/('resume-started.json' if resume else 'started.json')).open('x') as f:json.dump({'started':dt.datetime.now(dt.timezone.utc).isoformat()},f)
    suffix=folder.name[-8:];database='plan_metrics_'+suffix;restored='metrics_restore_'+suffix;user='metrics_test_'+suffix
    port=43181;containers=[];env=dict(os.environ);report=dict(status='FAIL',productionWrites=0,paidAiCalls=0,realEmails=0,cases=[])
    for line in (root/'current/.env').read_text().splitlines():
        if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
    assert env.get('MYSQL_PWD'),'Restricted DB authentication unavailable'
    def cmd(argv,data=None):
        p=subprocess.run(argv,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=180)
        if p.returncode:
            (folder/'command-error.private').write_bytes(p.stderr)
            raise RuntimeError('Isolated command failed; private error retained')
        return p.stdout
    def sql(statement,db=None):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',*([db] if db else [])],statement.encode()).decode().strip()
    def api(path,data=None,token=None,status=200,method=None,extra=None):
        headers={'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {}),**(extra or {})}
        req=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data,ensure_ascii=False).encode(),headers,method=method)
        try:r=urllib.request.urlopen(req,timeout=45)
        except urllib.error.HTTPError as e:r=e
        with r:body=r.read();assert r.code==status,f'Synthetic status {r.code}, expected {status}'
        return json.loads(body) if body and status<400 else None
    def health():return json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20))
    def start(label,new=True):
        assert not re.search(':'+str(port)+r'\s',cmd(['ss','-lnt']).decode()),'Isolated port occupied'
        name='plan-metrics-'+('resume-' if resume else '')+label+'-'+suffix
        args=['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(folder/'candidate.env'),'--entrypoint','java']
        if new:args+=['-v',str(folder/'api.jar')+':/candidate/api.jar:ro']
        cmd(args+['plan-platform-api:0.1.23','-jar','/candidate/api.jar' if new else '/app/api.jar','--app.ai.worker-enabled=false']);containers.append(name)
        for _ in range(120):
            assert cmd(['docker','inspect','--format','{{.State.Running}}',name]).strip()==b'true','Synthetic service exited'
            try:
                if api('/health')['ok']:return name
            except (OSError,AssertionError):pass
            time.sleep(2)
        raise RuntimeError('Isolated readiness timeout')
    def stop(name):cmd(['docker','stop',name])
    def columns(db):return {t:sql("SELECT column_name FROM information_schema.columns WHERE table_schema='"+db+"' AND table_name='"+t+"' ORDER BY ordinal_position").split() for t in sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name").split()}
    def projections(db,tables,history=False):
        answer={}
        for table,cols in tables.items():
            if table=='flyway_schema_history' and not history:continue
            assert all(re.fullmatch('[a-zA-Z0-9_]+',s) for s in [table,*cols]);fields=','.join('`'+s+'`' for s in cols)
            answer[table]=hashlib.sha256(sql('SELECT '+fields+' FROM `'+table+'` ORDER BY '+fields,db).encode()).hexdigest()
        return answer
    def account(role):
        credentials={'email':'metrics-mysql-'+uuid.uuid4().hex+'@example.test','password':'Synthetic-'+secrets.token_urlsafe(28)+'1','displayName':'统计合成'+role}
        identity=api('/auth/register',credentials,status=201)
        if role=='ADMIN':sql("UPDATE users SET role='ADMIN' WHERE id='"+identity['user']['id']+"'",database)
        return api('/auth/login',{k:credentials[k] for k in ['email','password']})
    origin={'Origin':f'http://127.0.0.1:{port}'}
    def ticket():return api('/public/analytics/ticket',{'page':'WELCOME'},extra=origin)['ticket']
    def event(t,visitor=None,status=204):return api('/public/analytics/event',{'page':'WELCOME','ticket':t,'visitorId':visitor},status=status,extra=origin)
    try:
        expected=(folder/'api.jar.sha256').read_text().split()[0];assert re.fullmatch('[a-f0-9]{64}',expected) and hashlib.sha256((folder/'api.jar').read_bytes()).hexdigest()==expected
        (folder/'api.jar').chmod(0o644);report['apiSha256']=expected;before=health();assert before['version']=='0.1.23'
        assert int(next(line.split()[1] for line in pathlib.Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemAvailable:')))>600000
        if resume:
            assert prior['apiSha256']==expected and prior['originalProjectionTables']==58
            assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',database)=='34'
            assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+restored+"'")=='0'
            config=dict(line.split('=',1) for line in (folder/'candidate.env').read_text().splitlines());assert config['SPRING_DATASOURCE_USERNAME']==user and config['JWT_ISSUER']=='synthetic-metrics'
            report['originalProjectionTables']=58;report['cases']=prior['cases'].copy();report['parentReport']='report.json'
        else:
            assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('"+database+"','"+restored+"')")=='0'
            password=secrets.token_urlsafe(32);sql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {database}.* TO '{user}'@'%';")
            cfg={'SPRING_DATASOURCE_URL':f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai','SPRING_DATASOURCE_USERNAME':user,'SPRING_DATASOURCE_PASSWORD':password,'SPRING_DATASOURCE_DRIVER_CLASS_NAME':'com.mysql.cj.jdbc.Driver','JWT_SECRET':secrets.token_urlsafe(48),'JWT_ISSUER':'synthetic-metrics','REGISTRATION_ENABLED':'true','INVITATION_ENABLED':'true','EMAIL_VERIFICATION_ENABLED':'false','EMAIL_CHANGE_ENABLED':'false','PASSWORD_RESET_ENABLED':'false','REFRESH_COOKIE_SECURE':'false','AI_ENABLED':'true','AI_API_KEY_FILE':'/synthetic-never-mounted','AI_DEFAULT_DAILY_LIMIT':'20','APP_AI_WORKER_ENABLED':'false','OPERATIONS_ENABLED':'false','MEMBER_GOVERNANCE_ENABLED':'false','OPERATIONS_MAIL_ALERTS_ENABLED':'false','METRICS_ENABLED':'true','APP_PUBLIC_URL':f'http://127.0.0.1:{port}','TELEMETRY_ENABLED':'false','TELEMETRY_MAIL_ALERTS_ENABLED':'false','SPRING_AUTOCONFIGURE_EXCLUDE':'org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration','JAVA_TOOL_OPTIONS':'-Xms64m -Xmx224m -XX:MaxMetaspaceSize=144m -XX:ReservedCodeCacheSize=48m -Xss512k','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'2'}
            with (folder/'candidate.env').open('x') as f:f.write(''.join(k+'='+v+'\n' for k,v in cfg.items()))
            old=start('v33',False);admin=account('ADMIN');member=account('USER');a=admin['accessToken'];u=member['accessToken'];assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',database)=='33'
            oldcols=columns(database);original=projections(database,oldcols);stop(old)
            new=start('v34');assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',database)=='34';assert projections(database,oldcols)==original
            stop(new);legacy=start('v33-read',False);assert api('/auth/me',token=u)['id']==member['user']['id'];stop(legacy)
            report['originalProjectionTables']=len(original);report['cases'].append('V33->V34 all original-column projections unchanged; actual prior-image read compatibility before telemetry writes')
        new=start('exercise')
        if resume:
            admin=account('ADMIN');member=account('USER');a=admin['accessToken'];u=member['accessToken']
        today=dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date();dates=f'?from={today}&to={today}'
        for part in ['growth','website','runtime']:api('/admin/metrics/'+part+('' if part=='runtime' else dates),token=u,status=403)
        growth=api('/admin/metrics/growth'+dates,token=a);assert growth['totalRegistered']==(4 if resume else 2) and growth['registeredInRange']==(4 if resume else 2) and growth['timezone']=='Asia/Shanghai'
        api('/public/analytics/ticket',{'page':'WELCOME'},status=403,extra={'Origin':'https://unrelated.example'})
        api('/public/analytics/ticket',{'page':'WELCOME','url':'private?token=not-stored'},status=400,extra=origin)
        once=ticket()
        with concurrent.futures.ThreadPoolExecutor(2) as pool:list(pool.map(lambda _:event(once),range(2)))
        visitor=str(uuid.uuid4());event(ticket(),visitor);event(ticket(),visitor)
        aggregate=api('/admin/metrics/website'+dates,token=a);assert aggregate['pv']==3 and aggregate['dailyVisitorSum']==1 and aggregate['withoutVisitorEstimate']==1
        assert sql('SELECT COUNT(*) FROM web_metric_events',database)=='3';assert sql('SELECT COUNT(*) FROM web_metric_visitors',database)=='1';assert visitor not in sql('SELECT visitor_hash FROM web_metric_visitors',database)
        assert not any(k in json.dumps(aggregate) for k in ['private?','accessToken','password','@example.test']);report['cases'].append('MySQL serialization/idempotent replay, daily PV/UV/null identity, whitelist/origin and USER403')
        # Induced constraint failure must roll back the ticket row and all counters.
        sql('ALTER TABLE web_metric_days ADD CONSTRAINT synthetic_metrics_reject CHECK(pv<=3)',database);blocked=ticket();event(blocked,status=500);assert sql('SELECT COUNT(*) FROM web_metric_events',database)=='3';sql('ALTER TABLE web_metric_days DROP CHECK synthetic_metrics_reject',database);event(blocked)
        assert api('/admin/metrics/website'+dates,token=a)['pv']==4
        sql('UPDATE web_metric_events SET created_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 49 HOUR); UPDATE web_metric_visitors SET created_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 49 HOUR);',database)
        # Scheduled expiry runs every minute, even if analysis collection is later disabled.
        for _ in range(90):
            if sql('SELECT COUNT(*) FROM web_metric_events',database)=='0':break
            time.sleep(2)
        else:raise AssertionError('48h expiry did not run')
        assert sql('SELECT COUNT(*) FROM web_metric_visitors',database)=='0';assert api('/admin/metrics/website'+dates,token=a)['pv']==4
        assert api('/admin/ai/usage',token=a)['attempts']['waitKnownJobs']==0;assert sql('SELECT COUNT(*) FROM ai_job_attempts',database)=='0';report['cases'].append('Constraint rollback, same ticket safe retry, 48h expiry without removing daily aggregates/users, AI aggregate SQL on MySQL')
        cols=columns(database);expectedRows=projections(database,cols,True);stop(new)
        dump=cmd(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',database]);assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',dump,re.I|re.M)
        zipped=io.BytesIO()
        with gzip.GzipFile(fileobj=zipped,mode='wb',mtime=0) as archive:archive.write(dump)
        data=zipped.getvalue();(folder/'synthetic.sql.gz').write_bytes(data);report['backupSha256']=hashlib.sha256(data).hexdigest();sql(f'CREATE DATABASE {restored} CHARACTER SET utf8mb4;');sql(dump.decode(),restored);assert projections(restored,cols,True)==expectedRows
        report['restoredTables']=len(cols);report['cases'].append('All V34 tables restored and compared; original database retained, no production writes')
        (folder/'logs').mkdir();nginx='plan-metrics-nginx-'+suffix;containers.append(nginx)
        cmd(['docker','run','--name',nginx,'--network','plan-platform_default','--memory','64m','--cpus','0.2','-v',str(folder/'nginx.conf')+':/etc/nginx/conf.d/default.conf:ro','-v',str(folder/'logs')+':/var/log/plan-metrics','--entrypoint','nginx','plan-platform-web:0.1.23','-t'])
        cmd(['python3',str(folder/'metrics-linux-probe.py')]);probe=json.loads((folder/'linux-report.json').read_text());assert probe['status']=='PASS';report['linux']=probe;report['cases'].append('Actual retained Linux/Nginx syntax and bounded synthetic collector resource baseline')
        assert before==health();report['status']='PASS'
    except Exception as failure:
        report['errorType']=type(failure).__name__;report['failedLine']=traceback.extract_tb(failure.__traceback__)[-1].lineno;raise
    finally:
        for name in containers:
            try:
                if cmd(['docker','inspect','--format','{{.State.Running}}',name]).strip()==b'true':stop(name)
            except Exception:report['cleanupUnconfirmed']=True
        target=folder/('resume-report.json' if resume else 'report.json');target.write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'status':report['status'],'cases':len(report['cases']),'productionWrites':0,'privateEvidence':str(target)}))
if __name__=='__main__':main()
