#!/usr/bin/env python3
"""Isolated V31->V32 synthetic exercise; no production writes/provider/SMTP."""
import datetime as dt,gzip,hashlib,io,json,os,pathlib,re,secrets,subprocess,time,urllib.request,urllib.error,uuid
root=pathlib.Path('/home/oyts/plan-platform');directory=pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root)+r'/long-planning-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory))
os.umask(0o077)
with (directory/'started.json').open('x') as f:json.dump({'started':dt.datetime.now(dt.timezone.utc).isoformat()},f)
env=dict(os.environ)
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD'),'Restricted database authentication unavailable'
suffix=directory.name[-8:];db='long_'+suffix;restore='long_restore_'+suffix;user='long_'+suffix
port=43175;containers=[];report={'status':'FAIL','productionWrites':0,'paidAiCalls':0,'realEmails':0,'cases':[]}
def cmd(args,data=None):
    p=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=180)
    if p.returncode:
        (directory/'command-error.private').write_bytes(p.stderr)
        raise RuntimeError('Isolated command failed; private error retained')
    return p.stdout
def sql(text,database=None):
    return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',*([database] if database else [])],text.encode()).decode()
def api(path,data=None,token=None,status=200,method=None):
    req=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})},method=method)
    try:r=urllib.request.urlopen(req,timeout=45)
    except urllib.error.HTTPError as e:r=e
    with r:
        body=r.read();assert r.code==status,f'Unexpected synthetic status {r.code}, expected {status}, path {path}'
    return json.loads(body) if body else None
def start(name,new):
    assert not re.search(':'+str(port)+r'\s',cmd(['ss','-lnt']).decode()),'Synthetic port occupied'
    args=['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(directory/'candidate.env'),'--entrypoint','java']
    if new:args+=['-v',str(directory/'api.jar')+':/candidate/api.jar:ro']
    args+=['plan-platform-api:0.1.21','-jar','/candidate/api.jar' if new else '/app/api.jar','--app.ai.worker-enabled=false']
    cmd(args);containers.append(name)
    for _ in range(100):
        if cmd(['docker','inspect','--format','{{.State.Running}}',name]).strip()!=b'true':raise RuntimeError('Synthetic service exited; inspect private container log')
        try:
            if api('/health')['ok']:return
        except (OSError,AssertionError):pass
        time.sleep(2)
    raise RuntimeError('Synthetic readiness timeout')
def stop(name):cmd(['docker','stop',name])
def columns(database):
    tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+database+"' ORDER BY table_name").split()
    return {t:sql("SELECT column_name FROM information_schema.columns WHERE table_schema='"+database+"' AND table_name='"+t+"' ORDER BY ordinal_position").split() for t in tables}
def projections(database,cols,history=False):
    result={}
    for t,c in cols.items():
        if t=='flyway_schema_history' and not history:continue
        assert all(re.fullmatch('[a-zA-Z0-9_]+',x) for x in [t,*c])
        fields=','.join('`'+x+'`' for x in c)
        result[t]=hashlib.sha256(sql('SELECT '+fields+' FROM `'+t+'` ORDER BY '+fields,database).encode()).hexdigest()
    return result
