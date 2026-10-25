#!/usr/bin/env python3
"""Final runner-only JAR boot on an already accepted isolated V33 database."""
import hashlib,json,os,pathlib,re,subprocess,sys,time,urllib.request
folder=pathlib.Path.cwd().resolve();root=pathlib.Path('/home/oyts/plan-platform');expected=sys.argv[1] if len(sys.argv)==2 else ''
assert re.fullmatch(str(root)+r'/admin-center-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(folder)) and re.fullmatch('[a-f0-9]{64}',expected)
proof=json.loads((folder/'report.json').read_text());assert proof['status']=='PASS' and proof['paidAiCalls']==0 and proof['realEmails']==0
assert hashlib.sha256((folder/'api-final.jar').read_bytes()).hexdigest()==expected
os.umask(0o077);env=dict(os.environ)
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD');match=re.search(r'jdbc:mysql://mysql:3306/(plan_acceptance_[0-9a-f_]+)\?',(folder/'candidate.env').read_text());assert match;db=match[1]
name='plan-admin-final-'+folder.name[-8:];port=43181;started=False;result=folder/('final-'+expected[:8]+'.json');assert not result.exists()
report=dict(status='FAIL',apiSha256=expected,parentMigrationJar=proof['apiSha256'],productionWrites=0,paidAiCalls=0,realEmails=0)
def cmd(args,data=None):
    p=subprocess.run(args,input=data,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180)
    if p.returncode:raise RuntimeError('Final isolated command failed; no private output disclosed')
    return p.stdout.decode().strip()
def sql(statement):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N','--batch'],statement.encode())
def checks():
    tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name").split();assert len(tables)>=59 and all(re.fullmatch('[A-Za-z0-9_]+',t) for t in tables)
    values=sql('CHECKSUM TABLE '+','.join(db+'.`'+t+'`' for t in tables));assert 'NULL' not in values;return values
def public():return json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15))
try:
    assert not re.search(':'+str(port)+r'\s',cmd(['ss','-lnt']));assert int(next(s.split()[1] for s in pathlib.Path('/proc/meminfo').read_text().splitlines() if s.startswith('MemAvailable:')))>600000
    before=checks();health=public();assert health['version']=='0.1.22';(folder/'api-final.jar').chmod(0o644)
    cmd(['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(folder/'candidate.env'),'-v',str(folder/'api-final.jar')+':/candidate/api.jar:ro','--entrypoint','java','plan-platform-api:0.1.22','-jar','/candidate/api.jar','--app.ai.worker-enabled=false']);started=True
    for _ in range(120):
        assert cmd(['docker','inspect','--format','{{.State.Running}}',name])=='true'
        try:
            h=json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/api/health',timeout=5))
            if h.get('ok') and h.get('version')=='0.1.22':break
        except (OSError,ValueError):pass
        time.sleep(2)
    else:raise RuntimeError('Final isolated readiness timeout')
    assert before==checks();assert health==public();report.update(status='PASS',unchangedTables=len(before.splitlines()),migration=33,workerEnabled=False)
finally:
    if started:cmd(['docker','stop',name])
    result.write_text(json.dumps(report,indent=2));print(json.dumps(report))
