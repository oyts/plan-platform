"""Continue retained synthetic V34 after observed public-error handling fix."""
import datetime as dt,gzip,hashlib,importlib.util,json,os,pathlib,re,subprocess,sys,time,urllib.request,urllib.error,traceback
root=pathlib.Path.cwd().resolve();assert re.fullmatch('/home/oyts/plan-platform/metrics-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(root));assert len(sys.argv)==2 and re.fullmatch('[a-f0-9]{64}',sys.argv[1]);expected=sys.argv[1];os.umask(0o077)
assert hashlib.sha256((root/'api-final.jar').read_bytes()).hexdigest()==expected
prior=json.loads((root/'resume-report.json').read_text());assert prior['status']=='FAIL' and prior['errorType']=='AssertionError' and len(prior['cases'])==2 and prior['originalProjectionTables']==58
with (root/'final-started.json').open('x') as f:json.dump({'at':time.time(),'apiSha256':expected},f)
config=dict(line.split('=',1) for line in (root/'candidate.env').read_text().splitlines());assert config['JWT_ISSUER']=='synthetic-metrics';database=re.fullmatch(r'jdbc:mysql://mysql:3306/(plan_metrics_[a-f0-9]{8})\?.*',config['SPRING_DATASOURCE_URL'])[1]
suffix=root.name[-8:];restore='metrics_restore_'+suffix;container='plan-metrics-final-'+suffix;env=dict(os.environ);started=False
report=dict(status='FAIL',parentMigrationJar=prior['apiSha256'],apiSha256=expected,originalProjectionTables=58,carriedCases=prior['cases'],cases=[],productionWrites=0,paidAiCalls=0,realEmails=0)
for line in pathlib.Path('/home/oyts/plan-platform/current/.env').read_text().splitlines():
 if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD')
def cmd(args,data=None):
 p=subprocess.run(args,input=data,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180)
 if p.returncode:(root/'final-command-error.private').write_bytes(p.stderr);raise RuntimeError('Isolated command failed; private stderr retained')
 return p.stdout
def sql(q,db=database):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',db],q.encode()).decode().strip()
def sums(db):
 names=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name").split();assert len(names)==62 and all(re.fullmatch('[A-Za-z0-9_]+',t) for t in names)
 result=sql('CHECKSUM TABLE '+','.join(db+'.`'+t+'`' for t in names));assert 'NULL' not in result;return {line.split()[0].split('.')[-1]:line.split()[-1] for line in result.splitlines()}
def api(path,data=None,status=200):
 req=urllib.request.Request('http://127.0.0.1:43181/api'+path,None if data is None else json.dumps(data).encode(),{'Content-Type':'application/json','Origin':'http://127.0.0.1:43181'})
 try:r=urllib.request.urlopen(req,timeout=15)
 except urllib.error.HTTPError as e:r=e
 with r:
  body=r.read();assert r.code==status,'Expected synthetic HTTP '+str(status)+', got '+str(r.code)
  if status==503:assert not body and 'no-store' in r.headers.get('Cache-Control','')
 return json.loads(body) if body and status<400 else None
def health():return json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=15))
try:
 before=health();assert before['version']=='0.1.23';unchanged=sums(database);assert sql('SELECT COUNT(*) FROM web_metric_events')=='3';assert sql('SELECT pv FROM web_metric_days')=='3'
 assert not re.search(':43181\s',cmd(['ss','-lnt']).decode());assert int(next(l.split()[1] for l in pathlib.Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))>600000
 (root/'api-final.jar').chmod(0o644);cmd(['docker','run','-d','--name',container,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p','127.0.0.1:43181:8080','--env-file',str(root/'candidate.env'),'-v',str(root/'api-final.jar')+':/candidate/api.jar:ro','--entrypoint','java','plan-platform-api:0.1.23','-jar','/candidate/api.jar','--app.ai.worker-enabled=false']);started=True
 for _ in range(120):
  assert cmd(['docker','inspect','--format','{{.State.Running}}',container]).strip()==b'true'
  try:
   if api('/health')['ok']:break
  except (OSError,AssertionError):pass
  time.sleep(2)
 else:raise RuntimeError('Final isolated startup timeout')
 assert sums(database)==unchanged;report['cases'].append('Final JAR V34 boot leaves all62 table checksums unchanged')
 t=api('/public/analytics/ticket',{'page':'WELCOME'})['ticket'];body={'page':'WELCOME','ticket':t,'visitorId':None};api('/public/analytics/event',body,503);assert sql('SELECT COUNT(*) FROM web_metric_events')=='3'
 sql('ALTER TABLE web_metric_days DROP CHECK synthetic_metrics_reject');api('/public/analytics/event',body,204);assert sql('SELECT pv FROM web_metric_days')=='4' and sql('SELECT COUNT(*) FROM web_metric_events')=='4';report['cases'].append('Final public database failure503/no-store/empty body, transaction rollback and same-ticket retry accepted once')
 guard=['python3',str(root/'check-governance-rollback.py'),'--database',database,'--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','yes']
 p=subprocess.run(guard,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30);assert p.returncode!=0 and b'metrics retention' in p.stderr;cmd(guard+['--target-supports-metrics','yes']);report['cases'].append('Actual MySQL metrics retention rollback guard refuses prior writer, allows aware writer')
 cmd(['python3',str(root/'metrics-private-baseline.py')]);report['apiBaseline']=json.loads((root/'api-baseline.json').read_text())
 sql('UPDATE web_metric_events SET created_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 49 HOUR); UPDATE web_metric_visitors SET created_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 49 HOUR);')
 for _ in range(90):
  if sql('SELECT COUNT(*) FROM web_metric_events')=='0':break
  time.sleep(2)
 else:raise RuntimeError('Scheduled expiration unavailable')
 assert sql('SELECT COUNT(*) FROM web_metric_visitors')=='0' and sql('SELECT pv FROM web_metric_days')=='4' and sql('SELECT COUNT(*) FROM users')=='4';report['cases'].append('Real scheduled48h expiry removes short-lived data, keeps daily counts and all4 synthetic accounts')
 cmd(['docker','stop',container]);started=False;source=sums(database)
 dump=cmd(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',database]);assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',dump,re.I|re.M)
 packed=gzip.compress(dump);(root/'final-synthetic.sql.gz').write_bytes(packed);report['backupSha256']=hashlib.sha256(packed).hexdigest();assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+restore+"'")=='0';sql('CREATE DATABASE '+restore+' CHARACTER SET utf8mb4;');sql(dump.decode(),restore);assert sums(restore)==source;report['restoredTables']=62;report['cases'].append('Final full62-table MySQL dump/restoration checksum equality')
 (root/'logs').mkdir(exist_ok=True);cmd(['docker','run','--name','plan-metrics-nginx-final-'+suffix,'--network','plan-platform_default','--memory','64m','--cpus','0.2','-v',str(root/'nginx.conf')+':/etc/nginx/conf.d/default.conf:ro','-v',str(root/'logs')+':/var/log/plan-metrics','--entrypoint','nginx','plan-platform-web:0.1.23','-t']);cmd(['python3',str(root/'metrics-linux-probe.py'),'--final']);report['linux']=json.loads((root/'linux-final-report.json').read_text());report['cases'].append('Retained Linux Nginx syntax and final bounded collector benchmark accepted')
 assert health()==before;report['status']='PASS'
except Exception as e:
 report['errorType']=type(e).__name__;report['failedLine']=traceback.extract_tb(e.__traceback__)[-1].lineno;raise
finally:
 if started:
  try:cmd(['docker','stop',container])
  except Exception:report['cleanupUnconfirmed']=True
 with (root/'final-report.json').open('x') as f:json.dump(report,f,indent=2)
 print(json.dumps({'status':report['status'],'cases':len(report['cases']),'productionWrites':0,'evidence':str(root/'final-report.json')}))
