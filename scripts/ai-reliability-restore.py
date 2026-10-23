#!/usr/bin/env python3
"""Resume ONLY retained synthetic restore/old-image gates, never replay admission."""
import gzip,hashlib,io,json,os,pathlib,re,subprocess,sys,time,urllib.request,urllib.error
directory=pathlib.Path.cwd().resolve();root=pathlib.Path('/home/oyts/plan-platform');os.umask(0o077)
assert re.fullmatch(str(root)+r'/reliability-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory))
report=json.loads((directory/'report.json').read_text());assert report['status']=='FAIL' and len(report['cases'])==2
assert report['apiSha256']==hashlib.sha256((directory/'api.jar').read_bytes()).hexdigest()
fix=sys.argv[1:]==['--python36-fix']
assert not sys.argv[1:] or fix
if fix:assert "unexpected keyword argument 'mtime'" in (directory/'restore-run.stderr').read_text()
with (directory/('restore-followup-python36-started.json' if fix else 'restore-followup-started.json')).open('x') as f:f.write('{}')
env=dict(os.environ)
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD');suffix=directory.name[-8:];db='reliability_'+suffix;restore='reliability_restore2_'+suffix;container='plan-reliability-resume-'+suffix
def cmd(args,data=None):
    p=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=180)
    if p.returncode:
        (directory/'restore-private-error.txt').write_bytes(p.stderr)
        raise RuntimeError('Synthetic command failed; private stderr saved')
    return p.stdout
def sql(text,database=None):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',*([database] if database else [])],text.encode()).decode()
def projections(database):
    tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+database+"' ORDER BY table_name").split();result={}
    for t in tables:
        cols=sql("SELECT column_name FROM information_schema.columns WHERE table_schema='"+database+"' AND table_name='"+t+"' ORDER BY ordinal_position").split()
        assert all(re.fullmatch('[a-zA-Z0-9_]+',x) for x in [t,*cols])
        fields=','.join('`'+x+'`' for x in cols)
        result[t]=hashlib.sha256(sql('SELECT '+fields+' FROM `'+t+'` ORDER BY '+fields,database).encode()).hexdigest()
    return result
started=False
try:
    before=json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20));assert before['version']=='0.1.20'
    original=projections(db)
    data=cmd(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',db])
    assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',data,re.I|re.M)
    zipped=io.BytesIO()
    with gzip.GzipFile(fileobj=zipped,mode='wb',mtime=0) as archive:archive.write(data)
    compressed=zipped.getvalue();(directory/'synthetic-followup.sql.gz').write_bytes(compressed)
    assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+restore+"'").strip()=='0'
    sql('CREATE DATABASE '+restore+' CHARACTER SET utf8mb4;');sql(data.decode(),restore)
    restored=projections(restore);different=[table for table in original if original[table]!=restored.get(table)]
    report['restoreDifferences']=different;assert not different and original.keys()==restored.keys()
    report['backupSha256']=hashlib.sha256(compressed).hexdigest();report['restoredTables']=len(original);report['cases'].append('Final synthetic full-dump restore all table projections')
    assert not re.search(':43172'+r'\s',cmd(['ss','-lnt']).decode())
    cmd(['docker','run','-d','--name',container,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p','127.0.0.1:43172:8080','--env-file',str(directory/'candidate.env'),'--entrypoint','java','plan-platform-api:0.1.20','-jar','/app/api.jar','--app.ai.worker-enabled=false']);started=True
    for _ in range(100):
        if cmd(['docker','inspect','--format','{{.State.Running}}',container]).strip()!=b'true':raise RuntimeError('Synthetic prior image exited')
        try:
            if json.load(urllib.request.urlopen('http://127.0.0.1:43172/api/health',timeout=3))['ok']:break
        except OSError:pass
        time.sleep(2)
    else:raise RuntimeError('Synthetic prior-image readiness timed out')
    # Flyway validates prior migrations and must leave V31 metadata/data intact.
    assert projections(db)==original
    report['cases'].append('Actual previous0.1.20 image boots afterV31 without business mutation')
    assert before==json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20));report['status']='PASS'
finally:
    if started:cmd(['docker','stop',container])
    (directory/'restore-followup-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False))