def account():return api('/auth/register',{'email':'long-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24),'displayName':'长规划合成验收'},status=201)['accessToken']
def prepare(token,mode,count):
    s=api('/ai/sessions',{'mode':mode,'consent':True},token)
    draft={'schemaVersion':1,'mode':mode,'revision':0,'title':'合成完整大纲','startDate':'2026-11-02' if mode=='PLAN' else None,'endDate':'2026-11-30' if mode=='PLAN' else None,'restDays':[],
        'stages':[{'localId':'s1','title':'基础'}] if mode=='ROUTE' else [],'items':[]}
    for i in range(count):draft['items'].append({'localId':'t'+str(i),'title':'合成事项'+str(i),'note':'待细化','dueDate':None if mode=='ROUTE' else (dt.date(2026,11,2)+dt.timedelta(days=i)).isoformat(),'plannedMinutes':30,'stageId':'s1' if mode=='ROUTE' else None,'prerequisites':['t'+str(i-1)] if i and mode=='ROUTE' else []})
    return api('/ai/sessions/'+s['id']+'/draft',{'version':s['version'],'draftJson':json.dumps(draft)},token,method='PUT')
def flow(token,s):return api('/ai/sessions/'+s['id']+'/long-plan',{'version':s['version'],'coverage':'全部事项、日期与阶段依赖已核对','confirmed':True},token)
def rollback_blocked():
    # Old backend can read V32, but lacks the new protocol's write gate.
    return int(sql('SELECT (SELECT COUNT(*) FROM ai_long_plans)+(SELECT COUNT(*) FROM ai_sessions WHERE outline_only=TRUE)',db).strip())>0
try:
    expected=(directory/'api.jar.sha256').read_text().split()[0];assert hashlib.sha256((directory/'api.jar').read_bytes()).hexdigest()==expected
    (directory/'api.jar').chmod(0o644);report['apiSha256']=expected
    before=json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20));assert before['version']=='0.1.21'
    assert int(cmd(['awk','/MemAvailable:/ {print $2}','/proc/meminfo']).strip())>600000
    assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('"+db+"','"+restore+"')").strip()=='0'
    password=secrets.token_urlsafe(32)
    sql(f"CREATE DATABASE {db} CHARACTER SET utf8mb4; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {db}.* TO '{user}'@'%';")
    cfg={'SPRING_DATASOURCE_URL':f'jdbc:mysql://mysql:3306/{db}?serverTimezone=Asia/Shanghai','SPRING_DATASOURCE_USERNAME':user,'SPRING_DATASOURCE_PASSWORD':password,'SPRING_DATASOURCE_DRIVER_CLASS_NAME':'com.mysql.cj.jdbc.Driver',
        'JWT_SECRET':secrets.token_urlsafe(48),'JWT_ISSUER':'synthetic-long','REGISTRATION_ENABLED':'true','EMAIL_VERIFICATION_ENABLED':'false','EMAIL_CHANGE_ENABLED':'false','PASSWORD_RESET_ENABLED':'false','REFRESH_COOKIE_SECURE':'false',
        'AI_ENABLED':'true','AI_API_KEY_FILE':'/synthetic-key-does-not-exist','APP_AI_WORKER_ENABLED':'false','OPERATIONS_ENABLED':'false','MEMBER_GOVERNANCE_ENABLED':'false','OPERATIONS_MAIL_ALERTS_ENABLED':'false',
        'SPRING_AUTOCONFIGURE_EXCLUDE':'org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration','JAVA_TOOL_OPTIONS':'-Xms64m -Xmx224m -XX:MaxMetaspaceSize=144m -XX:ReservedCodeCacheSize=48m -Xss512k','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'2'}
    with (directory/'candidate.env').open('x') as f:f.write(''.join(k+'='+v+'\n' for k,v in cfg.items()))
    old='plan-long-old-'+suffix;start(old,False);token=account();existing=prepare(token,'ROUTE',12)
    assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',db).strip()=='31'
    oldcols=columns(db);original=projections(db,oldcols);stop(old)
    new='plan-long-new-'+suffix;start(new,True)
    assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',db).strip()=='32'
    assert projections(db,oldcols)==original;report['originalBusinessTablesUnchanged']=len(original);report['cases'].append('V31->V32 original column projections unchanged')
    assert not rollback_blocked();other=account()
    for mode in ['PLAN','ROUTE','TODO']:
        s=flow(token,existing if mode=='ROUTE' else prepare(token,mode,12));assert rollback_blocked()
        api('/ai/sessions/'+s['id']+'/apply',{'version':s['version'],'confirmed':True},token,status=409)
        api('/ai/sessions/'+s['id'],token=other,status=404)
        for batch in s['longPlan']['batches']:
            partial=dict(s['draft']);partial['items']=[dict(item,note='合成细化：练习、检查、回顾') for item in s['draft']['items'] if item['localId'] in batch['itemIds']]
            s=api('/ai/sessions/'+s['id']+'/batch',{'version':s['version'],'draftJson':json.dumps(partial)},token,method='PUT')
        assert s['longPlan']['complete'] and s['longPlan']['submissions']==0
        material=api('/ai/sessions/'+s['id']+'/collaboration/export',{'version':s['version'],'instructions':'只调整第一项练习说明','consent':True},token)
        assert set(material)=={'format','schemaVersion','reference','mode','instructions','protectedItemIds','draft','prompt'}
        material['draft']['items'][0]['note']='外部合成返回：练习十分钟并检查'
        source=json.dumps(material);body={'version':s['version'],'source':source}
        preview=api('/ai/sessions/'+s['id']+'/collaboration/preview',body,token);assert preview['binding']=='BOUND'
        assert api('/ai/sessions/'+s['id'],token=token)['draft']['items'][0]['note']!='外部合成返回：练习十分钟并检查'
        body.update(fingerprint=preview['fingerprint'],confirmed=True)
        s=api('/ai/sessions/'+s['id']+'/collaboration/accept',body,token)
        assert api('/ai/sessions/'+s['id']+'/collaboration/accept',body,token)['version']==s['version']
        api('/ai/sessions/'+s['id']+'/draft',{'version':s['version']-1,'draftJson':json.dumps(s['draft'])},token,status=409,method='PUT')
        applied=api('/ai/sessions/'+s['id']+'/apply',{'version':s['version'],'confirmed':True},token);assert applied['count']==12
        assert api('/ai/sessions/'+s['id']+'/apply',{'version':s['version'],'confirmed':True},token)==applied
    report['cases'].append('Three modes manual batches, collaboration readonly preview, draft accept replay, 409, owner and business apply replay')
    sql('UPDATE ai_quota_policy SET daily_limit=20 WHERE id=1',db)
    pending=flow(token,prepare(token,'TODO',11));body={'version':pending['version'],'message':'细化本批','intent':'BATCH','operationKey':str(uuid.uuid4())}
    first=api('/ai/sessions/'+pending['id']+'/jobs',body,token,status=202)
    assert api('/ai/sessions/'+pending['id']+'/jobs',body,token,status=202)['id']==first['id']
    p=api('/ai/sessions/'+pending['id'],token=token);assert p['longPlan']['submissions']==1 and p['longPlan']['reservedCalls']==2
    assert sql('SELECT COUNT(*) FROM ai_job_attempts',db).strip()=='0';assert rollback_blocked()
    report['cases'].append('Durable one-time budget admission, no upstream calls, conservative prior-backend write rollback blocked')
    stop(new);migrated=columns(db);expectedRows=projections(db,migrated,True)
    data=cmd(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',db])
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',data,re.I|re.M)
    zipped=io.BytesIO()
    with gzip.GzipFile(fileobj=zipped,mode='wb',mtime=0) as archive:archive.write(data)
    compressed=zipped.getvalue();(directory/'synthetic.sql.gz').write_bytes(compressed);report['backupSha256']=hashlib.sha256(compressed).hexdigest()
    sql(f'CREATE DATABASE {restore} CHARACTER SET utf8mb4;');sql(data.decode(),restore);assert projections(restore,migrated,True)==expectedRows
    report['restoredTables']=len(migrated);report['cases'].append('Full V32 synthetic dump restored, every table and Flyway projection equal')
    legacy='plan-long-read-compat-'+suffix;start(legacy,False);assert api('/ai/sessions/'+pending['id'],token=token)['id']==pending['id'];stop(legacy)
    report['cases'].append('Actual prior0.1.21 image read compatibility only; NOT write rollback approval')
    resumed='plan-long-resume-'+suffix;start(resumed,True)
    p=api('/ai/sessions/'+pending['id'],token=token);assert p['longPlan']['submissions']==1 and p['longPlan']['reservedCalls']==2
    stop(resumed);assert before==json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20));report['status']='PASS'
finally:
    for c in containers:
        try:
            if cmd(['docker','inspect','--format','{{.State.Running}}',c]).strip()==b'true':stop(c)
        except Exception:report['cleanupUnconfirmed']=True
    (directory/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'status':report['status'],'cases':len(report['cases']),'productionWrites':0,'privateEvidence':str(directory/'report.json')}))
