"""Read-only HTTP baseline using an isolated synthetic principal, never production."""
import base64,datetime as dt,hashlib,hmac,json,os,pathlib,re,statistics,subprocess,time,urllib.request
root=pathlib.Path.cwd().resolve();assert re.fullmatch('/home/oyts/plan-platform/metrics-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(root));os.umask(0o077)
config=dict(line.split('=',1) for line in (root/'candidate.env').read_text().splitlines());assert config['JWT_ISSUER']=='synthetic-metrics';database=re.fullmatch(r'jdbc:mysql://mysql:3306/(plan_metrics_[a-f0-9]{8})\?.*',config['SPRING_DATASOURCE_URL'])[1]
env=dict(os.environ)
for line in pathlib.Path('/home/oyts/plan-platform/current/.env').read_text().splitlines():
 if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD')
p=subprocess.run(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N',database],input=b"SELECT id FROM users WHERE role='ADMIN' AND email LIKE 'metrics-mysql-%@example.test' LIMIT 1;",env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15);assert p.returncode==0;owner=p.stdout.decode().strip();assert re.fullmatch('[a-f0-9-]{36}',owner)
encode=lambda x:base64.urlsafe_b64encode(x).rstrip(b'=');now=int(time.time());parts=encode(b'{"alg":"HS256","typ":"JWT"}')+b'.'+encode(json.dumps({'iss':'plan-platform','sub':owner,'role':'ADMIN','accountGeneration':0,'iat':now,'exp':now+180}).encode());token=(parts+b'.'+encode(hmac.new(config['JWT_SECRET'].encode(),parts,hashlib.sha256).digest())).decode()
day=dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date();report={'status':'PASS','source':'Isolated synthetic ADMIN JWT with database ownership checks; not a production credential or login-flow benchmark.','requests':[]}
for kind in ['growth','website','runtime']:
 times=[]
 for i in range(6):
  url='http://127.0.0.1:43181/api/admin/metrics/'+kind+('' if kind=='runtime' else f'?from={day}&to={day}');start=time.monotonic()
  with urllib.request.urlopen(urllib.request.Request(url,headers={'Authorization':'Bearer '+token}),timeout=5) as r:body=r.read(65537);assert len(body)<=65536 and r.code==200 and 'no-store' in r.headers.get('Cache-Control','');json.loads(body)
  if i:times.append((time.monotonic()-start)*1000)
 report['requests'].append({'kind':kind,'measuredAfterWarmup':5,'medianMs':round(statistics.median(times),3),'maxMs':round(max(times),3)});assert max(times)<2000
with (root/'api-baseline.json').open('x') as f:json.dump(report,f,indent=2)
print('Read-only isolated aggregate baseline PASS; no token printed or saved.')
