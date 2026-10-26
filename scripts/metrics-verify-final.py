"""Read-only final-byte boot verification on the accepted isolated V34 database."""
import hashlib,json,os,pathlib,re,subprocess,sys,time,urllib.request
root=pathlib.Path.cwd().resolve();assert re.fullmatch('/home/oyts/plan-platform/metrics-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(root));assert len(sys.argv)==2 and re.fullmatch('[a-f0-9]{64}',sys.argv[1]);expected=sys.argv[1];os.umask(0o077)
proof=json.loads((root/'final-report.json').read_text());assert proof['status']=='PASS' and proof['restoredTables']==62;assert hashlib.sha256((root/'api-verified.jar').read_bytes()).hexdigest()==expected
config=dict(s.split('=',1) for s in (root/'candidate.env').read_text().splitlines());db=re.fullmatch(r'jdbc:mysql://mysql:3306/(plan_metrics_[a-f0-9]{8})\?.*',config['SPRING_DATASOURCE_URL'])[1];env=dict(os.environ)
for line in pathlib.Path('/home/oyts/plan-platform/current/.env').read_text().splitlines():
 if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD');name='plan-metrics-verify-'+expected[:8];started=False;report=dict(status='FAIL',apiSha256=expected,parentFullAcceptanceJar=proof['apiSha256'],productionWrites=0)
def cmd(args,data=None):
 p=subprocess.run(args,input=data,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180)
 if p.returncode:raise RuntimeError('Final verification command failed; private output withheld')
 return p.stdout.decode().strip()
def sql(q):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N',db],q.encode())
def checksum():
 tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name").split();assert len(tables)==62 and all(re.fullmatch('[A-Za-z0-9_]+',s) for s in tables);r=sql('CHECKSUM TABLE '+','.join(db+'.`'+s+'`' for s in tables));assert 'NULL' not in r;return r
def public():return json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15))
try:
 assert not (root/('verify-'+expected[:8]+'.json')).exists();before=checksum();health=public();assert health['version']=='0.1.23';assert not re.search(':43181\s',cmd(['ss','-lnt']));assert int(next(l.split()[1] for l in pathlib.Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))>600000
 (root/'api-verified.jar').chmod(0o644);cmd(['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p','127.0.0.1:43181:8080','--env-file',str(root/'candidate.env'),'-v',str(root/'api-verified.jar')+':/candidate/api.jar:ro','--entrypoint','java','plan-platform-api:0.1.23','-jar','/candidate/api.jar','--app.ai.worker-enabled=false']);started=True
 for _ in range(120):
  assert cmd(['docker','inspect','--format','{{.State.Running}}',name])=='true'
  try:
   if json.load(urllib.request.urlopen('http://127.0.0.1:43181/api/health',timeout=5))['ok']:break
  except (OSError,ValueError):pass
  time.sleep(2)
 else:raise RuntimeError('Final byte readiness timeout')
 assert before==checksum() and health==public();report.update(status='PASS',unchangedTables=62,migration=34,scope='Final alert-disable guard and public transaction-unavailable boundary: new unit coverage plus final JAR boot; full migration/restore evidence remains separate.')
finally:
 if started:cmd(['docker','stop',name])
 with (root/('verify-'+expected[:8]+'.json')).open('x') as f:json.dump(report,f,indent=2)
 print(json.dumps(report))
