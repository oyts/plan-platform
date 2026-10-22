"""Diagnose a saved synthetic supplement without re-importing or deleting test data."""
import hashlib,json,os,pathlib,re,subprocess,time,urllib.request,urllib.error,uuid
root=pathlib.Path('/home/oyts/plan-platform');directory=pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root)+r'/historical-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory))
suffix=directory.name[-8:];database='historical_acceptance_'+suffix;env=dict(os.environ)
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD')
def sql(query):
    r=subprocess.run(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',database],input=query.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=30)
    assert r.returncode==0,'Synthetic query failed';return r.stdout.decode().strip()
def api(path,data=None,token=None):
    request=urllib.request.Request('http://127.0.0.1:43169/api'+path,None if data is None else json.dumps(data).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token}if token else {})})
    try:r=urllib.request.urlopen(request,timeout=20)
    except urllib.error.HTTPError as e:r=e
    body=r.read();return r.code,json.loads(body)if body else None
out={}
try:
    for _ in range(100):
        try:
            if api('/health')[0]==200:break
        except OSError:pass
        time.sleep(2)
    code,account=api('/auth/login',{'email':f'historical-a-{suffix}@example.test','password':'SyntheticClosure123!'})
    assert code==200,'Synthetic login unavailable';token=account['accessToken']
    fields=sql('select import_id,operation_key,fingerprint from legacy_recycling_supplements limit 1').split('\t');assert len(fields)==3
    batch,key,fingerprint=fields;assert str(uuid.UUID(batch))==batch and str(uuid.UUID(key))==key and re.fullmatch('[a-f0-9]{64}',fingerprint)
    code,body=api(f'/migrations/legacy/{batch}/recycling-supplement',{'operationKey':key,'fingerprint':fingerprint,'confirmed':True},token)
    out['replayStatus']=code;out['replayMessage']=body.get('message');out['nameMatches']=sql("select count(*) from plans where name='合成迁移后本人编辑'")
    out['runningSnapshotPaused']=sql("select count(*) from mainline_items where title='删除步骤' and status='PAUSED'")
    out['sessionTimeZone']=sql('select @@session.time_zone,@@character_set_client')
    out['importCompletedAt']=body.get('importCompletedAt')
finally:
    subprocess.run(['docker','stop','plan-historical-new-'+suffix],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=40)
    (directory/'replay-report.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
