#!/usr/bin/env python3
"""Isolated V24 rehearsal. Synthetic data only; no SMTP/key mount or live cutover."""
import datetime as dt,gzip,hashlib,json,os,pathlib,re,secrets,subprocess,time,urllib.request,urllib.error

root=pathlib.Path('/home/oyts/plan-platform')
directory=pathlib.Path.cwd().resolve()
if not re.fullmatch(str(root)+r'/mail-operations-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory)):
    raise SystemExit('Run only in a new managed mail-operations candidate directory')
os.umask(0o077)
with (directory/'started.json').open('x') as stream:json.dump({'startedAt':dt.datetime.now(dt.timezone.utc).isoformat()},stream)
settings={}
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):settings['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert settings.get('MYSQL_PWD'),'Existing restricted MySQL authentication unavailable'
environment={**os.environ,**settings}
run=directory.name;stamp=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S');suffix=run[-8:]
database='plan_acceptance_'+stamp+'_'+suffix;restored='plan_restore_'+stamp+'_'+suffix
user='mail_'+suffix;password=secrets.token_hex(24);jwt=secrets.token_hex(32)
new='plan-mail-new-'+suffix;old='plan-mail-old-'+suffix
report={'version':'0.1.16','status':'FAIL','paidAiCalls':0,'realEmailsSent':0,'productionWrites':0,'cases':[]}
def process(args,body=None,check=True):
    result=subprocess.run(args,input=body,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=environment,timeout=180)
    if check and result.returncode:raise RuntimeError('Isolated command failed; private output was not printed')
    return result
def sql(statement,db=None,check=True):
    return process(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N','--batch',*( [db] if db else [])],statement.encode(),check)
def api(port,path,data=None,token=None,expected=200):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    request=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data).encode(),headers)
    try:response=urllib.request.urlopen(request,timeout=20)
    except urllib.error.HTTPError as error:response=error
    assert response.code==expected,(path,response.code,expected)
    body=response.read();return json.loads(body) if body else None
def checksums(db):
    names=sql(f"select table_name from information_schema.tables where table_schema='{db}' order by table_name").stdout.decode().split()
    result={}
    for table in names:
        assert re.fullmatch('[a-zA-Z0-9_]+',table)
        result[table]=sql(f'CHECKSUM TABLE {db}.{table} EXTENDED').stdout.decode().split()[-1]
        assert result[table]!='NULL'
    return result
def start(name,port,candidate):
    for portline in process(['ss','-lnt']).stdout.decode().splitlines():
        assert not re.search(r':'+str(port)+r'\s',portline),'Candidate loopback port occupied; no process stopped'
    command=['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(directory/'candidate.env')]
    if candidate:command+=['--entrypoint','java','-v',str(directory/'api.jar')+':/candidate/api.jar:ro','plan-platform-api:0.1.15','-Xms64m','-Xmx240m','-XX:MaxMetaspaceSize=128m','-jar','/candidate/api.jar']
    else:command+=['plan-platform-api:0.1.15']
    process(command)
    for _ in range(90):
        try:
            health=api(port,'/health')
            if health['version']==('0.1.16' if candidate else '0.1.15'):return
        except (OSError,AssertionError):pass
        time.sleep(2)
    raise RuntimeError('Isolated candidate did not become ready')
try:
    expected=(directory/'api.jar.sha256').read_text().split()[0]
    assert hashlib.sha256((directory/'api.jar').read_bytes()).hexdigest()==expected
    report['jarSha256']=expected
    sql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {database}.* TO '{user}'@'%';")
    env={'SPRING_PROFILES_ACTIVE':'mysql','DB_URL':f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai','DB_USERNAME':user,'DB_PASSWORD':password,'JWT_SECRET':jwt,'REGISTRATION_ENABLED':'true','EMAIL_VERIFICATION_ENABLED':'true','PASSWORD_RESET_ENABLED':'false','MEMBER_GOVERNANCE_ENABLED':'true','OPERATIONS_ENABLED':'true','OPERATIONS_MAIL_ALERTS_ENABLED':'false','AI_ENABLED':'false','REFRESH_COOKIE_SECURE':'false','JAVA_TOOL_OPTIONS':'-Xms64m -Xmx240m -XX:MaxMetaspaceSize=128m -Dapp.operations.scheduler-enabled=false'}
    (directory/'candidate.env').write_text(''.join(k+'='+v+'\n' for k,v in env.items()))
    start(new,43145,True)
    account=api(43145,'/auth/register',{'email':'synthetic-mail@example.test','password':'SyntheticOnly12345','displayName':'合成邮件验收'},expected=201)
    token=account['accessToken'];owner=account['user']['id'];assert re.fullmatch('[a-f0-9-]{36}',owner)
    status=api(43145,'/auth/email-verification/status',token=token);assert status['enabled'] and not status['configured'] and not status['verified']
    api(43145,'/auth/email-verification/request',{},token,503);api(43145,'/auth/email-verification/request',{},token,429)
    assert api(43145,'/auth/email-verification/status',token=token)['retryAfterSeconds']>0
    assert api(43145,'/plans',token=token)==[]
    report['cases'].append('MySQL V1-V24, authenticated status/cooldown/visible SMTP absence, unverified account still usable')
    sql(f"update users set role='ADMIN' where id='{owner}'",database)
    admin=api(43145,'/auth/login',{'email':'synthetic-mail@example.test','password':'SyntheticOnly12345'})['accessToken']
    assert not api(43145,'/admin/operations/mail-status',token=admin)['enabled']
    incident=secrets.token_hex(16)
    statement=f"insert into operations_mail_delivery(id,incident_id,code,event_kind,state,created_at,updated_at) values ('{secrets.token_hex(16)}','{incident}','BACKUP_STALE','OPENED','UNKNOWN',NOW(6),NOW(6));"
    sql(statement,database)
    assert sql(statement.replace("values ('", "values ('x",1),database,False).returncode!=0
    assert api(43145,'/admin/operations/mail-status',token=admin)['deliveries']['UNKNOWN']==1
    report['cases'].append('Durable mail ledger and unique incident transition verified; actual mail remains disabled')
    snapshot=checksums(database)
    dump=process(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',database]).stdout
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',dump,re.I|re.M)
    backup=directory/(database+'-backup.sql.gz');backup.write_bytes(gzip.compress(dump));report['backupSha256']=hashlib.sha256(backup.read_bytes()).hexdigest()
    sql(f'CREATE DATABASE {restored} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;');sql(gzip.decompress(backup.read_bytes()).decode(),restored)
    assert snapshot==checksums(restored);report['tablesRestored']=len(snapshot);report['cases'].append('Independent synthetic MySQL backup restored with every table checksum equal')
    process(['docker','stop',new]);start(old,43146,False)
    api(43146,'/plans',token=token);assert snapshot==checksums(database)
    report['cases'].append('Actual previous 0.1.15 image reads V24 without changing data')
    live=json.loads(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15).read());assert live['version']=='0.1.15'
    config=json.loads(urllib.request.urlopen('https://plan.zijun701.cn/api/auth/config',timeout=15).read());assert config['registrationEnabled'] is False
    report['cases'].append('Production health remains 0.1.15 and public registration remains closed')
    report.update(status='PASS',database=database,restoreDatabase=restored)
finally:
    for name in [new,old]:process(['docker','stop',name],check=False)
    report['at']=dt.datetime.now(dt.timezone.utc).isoformat();(directory/'report.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,ensure_ascii=True))
