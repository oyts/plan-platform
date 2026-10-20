#!/usr/bin/env python3
"""Isolated V25 -> V27 rehearsal; no production writes, provider calls or SMTP."""
import datetime as dt,gzip,hashlib,json,os,pathlib,re,secrets,subprocess,time,urllib.request,urllib.error,uuid

root=pathlib.Path('/home/oyts/plan-platform');directory=pathlib.Path.cwd().resolve()
if not re.fullmatch(str(root)+r'/reflection-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory)):
    raise SystemExit('Run only in a new managed reflection candidate directory')
os.umask(0o077)
with (directory/'started.json').open('x') as stream:json.dump({'at':dt.datetime.now(dt.timezone.utc).isoformat()},stream)
settings={}
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):settings['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert settings.get('MYSQL_PWD'),'Restricted MySQL authentication unavailable'
environment={**os.environ,**settings};suffix=directory.name[-8:];stamp=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')
database='plan_acceptance_'+stamp+'_'+suffix;restored='plan_restore_'+stamp+'_'+suffix
user='reflect_'+suffix;password=secrets.token_hex(24);jwt=secrets.token_hex(32)
old='plan-reflection-old-'+suffix;new='plan-reflection-new-'+suffix;rollback='plan-reflection-rollback-'+suffix
report={'status':'FAIL','productionWrites':0,'paidAiCalls':0,'realEmailsSent':0,'cases':[]}
def process(args,body=None,check=True):
    r=subprocess.run(args,input=body,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=environment,timeout=180)
    if check and r.returncode:raise RuntimeError('Isolated command failed; private output was not printed')
    return r
def sql(statement,db=None):
    return process(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N','--batch',*([db] if db else [])],statement.encode()).stdout.decode()
def api(port,path,data=None,token=None,expected=200,method=None):
    req=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})},method=method)
    try:r=urllib.request.urlopen(req,timeout=20)
    except urllib.error.HTTPError as e:r=e
    assert r.code==expected,(path,r.code,expected)
    body=r.read();return json.loads(body) if body else None
