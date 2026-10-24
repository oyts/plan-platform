#!/usr/bin/env python3
"""Verify final bytes against the retained synthetic V32 DB, not production."""
import argparse,hashlib,json,os,pathlib,re,secrets,subprocess,time,urllib.request,urllib.error,uuid

def main():
    p=argparse.ArgumentParser();p.add_argument('--sha256',required=True);p.add_argument('--version',default='0.1.21');args=p.parse_args()
    root=pathlib.Path('/home/oyts/plan-platform');folder=pathlib.Path.cwd().resolve()
    assert re.fullmatch(str(root)+r'/long-planning-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(folder))
    assert re.fullmatch('[a-f0-9]{64}',args.sha256) and re.fullmatch(r'0\.1\.[0-9]+',args.version)
    assert json.loads((folder/'report.json').read_text())['status']=='PASS'
    assert hashlib.sha256((folder/'api-final.jar').read_bytes()).hexdigest()==args.sha256
    os.umask(0o077);(folder/'api-final.jar').chmod(0o644);suffix=folder.name[-8:];database='long_'+suffix;port=43175
    stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime());result=folder/('final-'+stamp+'.json');name='plan-long-final-'+suffix+'-'+stamp.lower();started=False
    report=dict(status='FAIL',apiSha256=args.sha256,productionWrites=0,paidAiCalls=0,realEmails=0,cases=[])
    environment=dict(os.environ)
    for line in (root/'current/.env').read_text().splitlines():
        if line.startswith('MYSQL_ROOT_PASSWORD='):environment['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
    assert environment.get('MYSQL_PWD')
    def cmd(argv,data=None):
        r=subprocess.run(argv,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=environment,timeout=180)
        if r.returncode:raise RuntimeError('Isolated final verification command failed; no output disclosed')
        return r.stdout.decode().strip()
    def sql(statement):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',database],statement.encode())
    token=None
    def api(path,data=None,method=None,status=200):
        request=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data,ensure_ascii=False).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})},method=method)
        try:r=urllib.request.urlopen(request,timeout=45)
        except urllib.error.HTTPError as e:r=e
        with r:body=r.read();assert r.code==status,f'Synthetic HTTP status {r.code}, expected {status}'
        return json.loads(body) if body else None
    try:
        assert not re.search(':'+str(port)+r'\s',cmd(['ss','-lnt']))
        assert int(cmd(['awk','/MemAvailable:/ {print $2}','/proc/meminfo']))>600000
        before=json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15))
        budget=sql('SELECT session_id,submissions,completed_json FROM ai_long_plans ORDER BY session_id');assert budget
        history=sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')
        cmd(['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(folder/'candidate.env'),'-v',str(folder/'api-final.jar')+':/candidate/api.jar:ro','--entrypoint','java','plan-platform-api:0.1.21','-jar','/candidate/api.jar','--app.ai.worker-enabled=false']);started=True
        for _ in range(120):
            assert cmd(['docker','inspect','--format','{{.State.Running}}',name])=='true'
            try:
                if api('/health')['version']==args.version:break
            except (OSError,AssertionError):pass
            time.sleep(2)
        else:raise RuntimeError('Final candidate readiness timeout')
        assert sql('SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank')==history
        assert sql('SELECT session_id,submissions,completed_json FROM ai_long_plans ORDER BY session_id')==budget
        assert sql('SELECT COUNT(*) FROM ai_job_attempts')=='0';report['cases'].append('Final JAR boots on V32, Flyway checksums and retained batch budgets unchanged')
        token=api('/auth/register',{'email':'long-final-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24),'displayName':'最终容量合成验收'},status=201)['accessToken']
        s=api('/ai/sessions',{'mode':'ROUTE','consent':True})
        draft={'schemaVersion':1,'mode':'ROUTE','revision':0,'title':'中文大草稿','startDate':None,'endDate':None,'restDays':[],'stages':[],'items':[{'localId':'t'+str(i),'title':'合成事项'+str(i),'note':'练'*1800,'dueDate':None,'plannedMinutes':30,'stageId':None,'prerequisites':[]} for i in range(50)]}
        body={'version':s['version'],'draftJson':json.dumps(draft,ensure_ascii=False)};assert len(json.dumps(body,ensure_ascii=False).encode())>256000
        s=api('/ai/sessions/'+s['id']+'/draft',body,method='PUT');assert len(s['draft']['items'])==50
        material=api('/ai/sessions/'+s['id']+'/collaboration/export',{'version':s['version'],'instructions':'仅供中文容量合成验证','consent':True})
        preview=api('/ai/sessions/'+s['id']+'/collaboration/preview',{'version':s['version'],'source':json.dumps(material,ensure_ascii=False)})
        assert preview['binding']=='BOUND' and preview['proposed']['items']==s['draft']['items'];assert api('/ai/sessions/'+s['id'])['version']==s['version']
        report['cases'].append('Real MySQL/HTTP UTF8 draft >256k bytes and collaboration readonly preflight accepted within character limit')
        assert before==json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15));assert sql('SELECT COUNT(*) FROM ai_job_attempts')=='0'
        report['status']='PASS'
    finally:
        if started:cmd(['docker','stop',name])
        result.write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'status':report['status'],'privateEvidence':str(result)}))
if __name__=='__main__':main()
