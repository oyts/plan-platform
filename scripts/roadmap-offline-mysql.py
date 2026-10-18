#!/usr/bin/env python3
"""Read-only stage-4 queries against the retained synthetic V22 database. Never production data."""
import argparse,base64,datetime,hashlib,hmac,importlib.util,json,os,re,time,urllib.parse
from pathlib import Path

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--directory',required=True);parser.add_argument('--state-directory',required=True);args=parser.parse_args()
    folder=Path(args.directory).resolve();source=Path(args.state_directory).resolve();root='/home/oyts/plan-platform/roadmap-candidates/'
    if not all(str(path).startswith(root) for path in [folder,source]) or folder==source:raise RuntimeError('Separate roadmap candidate directories required.')
    spec=importlib.util.spec_from_file_location('roadmap_common',folder/'roadmap-mysql-acceptance.py');common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
    require,command,inspect,request=common.require,common.command,common.inspect,common.request
    os.umask(0o077);common.private_write(folder/'started.json',json.dumps({'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}))
    report={'status':'FAIL','productionDatabaseAccessed':False,'paidAiCalls':0,'realEmailsSent':0,'cases':[]};running=False
    state=json.loads((source/'candidate-private.json').read_text());database=state['database'];require(re.fullmatch('plan_acceptance_[0-9a-f_]+',database),'Refusing non-synthetic database.')
    production=inspect('plan-platform-backend-1');require(production['Image']==state['image'],'Production baseline changed; inspect before resuming.')
    mysql=inspect('plan-platform-mysql-1');password=next(item.split('=',1)[1] for item in mysql['Config']['Env'] if item.startswith('MYSQL_ROOT_PASSWORD='));env={**os.environ,'MYSQL_PWD':password}
    def sql(value):return command(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','--batch','--skip-column-names'],data=value.encode(),env=env).decode().strip()
    def checksums():
        tables=sql("select table_name from information_schema.tables where table_schema='"+database+"' order by table_name").splitlines();result={}
        for table in tables:
            require(re.fullmatch('[a-z0-9_]+',table),'Unexpected table.');result[table]=sql('checksum table '+database+'.'+table+' extended').split()[-1]
            require(result[table]!='NULL','Table checksum unavailable.')
        return result
    owner=sql("select id from "+database+".users where role='USER' order by id limit 1");require(re.fullmatch('[a-f0-9-]{36}',owner),'Synthetic owner missing.')
    todo=sql("select id from "+database+".todos where owner_id='"+owner+"' order by id limit 1");require(re.fullmatch('[a-f0-9-]{36}',todo),'Synthetic todo missing.')
    encode=lambda value:base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).rstrip(b'=').decode()
    now=int(time.time());unsigned=encode({'alg':'HS256','typ':'JWT'})+'.'+encode({'iss':'plan-platform','sub':owner,'role':'USER','iat':now,'exp':now+900});token=unsigned+'.'+base64.urlsafe_b64encode(hmac.new(state['jwt'].encode(),unsigned.encode(),hashlib.sha256).digest()).rstrip(b'=').decode()
    name='plan-roadmap-offline-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S');common.private_write(folder/'candidate.env',(source/'candidate.env').read_text())
    try:
        before=checksums();health=request('/health',base='https://plan.zijun701.cn/api')
        command(['docker','run','-d','--name',name,'--network',state['network'],'--memory','480m','--cpus','0.6','--pids-limit','256','--env-file',str(folder/'candidate.env'),'--mount','type=bind,src='+str(folder/'api.jar')+',dst=/app/app.jar,readonly','-p','127.0.0.1:43145:8080','--entrypoint','java',state['image'],'-Xms48m','-Xmx192m','-XX:MaxMetaspaceSize=160m','-Duser.timezone=Asia/Shanghai','-jar','/app/app.jar','--spring.profiles.active=mysql']);running=True
        ready=False;deadline=time.monotonic()+240
        while time.monotonic()<deadline:
            try:
                if request('/health').get('ok') is True:ready=True;break
            except Exception:pass
            if not inspect(name)['State']['Running']:break
            time.sleep(2)
        require(ready,'Isolated offline candidate did not become healthy.')
        selected=request('/sync/record?'+urllib.parse.urlencode({'path':'/todos/'+todo}),token=token)
        require(set(selected)=={'version','fields'} and set(selected['fields'])=={'title','note','dueDate','done','deleted'},'Editable snapshot whitelist differs.')
        request('/sync/record?'+urllib.parse.urlencode({'path':'/auth/me'}),token=token,expected=400)
        request('/tasks/carryover?from=2026-10-18&to=2026-10-24',token=token)
        backup=request('/data/backup',token=token);require(len(backup['records']['todos'])==1,'Streaming private backup read failed.')
        report['cases'].append('MySQL selected editable snapshot, whitelist, bounded carryover and private backup reads')
        command(['docker','stop','--time','30',name]);running=False
        require(checksums()==before,'Read-only candidate changed retained synthetic data.')
        require(request('/health',base='https://plan.zijun701.cn/api')==health,'Public health changed.')
        require(request('/auth/config',base='https://plan.zijun701.cn/api')['registrationEnabled'] is False,'Public registration policy changed.')
        report.update(status='PASS',container=name,checkedTables=len(before),jarSha256=hashlib.sha256((folder/'api.jar').read_bytes()).hexdigest());report['cases'].append('All synthetic table checksums and public health/closed registration unchanged')
    finally:
        if running:command(['docker','stop','--time','30',name])
        common.private_write(folder/'report.json',json.dumps(report,indent=2));print('Report status: '+report['status'],flush=True)

if __name__=='__main__':main()
