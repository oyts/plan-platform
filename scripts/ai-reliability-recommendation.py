#!/usr/bin/env python3
"""Final prompt-only JAR probe; optional separately approved SINGLE paid parent."""
import datetime as dt,hashlib,json,os,pathlib,re,secrets,subprocess,sys,time,urllib.request,urllib.error,uuid
assert sys.argv[1:] in (['--verify-only'],['--approved-one-parent-2048-output'])
paid=sys.argv[1]=='--approved-one-parent-2048-output';directory=pathlib.Path.cwd().resolve();os.umask(0o077)
assert re.fullmatch(r'/home/oyts/plan-platform/reliability-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory))
assert json.loads((directory/'restore-followup-report.json').read_text())['status']=='PASS'
expected=(directory/'api-prompt.jar.sha256').read_text().split()[0];assert hashlib.sha256((directory/'api-prompt.jar').read_bytes()).hexdigest()==expected
prefix='recommendation-paid' if paid else 'prompt-verify'
with (directory/(prefix+'-started.json')).open('x') as f:json.dump({'approvedParents':int(paid),'maxAttempts':2 if paid else 0,'maxOutputTokens':2048 if paid else 0},f)
env=dict(os.environ)
for line in pathlib.Path('/home/oyts/plan-platform/current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD');suffix=directory.name[-8:];db='reliability_real_'+suffix;container='plan-reliability-'+prefix+'-'+suffix;port=43172;started=False
report={'status':'FAIL','paidParents':0,'upstreamAttempts':0,'productionWrites':0,'apiSha256':expected}
def cmd(args,data=None):
    p=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=180)
    if p.returncode:raise RuntimeError('Isolated command failed, no credential output disclosed')
    return p.stdout
def sql(text):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',db],text.encode()).decode()
def api(path,data=None,token=None,status=200):
    req=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})})
    try:r=urllib.request.urlopen(req,timeout=25)
    except urllib.error.HTTPError as e:r=e
    with r:body=r.read();assert r.code==status,'Unexpected isolated API status'
    return json.loads(body) if body else None
try:
    history=sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank');assert len(history.splitlines())==31 and history.splitlines()[-1].startswith('31\t')
    assert not re.search(':43172'+r'\s',cmd(['ss','-lnt']).decode())
    args=['docker','run','-d','--name',container,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(directory/'real.env'),'-v',str(directory/'api-prompt.jar')+':/candidate/api.jar:ro','--entrypoint','java']
    if paid:args+=['-v','/etc/plan-platform-ai/deepseek-api-key:/run/secrets/deepseek-api-key:ro']
    args+=['plan-platform-api:0.1.20','-jar','/candidate/api.jar','--app.ai.worker-enabled='+str(paid).lower(),'--app.ai.max-output-tokens=1024','--app.ai.max-input-chars=16000','--spring.h2.console.enabled=false']
    cmd(args);started=True
    for _ in range(100):
        if cmd(['docker','inspect','--format','{{.State.Running}}',container]).strip()!=b'true':raise RuntimeError('Final prompt candidate exited')
        try:
            if api('/health')['ok']:break
        except OSError:pass
        time.sleep(2)
    else:raise RuntimeError('Final prompt candidate readiness timed out')
    assert sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')==history
    report['unchangedMigrations']=31
    if paid:
        assert json.loads((directory/'real-report.json').read_text())['submittedParents']==6
        sql('UPDATE ai_quota_policy SET daily_limit=1')
        account=api('/auth/register',{'email':'recommend-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(30),'displayName':'合成模式补验'},status=201);token=account['accessToken']
        session=api('/ai/sessions',{'mode':None,'consent':True},token);operation=str(uuid.uuid4())
        (directory/'recommendation-before-submit.json').write_text(json.dumps({'operation':operation,'owner':account['user']['id'],'intent':'DIALOGUE','submittedUtc':dt.datetime.now(dt.timezone.utc).isoformat()}));report['paidParents']=1
        api('/ai/sessions/'+session['id']+'/jobs',{'message':'我零基础学Java，准备从2026-10-26到2026-12-26，每天确定有两小时，星期日休息，有电脑。我希望每天有明确内容，先请推荐使用哪种模式及理由，不要生成草稿。','intent':'DIALOGUE','version':session['version'],'operationKey':operation},token,status=202)
        for _ in range(60):
            session=api('/ai/sessions/'+session['id'],token=token)
            if session['turns'][-1]['state'] not in ('QUEUED','RUNNING'):break
            time.sleep(2)
        (directory/'recommendation-private.json').write_text(json.dumps(session,ensure_ascii=False))
        turn=session['turns'][-1];report.update(upstreamAttempts=turn['diagnostic']['attempts'],knownOutputTokens=sum(a['outputTokens'] or 0 for a in turn['diagnostic']['details']),state=turn['state'],recommendedMode=session['recommendedMode'])
        assert report['upstreamAttempts']<=2 and report['knownOutputTokens']<=2048
        report['status']='PASS' if turn['state']=='SUCCEEDED' and session['recommendedMode']=='PLAN' and session['draft'] is None else 'REVIEW_REQUIRED'
    else:report['status']='PASS'
finally:
    if paid:
        try:sql('UPDATE ai_quota_policy SET daily_limit=0')
        except Exception:pass
    if started:cmd(['docker','stop',container])
    (directory/(prefix+'-report.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False))
