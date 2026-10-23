#!/usr/bin/env python3
"""Synthetic V30->V31 only. No production data, SMTP, provider calls or cutover."""
import datetime as dt,gzip,hashlib,io,json,os,pathlib,re,secrets,subprocess,time,urllib.request,urllib.error,uuid
root=pathlib.Path('/home/oyts/plan-platform');directory=pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root)+r'/reliability-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory))
os.umask(0o077)
with (directory/'started.json').open('x') as f:json.dump({'started':dt.datetime.now(dt.timezone.utc).isoformat()},f)
env=dict(os.environ)
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD'),'Restricted database authentication unavailable'
suffix=directory.name[-8:];db='reliability_'+suffix;restore='reliability_restore_'+suffix;user='rel_'+suffix
port=43172;containers=[];report={'status':'FAIL','productionWrites':0,'paidAiCalls':0,'realEmails':0,'cases':[]}
def cmd(args,data=None):
    p=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=180)
    if p.returncode:raise RuntimeError('Isolated command failed; private output retained, not disclosed')
    return p.stdout
def sql(text,database=None):
    return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',*([database] if database else [])],text.encode()).decode()
def api(path,data=None,token=None,status=200,method=None):
    req=urllib.request.Request('http://127.0.0.1:'+str(port)+'/api'+path,None if data is None else json.dumps(data).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})},method=method)
    try:r=urllib.request.urlopen(req,timeout=45)
    except urllib.error.HTTPError as e:r=e
    with r:
        body=r.read();assert r.code==status,'Unexpected synthetic API status'
    return json.loads(body) if body else None
def start(name,new):
    assert not re.search(':'+str(port)+r'\s',cmd(['ss','-lnt']).decode()),'Synthetic port occupied'
    args=['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(directory/'candidate.env'),'--entrypoint','java']
    if new:args+=['-v',str(directory/'api.jar')+':/candidate/api.jar:ro']
    args+=['plan-platform-api:0.1.20','-jar','/candidate/api.jar' if new else '/app/api.jar','--app.ai.worker-enabled=false']
    cmd(args);containers.append(name)
    for _ in range(100):
        if cmd(['docker','inspect','--format','{{.State.Running}}',name]).strip()!=b'true':raise RuntimeError('Synthetic service exited; inspect its private log')
        try:
            if api('/health')['ok']:return
        except (OSError,AssertionError):pass
        time.sleep(2)
    raise RuntimeError('Synthetic service readiness failed')
def stop(name):cmd(['docker','stop',name])
def columns(database):
    tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+database+"' ORDER BY table_name").split()
    return {t:sql("SELECT column_name FROM information_schema.columns WHERE table_schema='"+database+"' AND table_name='"+t+"' ORDER BY ordinal_position").split() for t in tables}
def projections(database,cols,include_history=False):
    result={}
    for t,c in cols.items():
        if t=='flyway_schema_history' and not include_history:continue
        assert all(re.fullmatch('[a-zA-Z0-9_]+',x) for x in [t,*c])
        fields=','.join('`'+x+'`' for x in c);rows=sql('SELECT '+fields+' FROM `'+t+'` ORDER BY '+fields,database)
        result[t]=hashlib.sha256(rows.encode()).hexdigest()
    return result
