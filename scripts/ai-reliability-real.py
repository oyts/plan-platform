#!/usr/bin/env python3
"""Owner-approved ONE real-provider round: <=6 parents, <=12 attempts, <=49152 output.

Run only in a successful isolated reliability-candidates directory. A persistent
exclusive marker is written before startup/submission. Never delete it to replay.
The provider key is mounted directly by Docker, never read by this script.
"""
import calendar,datetime as dt,hashlib,json,os,pathlib,re,secrets,subprocess,sys,time,urllib.request,urllib.error,uuid
assert sys.argv[1:]==['--approved-budget-six'],'Explicit approved round required'
root=pathlib.Path('/home/oyts/plan-platform');directory=pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root)+r'/reliability-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory))
os.umask(0o077)
previous=json.loads((directory/'restore-followup-report.json').read_text());assert previous['status']=='PASS'
assert hashlib.sha256((directory/'api.jar').read_bytes()).hexdigest()==previous['apiSha256']
ledger={'approvedParents':6,'approvedAttempts':12,'outputTokenCeiling':49152,'submitted':[]}
with (directory/'real-acceptance-started.json').open('x') as f:json.dump(ledger,f)
env=dict(os.environ)
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD')
suffix=directory.name[-8:];source='reliability_'+suffix;db='reliability_real_'+suffix;user='rel_'+suffix;port=43172;container='plan-reliability-real-'+suffix
report={'status':'INCOMPLETE','productionWrites':0,'realEmails':0,'cases':[]};started=False
def cmd(args,data=None):
    p=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=180)
    if p.returncode:raise RuntimeError('Isolated command failed; no credential output disclosed')
    return p.stdout
def sql(text,database=None):
    return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',*([database] if database else [])],text.encode()).decode()
def api(path,data=None,token=None,status=200,method=None):
    req=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})},method=method)
    try:r=urllib.request.urlopen(req,timeout=25)
    except urllib.error.HTTPError as e:r=e
    with r:body=r.read();assert r.code==status,'Unexpected isolated API status'
    return json.loads(body) if body else None
def snapshot():
    rows=sql('SELECT COUNT(*),COALESCE(SUM(output_tokens),0) FROM ai_job_attempts',db).strip().split('\t')
    attempts,outputs=map(int,rows);assert attempts<=12 and outputs<=49152
    report.update(actualProviderAttempts=attempts,knownOutputTokens=outputs)
