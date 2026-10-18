#!/usr/bin/env python3
"""Bounded read-only baseline against retained synthetic MySQL, never the production database."""
import argparse,base64,concurrent.futures,datetime,hashlib,hmac,importlib.util,json,math,os,re,time,urllib.request
from pathlib import Path

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--directory',required=True);parser.add_argument('--state-directory',required=True);args=parser.parse_args()
    folder=Path(args.directory).resolve();source=Path(args.state_directory).resolve();root=Path('/home/oyts/plan-platform/roadmap-candidates')
    if not all(root in path.parents for path in [folder,source]) or folder==source:raise RuntimeError('Separate managed candidate directories required.')
    spec=importlib.util.spec_from_file_location('roadmap_common',folder/'roadmap-mysql-acceptance.py');common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
    require,command,inspect,request=common.require,common.command,common.inspect,common.request
    os.umask(0o077);common.private_write(folder/'started.json',json.dumps({'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}))
    state=json.loads((source/'candidate-private.json').read_text());database=state['database'];require(re.fullmatch('plan_acceptance_[0-9a-f_]+',database),'Only a synthetic database is allowed.')
    require(not command(['docker','ps','-q','--filter','publish=43145']).strip(),'Isolated port is busy; no process was stopped.')
    mysql=inspect('plan-platform-mysql-1');secret=next(value.split('=',1)[1] for value in mysql['Config']['Env'] if value.startswith('MYSQL_ROOT_PASSWORD='));environment={**os.environ,'MYSQL_PWD':secret}
    def sql(statement):return command(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','--batch','--skip-column-names'],data=statement.encode(),env=environment).decode().strip()
    tables=sql("select table_name from information_schema.tables where table_schema='"+database+"' order by table_name").splitlines();require(all(re.fullmatch('[a-z0-9_]+',table) for table in tables),'Unexpected table.')
    def checksums():return {table:sql('checksum table '+database+'.'+table+' extended').split()[-1] for table in tables}
    owner,generation=sql('select id,auth_generation from '+database+".users where enabled=true and role='USER' order by id limit 1").split('\t');require(re.fullmatch('[a-f0-9-]{36}',owner) and generation.isdigit(),'Synthetic account missing.')
    encode=lambda value:base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).rstrip(b'=').decode()
    now=int(time.time());unsigned=encode({'alg':'HS256','typ':'JWT'})+'.'+encode({'iss':'plan-platform','sub':owner,'role':'USER','accountGeneration':int(generation),'iat':now,'exp':now+900});token=unsigned+'.'+base64.urlsafe_b64encode(hmac.new(state['jwt'].encode(),unsigned.encode(),hashlib.sha256).digest()).rstrip(b'=').decode()
    name='plan-roadmap-baseline-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S');running=False
    report={'status':'FAIL','productionDatabaseAccessed':False,'paidAiCalls':0,'realEmailsSent':0,'conditions':{'candidateCpuLimit':0.6,'candidateMemoryMiB':480,'javaHeapMiB':192,'database':'shared MySQL 8.4 container, isolated synthetic schema','transport':'loopback HTTP, no TLS / internet latency','warmupsPerCase':3,'samplesPerCase':15,'concurrency':[1,2],'requestTimeoutSeconds':15,'clientPacingMilliseconds':50},'results':[]}
    report['host']={'logicalCpuCount':os.cpu_count(),'memoryMiB':int(re.search(r'MemTotal:\s+(\d+)',Path('/proc/meminfo').read_text())[1])//1024,'cpuModel':next((line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),'unknown')}
    report['syntheticRows']={table:int(sql('select count(*) from '+database+'.'+table)) for table in ['users','plans','plan_tasks','mainlines','mainline_items','todos']}
    def sample(path):
        started=time.perf_counter()
        try:
            with urllib.request.urlopen(urllib.request.Request(common.BASE+path,headers={'Authorization':'Bearer '+token}),timeout=15) as response:
                body=response.read(5*1024*1024+1);status=response.status
            require(status==200 and len(body)<=5*1024*1024,'Read response failed or exceeded cap.')
            result={'ms':(time.perf_counter()-started)*1000,'bytes':len(body),'ok':True}
        except Exception:result={'ms':(time.perf_counter()-started)*1000,'bytes':0,'ok':False}
        time.sleep(.05);return result
    try:
        before=checksums();require('NULL' not in before.values(),'Checksum unavailable.');public=request('/health',base='https://plan.zijun701.cn/api')
        # All secrets stay inside a private candidate file and are not returned in the report.
        common.private_write(folder/'candidate.env',(source/'candidate.env').read_text()+'\nOPERATIONS_ENABLED=false\n')
        command(['docker','run','-d','--name',name,'--network',state['network'],'--memory','480m','--cpus','0.6','--pids-limit','256','--env-file',str(folder/'candidate.env'),'--mount','type=bind,src='+str(folder/'api.jar')+',dst=/app/app.jar,readonly','-p','127.0.0.1:43145:8080','--entrypoint','java',state['image'],'-Xms48m','-Xmx192m','-XX:MaxMetaspaceSize=160m','-Duser.timezone=Asia/Shanghai','-jar','/app/app.jar','--spring.profiles.active=mysql']);running=True
        ready=False;deadline=time.monotonic()+240
        while time.monotonic()<deadline:
            try:
                if request('/health').get('ok') is True:ready=True;break
            except Exception:pass
            if not inspect(name)['State']['Running']:break
            time.sleep(2)
        require(ready,'Baseline candidate did not become healthy.')
        for label,path in [('plan-list','/plans'),('todo-page','/todos/page?from=2026-10-01&to=2026-11-30&page=0&size=20'),('route-page','/organize/routes?page=0&size=20')]:
            for _ in range(3):require(sample(path)['ok'],'Warmup failed.')
            for concurrency in [1,2]:
                started=time.perf_counter()
                with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:rows=list(pool.map(sample,[path]*15))
                elapsed=time.perf_counter()-started;latencies=sorted(row['ms'] for row in rows)
                report['results'].append({'case':label,'concurrency':concurrency,'requests':len(rows),'errors':sum(not row['ok'] for row in rows),'p50Ms':round(latencies[math.ceil(len(rows)*.5)-1],2),'p95Ms':round(latencies[math.ceil(len(rows)*.95)-1],2),'maxMs':round(max(latencies),2),'observedRequestsPerSecondWithPacing':round(len(rows)/elapsed,2),'maxResponseBytes':max(row['bytes'] for row in rows)})
        report['containerSnapshot']=json.loads(command(['docker','stats','--no-stream','--format','{{json .}}',name]).decode())
        # Do not retain container identifiers or unrelated runtime fields in portable evidence.
        report['containerSnapshot']={key:report['containerSnapshot'].get(key) for key in ['CPUPerc','MemUsage','MemPerc','PIDs']}
        command(['docker','stop','--time','30',name]);running=False
        require(checksums()==before,'Read-only baseline changed synthetic data.');require(request('/health',base='https://plan.zijun701.cn/api')==public,'Public health changed.');require(request('/auth/config',base='https://plan.zijun701.cn/api')['registrationEnabled'] is False,'Public registration changed.')
        require(all(row['errors']==0 for row in report['results']),'Baseline recorded request failures; no capacity claim is allowed.')
        report.update(status='PASS',jarSha256=hashlib.sha256((folder/'api.jar').read_bytes()).hexdigest(),checkedTables=len(before),measuredAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    finally:
        if running:command(['docker','stop','--time','30',name])
        common.private_write(folder/'report.json',json.dumps(report,ensure_ascii=False,indent=2));print('Bounded baseline report: '+report['status'],flush=True)
if __name__=='__main__':main()