try:
    expected=(directory/'api.jar.sha256').read_text().split()[0];assert hashlib.sha256((directory/'api.jar').read_bytes()).hexdigest()==expected
    (directory/'api.jar').chmod(0o644);report['apiSha256']=expected
    before=json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20));assert before['version']=='0.1.20'
    assert int(cmd(['awk','/MemAvailable:/ {print $2}','/proc/meminfo']).strip())>600000,'Insufficient spare memory'
    assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('"+db+"','"+restore+"')").strip()=='0'
    password=secrets.token_urlsafe(32)
    sql(f"CREATE DATABASE {db} CHARACTER SET utf8mb4; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {db}.* TO '{user}'@'%';")
    cfg={'DB_URL':f'jdbc:mysql://mysql:3306/{db}?serverTimezone=Asia/Shanghai','DB_USERNAME':user,'DB_PASSWORD':password,
        'SPRING_DATASOURCE_URL':f'jdbc:mysql://mysql:3306/{db}?serverTimezone=Asia/Shanghai','SPRING_DATASOURCE_USERNAME':user,'SPRING_DATASOURCE_PASSWORD':password,'SPRING_DATASOURCE_DRIVER_CLASS_NAME':'com.mysql.cj.jdbc.Driver',
        'JWT_SECRET':secrets.token_urlsafe(48),'JWT_ISSUER':'synthetic-reliability','REGISTRATION_ENABLED':'true','EMAIL_VERIFICATION_ENABLED':'false','EMAIL_CHANGE_ENABLED':'false','PASSWORD_RESET_ENABLED':'false','REFRESH_COOKIE_SECURE':'false',
        'AI_ENABLED':'true','AI_API_KEY_FILE':'/synthetic-key-does-not-exist','APP_AI_WORKER_ENABLED':'false','OPERATIONS_ENABLED':'false','MEMBER_GOVERNANCE_ENABLED':'false','OPERATIONS_MAIL_ALERTS_ENABLED':'false',
        'SPRING_AUTOCONFIGURE_EXCLUDE':'org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration','JAVA_TOOL_OPTIONS':'-Xms64m -Xmx224m -XX:MaxMetaspaceSize=144m -XX:ReservedCodeCacheSize=48m -Xss512k','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'2'}
    with (directory/'candidate.env').open('x') as f:f.write(''.join(k+'='+v+'\n' for k,v in cfg.items()))
    old='plan-reliability-old-'+suffix;start(old,False)
    account=api('/auth/register',{'email':'synthetic-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24),'displayName':'合成可靠性'},status=201);token=account['accessToken']
    s=api('/ai/sessions',{'mode':'PLAN','consent':True},token)
    assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',db).strip()=='30'
    oldcols=columns(db);original=projections(db,oldcols);stop(old)
    new='plan-reliability-new-'+suffix;start(new,True)
    assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',db).strip()=='31'
    assert projections(db,oldcols)==original;report['originalBusinessTablesUnchanged']=len(original);report['cases'].append('V30->V31 old-column projections unchanged')
    s=api('/ai/sessions/'+s['id'],token=token);assert s['requirementsText']==''
    s=api('/ai/sessions/'+s['id']+'/requirements',{'text':'零基础，每天两小时，星期日休息','version':s['version']},token,method='PUT')
    api('/ai/sessions/'+s['id']+'/requirements',{'text':'stale','version':s['version']-1},token,status=409,method='PUT')
    other=api('/auth/register',{'email':'synthetic-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24),'displayName':'另一个合成账号'},status=201)
    api('/ai/sessions/'+s['id'],token=other['accessToken'],status=404)
    sql('UPDATE ai_quota_policy SET daily_limit=20 WHERE id=1',db)
    body={'message':'请先讨论，不生成','intent':'DIALOGUE','operationKey':str(uuid.uuid4()),'version':s['version']}
    first=api('/ai/sessions/'+s['id']+'/jobs',body,token,status=202);second=api('/ai/sessions/'+s['id']+'/jobs',body,token,status=202);assert first['id']==second['id']
    assert api('/ai/quota',token=token)['pending']==1
    api('/ai/sessions/'+s['id']+'/requirements',{'text':'busy','version':s['version']+1},token,status=409,method='PUT')
    sql("UPDATE ai_jobs SET state='RUNNING' WHERE id='"+first['id']+"'; INSERT INTO ai_job_attempts(job_id,attempt_no,state,input_tokens,output_tokens,started_at) VALUES ('"+first['id']+"',0,'REJECTED',100,200,NOW(6));",db)
    metadata=api('/ai/sessions/'+s['id'],token=token);assert metadata['turns'][0]['diagnostic']['attempts']==1;assert '星期日' in metadata['requirementsText']
    report['cases'].append('Requirements UTF8/version/owner and durable admission/metadata')
    stop(new)
    migrated=columns(db);expectedRows=projections(db,migrated,True)
    data=cmd(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',db])
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',data,re.I|re.M)
    zipped=io.BytesIO()
    with gzip.GzipFile(fileobj=zipped,mode='wb',mtime=0) as archive:archive.write(data)
    compressed=zipped.getvalue();(directory/'synthetic.sql.gz').write_bytes(compressed);report['backupSha256']=hashlib.sha256(compressed).hexdigest()
    sql(f'CREATE DATABASE {restore} CHARACTER SET utf8mb4;');sql(data.decode(),restore);assert projections(restore,migrated,True)==expectedRows
    assert sql('SELECT COUNT(*) FROM flyway_schema_history',restore)==sql('SELECT COUNT(*) FROM flyway_schema_history',db)
    report['restoredTables']=len(migrated);report['cases'].append('Synthetic full-dump restore all table projections')
    rollback='plan-reliability-rollback-'+suffix;start(rollback,False);legacy=api('/ai/sessions/'+s['id'],token=token);assert legacy['id']==s['id'];assert legacy['turns'][0]['state']=='RUNNING';stop(rollback)
    report['cases'].append('Actual previous0.1.20 image reads migrated synthetic data')
    after=json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20));assert before==after;report['status']='PASS'
finally:
    for c in containers:
        try:
            if cmd(['docker','inspect','--format','{{.State.Running}}',c]).strip()==b'true':stop(c)
        except Exception:report['cleanupUnconfirmed']=True
    (directory/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'status':report['status'],'cases':len(report['cases']),'productionWrites':0,'privateEvidence':str(directory/'report.json')}))