def submit(label,session,intent,message,check):
    assert len(ledger['submitted'])<6
    operation=str(uuid.uuid4());ledger['submitted'].append({'label':label,'operation':operation,'intent':intent,'beforeSubmissionUtc':dt.datetime.now(dt.timezone.utc).isoformat()})
    (directory/'real-acceptance-ledger.json').write_text(json.dumps(ledger))
    job=api('/ai/sessions/'+session['id']+'/jobs',{'message':message,'intent':intent,'version':session['version'],'operationKey':operation},token,status=202)
    for _ in range(60):
        session=api('/ai/sessions/'+session['id'],token=token);turn=session['turns'][-1]
        if turn['state'] not in ('QUEUED','RUNNING'):break
        time.sleep(2)
    (directory/('real-private-'+label+'.json')).write_text(json.dumps(session,ensure_ascii=False))
    snapshot();passed=turn['state']=='SUCCEEDED' and check(session)
    report['cases'].append({'label':label,'state':turn['state'],'attempts':turn['diagnostic']['attempts'],'reason':turn['diagnostic']['reason'],'draftItems':len((session.get('draft') or {}).get('items',[])),'passed':passed})
    (directory/'real-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    if turn['state'] in ('UNKNOWN','QUEUED','RUNNING'):raise RuntimeError('Uncertain result: no more paid requests submitted')
    return session
def create(mode):return api('/ai/sessions',{'mode':mode,'consent':True},token)
try:
    before=json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20));assert before['version']=='0.1.20'
    assert not re.search(':'+str(port)+r'\s',cmd(['ss','-lnt']).decode())
    assert int(cmd(['awk','/MemAvailable:/ {print $2}','/proc/meminfo']).strip())>600000
    assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+db+"'").strip()=='0'
    sql(f"CREATE DATABASE {db} CHARACTER SET utf8mb4; GRANT ALL ON {db}.* TO '{user}'@'%';")
    ddl=cmd(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--no-data','--no-tablespaces',source]);sql(ddl.decode(),db)
    history=sql('SELECT installed_rank FROM flyway_schema_history',source)
    assert history.strip()
    # Only synthetic migration metadata is copied; no user/session/job data.
    sql(f'INSERT INTO {db}.flyway_schema_history SELECT * FROM {source}.flyway_schema_history; INSERT INTO {db}.ai_quota_policy SELECT * FROM {source}.ai_quota_policy; UPDATE {db}.ai_quota_policy SET daily_limit=6;')
    values=dict(line.split('=',1) for line in (directory/'candidate.env').read_text().splitlines() if '=' in line)
    values.update(DB_URL=f'jdbc:mysql://mysql:3306/{db}?serverTimezone=Asia/Shanghai',SPRING_DATASOURCE_URL=f'jdbc:mysql://mysql:3306/{db}?serverTimezone=Asia/Shanghai',AI_API_KEY_FILE='/run/secrets/deepseek-api-key',AI_MAX_CONCURRENT='1',AI_QUEUE_CAPACITY='1',AI_MAX_INPUT_CHARS='32000',AI_MAX_OUTPUT_TOKENS='8192',AI_TIMEOUT_SECONDS='60',AI_REPAIR_ENABLED='true')
    with (directory/'real.env').open('x') as f:f.write(''.join(k+'='+v+'\n' for k,v in values.items()))
    cmd(['docker','run','-d','--name',container,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(directory/'real.env'),'-v',str(directory/'api.jar')+':/candidate/api.jar:ro','-v','/etc/plan-platform-ai/deepseek-api-key:/run/secrets/deepseek-api-key:ro','--entrypoint','java','plan-platform-api:0.1.20','-jar','/candidate/api.jar','--app.ai.worker-enabled=true']);started=True
    for _ in range(100):
        if cmd(['docker','inspect','--format','{{.State.Running}}',container]).strip()!=b'true':raise RuntimeError('Isolated provider candidate exited')
        try:
            if api('/health')['ok']:break
        except OSError:pass
        time.sleep(2)
    account=api('/auth/register',{'email':'real-synthetic-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(30),'displayName':'合成模型验收'},status=201);token=account['accessToken']
    today=dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date();start=today+dt.timedelta(days=(7-today.weekday())%7 or 7)
    month=start.month+2;endYear=start.year+(month-1)//12;endMonth=(month-1)%12+1
    end=start.replace(year=endYear,month=endMonth,day=min(start.day,calendar.monthrange(endYear,endMonth)[1]))
    java=submit('java-discuss',create(None),'DIALOGUE',f'我零基础想学习Java，打算从{start}到{end}，每天两小时，星期日休息。在学校，有电脑。先讨论适合哪种规划方式，不要生成草稿。',lambda s:s['draft'] is None and s['recommendedMode']=='PLAN')
    java=api('/ai/sessions/'+java['id']+'/mode',{'mode':'PLAN','version':java['version']},token,method='PUT')
    java=submit('java-calendar',java,'GENERATE','确认使用周期计划，请根据刚才完整要求生成，基础、日期和周日休息均不变；涵盖整个周期，每个计划日一项可执行任务，各120分钟。',lambda s:bool(s['draft']) and s['draft']['startDate']==str(start) and s['draft']['endDate']==str(end) and s['draft']['restDays']==[7] and all(i['plannedMinutes']==120 for i in s['draft']['items']) and len({i['dueDate'] for i in s['draft']['items']})==sum((start+dt.timedelta(days=i)).weekday()!=6 for i in range((end-start).days+1)))
    school=submit('school-flexible',create('PLAN'),'GENERATE',f'在学校，无健身房，没有器械，想用自重练习建立运动习惯。确认使用周期计划，从{start}到{start+dt.timedelta(days=6)}，只安排三次，每次20分钟，时间和休息日无所谓，请推荐具体日期及休息日，给我预览再决定，不要假称我已确认这些推荐。',lambda s:bool(s['draft']) and len(s['draft']['items'])==3 and all(i['plannedMinutes']==20 for i in s['draft']['items']))
    guitar=submit('guitar-route',create('ROUTE'),'GENERATE','我零基础学吉他，没有截止日期，有吉他，空闲才学，每次大概20分钟。请按乐理入门、认识吉他、单音、和弦、简单歌曲分阶段，各一至两步，不安排每日任务或结束日。直接生成可调整的目标路线。',lambda s:bool(s['draft']) and s['draft']['endDate'] is None and len(s['draft']['stages'])>=3)
    todo=submit('todo-create',create('TODO'),'GENERATE',f'确认用今日待办：{today}需要完成三件事：给盆栽浇水5分钟，整理桌面10分钟，归还图书15分钟。只创建这三项，不扩成长期计划，供我预览确认。',lambda s:bool(s['draft']) and len(s['draft']['items'])==3 and all(i['dueDate']==str(today) for i in s['draft']['items']))
    if todo['draft']:
        todo=submit('todo-revise',todo,'REVISE',f'保留原来三项和标题，只把整理桌面调整为20分钟，另外两项用时、日期不变。不添加任务。',lambda s:bool(s['draft']) and len(s['draft']['items'])==3 and sorted(i['plannedMinutes'] for i in s['draft']['items'])==[5,15,20])
    # A valid draft is not enough: verify explicit apply once and replay idempotence.
    for label,s in [('java',java),('school',school),('guitar',guitar),('todo',todo)]:
        if s['draft']:
            body={'version':s['version'],'confirmed':True};a=api('/ai/sessions/'+s['id']+'/apply',body,token);b=api('/ai/sessions/'+s['id']+'/apply',body,token);assert a==b
    snapshot();assert before==json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20))
    report['status']='PASS' if len(report['cases'])==6 and all(c['passed'] for c in report['cases']) else 'REVIEW_REQUIRED'
finally:
    try:sql('UPDATE ai_quota_policy SET daily_limit=0',db)
    except Exception:pass
    if started:
        try:cmd(['docker','stop',container])
        except Exception:report['stopUnconfirmed']=True
    report['submittedParents']=len(ledger['submitted']);(directory/'real-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False))