def start(name,port,candidate):
    assert not re.search(r':'+str(port)+r'\s',process(['ss','-lnt']).stdout.decode()),'Port occupied; no process stopped'
    cmd=['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(directory/'candidate.env')]
    if candidate:cmd+=['--entrypoint','java','-v',str(directory/'api.jar')+':/candidate/api.jar:ro','plan-platform-api:0.1.17','-Xms64m','-Xmx240m','-XX:MaxMetaspaceSize=128m','-jar','/candidate/api.jar']
    else:cmd+=['plan-platform-api:0.1.17']
    process(cmd)
    for _ in range(90):
        if process(['docker','inspect','--format','{{.State.Running}}',name]).stdout.strip()!=b'true':
            raise RuntimeError('Isolated container exited; inspect its private logs before retrying')
        try:
            if api(port,'/health')['service']=='plan-platform-api':return
        except (OSError,AssertionError):pass
        time.sleep(2)
    raise RuntimeError('Isolated candidate not ready')
def checksums(db):
    names=sql(f"select table_name from information_schema.tables where table_schema='{db}' order by table_name").split()
    result={}
    for table in names:
        assert re.fullmatch('[a-zA-Z0-9_]+',table)
        result[table]=sql(f'CHECKSUM TABLE {db}.{table} EXTENDED').split()[-1];assert result[table]!='NULL'
    return result
def note(text,version=None):return {'wins':text,'blockers':'合成阻塞','nextFocus':'小步推进','energy':'LOW','burden':'HEAVY','reviewed':True,'version':version}
try:
    expected=(directory/'api.jar.sha256').read_text().split()[0];assert hashlib.sha256((directory/'api.jar').read_bytes()).hexdigest()==expected;report['jarSha256']=expected
    live=json.loads(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15).read());assert live['version']=='0.1.17'
    sql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {database}.* TO '{user}'@'%';")
    env={'SPRING_PROFILES_ACTIVE':'mysql','DB_URL':f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai','DB_USERNAME':user,'DB_PASSWORD':password,'JWT_SECRET':jwt,'REGISTRATION_ENABLED':'true','EMAIL_VERIFICATION_ENABLED':'false','PASSWORD_RESET_ENABLED':'false','MEMBER_GOVERNANCE_ENABLED':'false','OPERATIONS_ENABLED':'false','OPERATIONS_MAIL_ALERTS_ENABLED':'false','AI_ENABLED':'false','REFRESH_COOKIE_SECURE':'false','JAVA_TOOL_OPTIONS':'-Xms64m -Xmx240m -XX:MaxMetaspaceSize=128m -Dapp.operations.scheduler-enabled=false'}
    (directory/'candidate.env').write_text(''.join(k+'='+v+'\n' for k,v in env.items()))
    start(old,43155,False)
    accounts=[api(43155,'/auth/register',{'email':f'synthetic-{x}@example.test','password':'SyntheticReflection123!','displayName':'合成复盘验收'},expected=201) for x in ['a','b']]
    token=accounts[0]['accessToken'];other=accounts[1]['accessToken'];owner=accounts[0]['user']['id'];assert re.fullmatch('[a-f0-9-]{36}',owner)
    plan=api(43155,'/plans',{'name':'合成迁移计划','startDate':'2026-10-12','endDate':'2026-10-25','restDays':[7]},token,201)
    tasks=[]
    for i in range(3):
        t=api(43155,'/plans/'+plan['id']+'/tasks',{'date':'2026-10-18','startTime':'09:30','endTime':'10:00','plannedMinutes':30,'title':f'合成迁移任务{i}','category':'STUDY','required':True,'sortOrder':i,'acceptance':[]},token,201)
        assert re.fullmatch('[a-f0-9-]{36}',t['id']);tasks.append(t['id'])
    sql(f"update task_progress set status='PAUSED',accumulated_seconds=0,started_at=null where task_id='{tasks[1]}';update task_progress set status='COMPLETED',completed_at='2026-10-18 14:00:00' where task_id='{tasks[2]}'",database)
    api(43155,'/weekly/reviews/2026-10-12',{'wins':'合成旧周复盘','blockers':'','nextFocus':'保留原文'},token,method='PUT')
    before=sql('select task_id,status,accumulated_seconds,started_at,completed_at,version from task_progress order by task_id',database)
    assert sql("select max(cast(version as unsigned)) from flyway_schema_history where success=1",database).strip()=='25'
    process(['docker','stop',old]);start(new,43156,True)
    assert before==sql('select task_id,status,accumulated_seconds,started_at,completed_at,version from task_progress order by task_id',database)
    flags=dict(line.split('\t') for line in sql('select task_id,ever_started from task_progress',database).splitlines());assert flags[tasks[0]]=='0' and flags[tasks[1]]=='1' and flags[tasks[2]]=='1'
    legacy=api(43156,'/weekly/reviews/2026-10-12',token=token);assert legacy['wins']=='合成旧周复盘' and legacy['reviewed'] and legacy['energy']=='UNSPECIFIED' and not legacy['openToIncrease']
    report['cases'].append('Actual 0.1.17 V25 -> V27, stable old progress and V26 started evidence, legacy weekly notes preserved')
    saved=api(43156,'/reflection/days/2026-10-18',note('合成日复盘'),token,method='PUT');assert saved['version']==0
    api(43156,'/reflection/days/2026-10-18',note('不应覆盖'),token,409,'PUT');assert api(43156,'/reflection/days/2026-10-18',token=other)['wins']==''
    facts=api(43156,'/reflection/facts?kind=DAY&start=2026-10-18',token=token);assert facts['plan']['arranged']==3 and facts['plan']['actuallyCompleted']==1
    prefs=api(43156,'/reflection/preferences',token=token);assert not prefs['dailyEnabled'] and not prefs['weeklyEnabled'];prefs['dailyEnabled']=True
    api(43156,'/reflection/preferences',prefs,token,method='PUT');api(43156,'/reflection/preferences',prefs,token,409,'PUT')
    report['cases'].append('MySQL day facts, ownership, optimistic409 and default-off independent preferences')
    preview=api(43156,'/reflection/analysis-preview',{'kind':'DAY','start':'2026-10-18','includeNotes':True},token)
    assert '合成日复盘' in preview['contentJson'] and token not in preview['contentJson'] and owner not in preview['contentJson']
    assert sql('select count(*) from ai_jobs',database).strip()=='0';report['cases'].append('Consent snapshot generated locally, no provider or paid jobs')
    backup=api(43156,'/data/backup',token=token);assert backup['schemaVersion']==3
    p=api(43156,'/data/preview',backup,other);body={'backup':backup,'checksum':p['checksum'],'operationKey':str(uuid.uuid4()),'confirmed':True}
    restored_api=api(43156,'/data/restore',body,other);assert api(43156,'/data/restore',body,other)==restored_api
    copies=api(43156,'/data/restored-daily-reviews',token=other);assert copies['content'][0]['wins']=='合成日复盘';assert api(43156,'/reflection/days/2026-10-18',token=other)['wins']==''
    report['cases'].append('Schema3 restore remaps records, keeps reviews readonly and repeated confirmation idempotent')
    process(['docker','stop',new]);snapshot=checksums(database)
    dump=process(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',database]).stdout
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',dump,re.I|re.M)
    file=directory/(database+'-backup.sql.gz');file.write_bytes(gzip.compress(dump));report['backupSha256']=hashlib.sha256(file.read_bytes()).hexdigest()
    sql(f'CREATE DATABASE {restored} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;');sql(gzip.decompress(file.read_bytes()).decode(),restored)
    assert snapshot==checksums(restored);report['tablesRestored']=len(snapshot);report['cases'].append('Full isolated MySQL8.4 restore, all table checksums equal')
    start(rollback,43155,False);assert api(43155,'/plans',token=token)[0]['name']=='合成迁移计划';assert api(43155,'/weekly/reviews/2026-10-12',token=token)['wins']=='合成旧周复盘'
    process(['docker','stop',rollback]);assert snapshot==checksums(database);report['cases'].append('Actual previous image reads migrated database without modifying it')
    assert json.loads(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15).read())==live
    assert not json.loads(urllib.request.urlopen('https://plan.zijun701.cn/api/auth/config',timeout=15).read())['registrationEnabled']
    report.update(status='PASS',database=database,restoreDatabase=restored,productionVersion='0.1.17')
finally:
    for name in [new,old,rollback]:process(['docker','stop',name],check=False)
    report['at']=dt.datetime.now(dt.timezone.utc).isoformat();(directory/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,ensure_ascii=True))
