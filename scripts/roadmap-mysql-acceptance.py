"""Run on ECS only: isolated synthetic V20/V21 migration, restore and old-image compatibility.

No production database reads/writes, real account material, AI calls or emails.
Docker root credentials remain in this process; no credential values are printed.
Generated databases, containers, private configuration and synthetic dump are retained.
"""
import argparse
import base64
import datetime as dt
import gzip
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request

BASE='http://127.0.0.1:43145/api'

def require(value,message):
    if not value:
        raise RuntimeError(message)

def command(args,*,data=None,env=None):
    result=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env)
    require(result.returncode==0,'An isolated Docker/MySQL step failed; private state retained. No command output was disclosed.')
    return result.stdout

def inspect(name):
    return json.loads(command(['docker','inspect',name]))[0]

def private_write(path,value):
    descriptor=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(descriptor,'wb' if isinstance(value,bytes) else 'w') as handle:
        handle.write(value)

def request(path,*,token=None,data=None,method=None,expected=200,base=BASE):
    headers={'Content-Type':'application/json'}
    if token:
        headers['Authorization']='Bearer '+token
    body=None if data is None else json.dumps(data,ensure_ascii=False).encode()
    req=urllib.request.Request(base+path,data=body,headers=headers,method=method or ('GET' if data is None else 'POST'))
    try:
        with urllib.request.urlopen(req,timeout=120) as response:
            status=response.status
            payload=response.read()
    except urllib.error.HTTPError as error:
        status=error.code
        payload=error.read()
    require(status==expected,'Unexpected status for synthetic '+(method or ('GET' if data is None else 'POST'))+' '+path+': '+str(status))
    return json.loads(payload) if payload and expected!=404 else None

def old_jar(production):
    entrypoint=production['Config'].get('Entrypoint') or []
    paths=[item for item in entrypoint if re.fullmatch(r'/app/[A-Za-z0-9_.-]+\.jar',item)]
    require(len(paths)==1,'Previous image JAR path was not uniquely identified.')
    return paths[0]

def verify_orchestration(token,other,plan,line,passed):
    uid=lambda:str(__import__('uuid').uuid4())
    def apply(kind,body,preview):
        request_body={'request':body,'checksum':preview['checksum'],'operationKey':uid(),'confirmed':True}
        result=request('/organize/'+kind+'/apply',token=token,data=request_body)
        require(request('/organize/'+kind+'/apply',token=token,data=request_body)==result,'Durable orchestration replay changed result.')
        return result
    copy={'kind':'PLAN','sourceId':plan['id'],'name':'MySQL 结构副本','startDate':plan['startDate'],'endDate':plan['endDate'],'restDaysMask':32}
    preview=request('/organize/copy/preview',token=token,data=copy)
    request('/organize/copy/preview',token=other,data=copy,expected=404)
    copied=apply('copy',copy,preview)
    tasks=request('/plans/'+copied['id']+'/tasks',token=token)
    require(len(tasks)==1 and tasks[0]['progress']['status']=='PENDING' and tasks[0]['progress']['accumulatedSeconds']==0,'Structural copy included execution history.')
    request('/mainlines/'+line['id']+'/paused',token=token,method='PUT',data={'paused':True,'version':line['version']})
    source=request('/mainlines/'+line['id']+'/steps/page?page=1&size=1',token=token)
    require(len(source['content'])==1 and not source['content'][0]['unlocked'],'Paged prerequisite/pause calculation used an incomplete graph.')
    request('/mainlines/items/'+source['content'][0]['id']+'/start?version='+str(source['content'][0]['version']),token=token,data=None,method='POST',expected=409)
    route_copy={'kind':'ROUTE','sourceId':line['id'],'name':'MySQL 路线结构副本'}
    result=apply('copy',route_copy,request('/organize/copy/preview',token=token,data=route_copy))
    steps=request('/mainlines/'+result['id']+'/steps/page?size=10',token=token)['content']
    require(steps[1]['prerequisiteIds']==[steps[0]['id']] and steps[0]['status']=='PENDING','Copied route graph was not remapped/reset.')
    tomorrow=(dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date()+dt.timedelta(days=1))
    task={'title':'MySQL 未来批次','date':tomorrow.isoformat(),'plannedMinutes':20,'category':'STUDY','acceptance':[],'required':True,'sortOrder':1}
    recurring=request('/plans/'+plan['id']+'/tasks/recurring',token=token,data={'task':task,'mode':'DAILY','until':(tomorrow+dt.timedelta(days=2)).isoformat()},expected=201)
    batch={'planId':plan['id'],'recurrenceId':recurring[0]['recurrenceId'],'from':tomorrow.isoformat(),'shiftDays':1,'plannedMinutes':30,'replaceTag':False}
    stale=request('/organize/batch/preview',token=token,data=batch)
    request('/tasks/'+recurring[0]['id']+'/manual-time',token=token,data={'seconds':10,'version':0})
    request('/organize/batch/apply',token=token,data={'request':batch,'checksum':stale['checksum'],'operationKey':uid(),'confirmed':True},expected=409)
    fresh=request('/organize/batch/preview',token=token,data=batch)
    require(len(fresh['changes'])==2 and len(fresh['protectedItems'])==1,'Batch failed to protect executed instance.')
    require(apply('batch',batch,fresh)['changed']==2,'Unexpected changed batch count.')
    require(request('/todos/page?from=2026-10-01&to=2026-11-30&page=0&size=1',token=token)['totalElements']==1,'Todo database page failed.')
    passed('MySQL structural copies, remapped dependencies, route pause, server pages, protected history, 409 and durable replay')


def resume_rollback(folder,final_jar=False):
    state=json.loads((folder/'candidate-private.json').read_text())
    report=json.loads((folder/'report.json').read_text())
    database,restored=state['database'],state['restoredDatabase']
    require(re.fullmatch(r'plan_acceptance_[0-9a-f_]+',database) and restored==database+'_restore','Refusing non-isolated database state.')
    production=inspect('plan-platform-backend-1')
    require(production['Image']==state['image'],'Production baseline changed; review before resuming.')
    jar='/app/app.jar' if final_jar else old_jar(production)
    target=database if final_jar else restored
    mysql=inspect('plan-platform-mysql-1')
    secret=next(value.split('=',1)[1] for value in mysql['Config']['Env'] if value.startswith('MYSQL_ROOT_PASSWORD='))
    environment={**os.environ,'MYSQL_PWD':secret}
    def sql(value):
        return command(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','--batch','--skip-column-names'],data=value.encode(),env=environment).decode().strip()
    def checksum(db):
        tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name").splitlines()
        values={}
        for table in tables:
            require(re.fullmatch('[a-z0-9_]+',table),'Unexpected table.')
            values[table]=sql('CHECKSUM TABLE '+db+'.'+table+' EXTENDED').split()[-1]
            require(values[table]!='NULL','Unavailable table checksum.')
        return values
    before=checksum(database)
    require(before==checksum(restored),'Retained restore database differs from the original synthetic snapshot.')
    # This token is a fixture signed only by the independent candidate secret.
    # No real user's password, account or production JWT secret is used.
    owner=sql("SELECT id FROM "+target+".users WHERE role='USER' ORDER BY id LIMIT 1")
    require(re.fullmatch('[a-f0-9-]{36}',owner),'Synthetic account missing.')
    encode=lambda value:base64.urlsafe_b64encode(json.dumps(value,separators=(',',':')).encode()).rstrip(b'=').decode()
    now=int(time.time())
    unsigned=encode({'alg':'HS256','typ':'JWT'})+'.'+encode({'iss':'plan-platform','sub':owner,'role':'USER','iat':now,'exp':now+600})
    token=unsigned+'.'+base64.urlsafe_b64encode(hmac.new(state['jwt'].encode(),unsigned.encode(),hashlib.sha256).digest()).rstrip(b'=').decode()
    name='plan-roadmap-'+database[len('plan_acceptance_'):].replace('_','-')+('' if final_jar else '-rollback-verified')
    existing=bool(command(['docker','ps','-aq','--filter','name=^'+name+'$']).strip())
    if existing:
        retained=inspect(name)
        environment_values=dict(value.split('=',1) for value in retained['Config']['Env'] if '=' in value)
        require(not retained['State']['Running'] and retained['Image']==state['image'] and jar in (retained['Config'].get('Cmd') or []) and environment_values.get('DB_URL','').startswith('jdbc:mysql://plan-platform-mysql-1:3306/'+target+'?'),'Retained container no longer matches the isolated fixture.')
    require(not final_jar or existing,'Final-JAR check requires the retained candidate container.')
    original_report=folder/('report-before-rollback-resume-'+str(now)+'.json')
    require(not original_report.exists(),'Evidence path already exists.')
    (folder/'report.json').rename(original_report)
    report['status']='FAIL'
    report['failure']='Resumed verification did not finish; inspect retained evidence before claiming success.'
    running=False
    try:
        if existing:
            command(['docker','start',name])
        else:
            command(['docker','run','-d','--name',name,'--network',state['network'],'--memory','480m','--cpus','0.6','--pids-limit','256','--env-file',str(folder/'rollback.env'),'-p','127.0.0.1:43145:8080','--entrypoint','java',state['image'],'-Xms48m','-Xmx192m','-XX:MaxMetaspaceSize=160m','-Duser.timezone=Asia/Shanghai','-jar',jar,'--spring.profiles.active=mysql'])
        running=True
        health=None
        deadline=time.monotonic()+240
        while time.monotonic()<deadline:
            try:
                health=request('/health')
                if health.get('ok') is True:
                    break
            except (RuntimeError,urllib.error.URLError,TimeoutError,ConnectionError):
                pass
            if not inspect(name)['State']['Running']:
                break
            time.sleep(2)
        require(health and health.get('ok') is True,'Previous image failed to become healthy after path correction.')
        if final_jar:
            require(len(request('/data/backup',token=token)['records']['todos'])==1,'Final streaming backup failed.')
            req=urllib.request.Request(BASE+'/data/export/todos',headers={'Authorization':'Bearer '+token})
            with urllib.request.urlopen(req,timeout=120) as response:
                require(response.status==200 and response.read().startswith(b'\xef\xbb\xbf'),'Final streaming CSV failed.')
        else:
            request('/data/backup',token=token,expected=404)
        require(len(request('/todos?from=2026-10-18&to=2026-10-18',token=token))==1,'Old image cannot read restored synthetic todo.')
        command(['docker','stop','--time','30',name]);running=False
        require(before==checksum(target),'Read-only verification changed business data.')
        if final_jar:
            report['finalJarSha256']=hashlib.sha256((folder/'api.jar').read_bytes()).hexdigest()
            report['cases'].append({'name':'final JAR MySQL streaming private backup and CSV with unchanged table checksums','status':'PASS'})
        else:
            report['oldImageHealth']=health
            report['cases'].append({'name':'actual previous 0.1.14 image reads upgraded isolated database without changing it','status':'PASS'})
        report['productionAfter']=request('/health',base='https://plan.zijun701.cn/api')
        require(report['productionBefore']==report['productionAfter'],'Production health changed.')
        require(request('/auth/config',base='https://plan.zijun701.cn/api')['registrationEnabled'] is False,'Public registration policy changed.')
        report['cases'].append({'name':'public health and closed registration unchanged','status':'PASS'})
        report.update({'status':'PASS','database':database,'restoredDatabase':restored,'checkedTables':len(before),'rollbackResume':'Corrected old image JAR path; did not repeat migration or synthetic restore.'})
        report.pop('failure',None)
        print('PASS previous-image rollback compatibility and unchanged public health',flush=True)
    finally:
        if running:
            command(['docker','stop','--time','30',name])
        private_write(folder/'report.json',json.dumps(report,ensure_ascii=False,indent=2))
        print('Report status: '+report['status'],flush=True)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--directory',required=True)
    parser.add_argument('--resume-rollback',action='store_true')
    parser.add_argument('--verify-final-jar',action='store_true')
    parser.add_argument('--stage3',action='store_true')
    args=parser.parse_args()
    folder=Path(args.directory).resolve()
    require(str(folder).startswith('/home/oyts/plan-platform/roadmap-candidates/'),'Refusing a directory outside roadmap-candidates.')
    require(folder.is_dir() and (folder/'api.jar').is_file(),'Upload the candidate JAR first.')
    os.umask(0o077)
    if args.resume_rollback or args.verify_final_jar:
        resume_rollback(folder,args.verify_final_jar)
        return
    private_write(folder/'started.json',json.dumps({'startedAt':dt.datetime.now(dt.timezone.utc).isoformat()}))
    report={'status':'FAIL','paidAiCalls':0,'realEmailsSent':0,'productionDatabaseAccessed':False,'cases':[]}
    owned=[]
    run=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d%H%M%S')+'_'+secrets.token_hex(3)
    database='plan_acceptance_'+run
    restored=database+'_restore'
    username='pr_'+secrets.token_hex(8)
    password=secrets.token_hex(32)
    jwt=secrets.token_hex(48)
    account_password=secrets.token_hex(20)+'A1!'
    mysql=inspect('plan-platform-mysql-1')
    production=inspect('plan-platform-backend-1')
    image=production['Image']
    network=next(name for name in production['NetworkSettings']['Networks'] if name in mysql['NetworkSettings']['Networks'])
    root_password=next(value.split('=',1)[1] for value in mysql['Config']['Env'] if value.startswith('MYSQL_ROOT_PASSWORD='))
    mysql_environment={**os.environ,'MYSQL_PWD':root_password}
    def sql(statement):
        return command(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','--batch','--skip-column-names'],data=statement.encode(),env=mysql_environment).decode().strip()
    def candidate_env(db):
        require(re.fullmatch(r'plan_acceptance_[0-9a-f_]+(?:_restore)?',db),'Unexpected isolated database name.')
        return '\n'.join(['DB_URL=jdbc:mysql://plan-platform-mysql-1:3306/'+db+'?serverTimezone=Asia/Shanghai','DB_USERNAME='+username,'DB_PASSWORD='+password,'JWT_SECRET='+jwt,'SPRING_PROFILES_ACTIVE=mysql','REGISTRATION_ENABLED=true','REFRESH_COOKIE_SECURE=false','REFRESH_COOKIE_NAME=plan_roadmap_refresh','BOOTSTRAP_ADMIN_EMAIL=','AI_ENABLED=false','PASSWORD_RESET_ENABLED=false','EMAIL_VERIFICATION_ENABLED=false','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3','CORS_ALLOWED_ORIGINS=http://127.0.0.1:43145','APP_PUBLIC_URL=http://127.0.0.1:43145'])+'\n'
    def start(name,env_file,jar=False):
        require(not command(['docker','ps','-aq','--filter','name=^'+name+'$']).strip(),'Candidate container name already exists; nothing was replaced.')
        command_args=['docker','run','-d','--name',name,'--network',network,'--memory','480m','--cpus','0.6','--pids-limit','256','--env-file',str(env_file),'-p','127.0.0.1:43145:8080']
        if jar:
            command_args+=['--mount','type=bind,src='+str(folder/'api.jar')+',dst=/app/app.jar,readonly']
        command_args+=['--entrypoint','java',image,'-Xms48m','-Xmx192m','-XX:MaxMetaspaceSize=160m','-XX:+ExitOnOutOfMemoryError','-Duser.timezone=Asia/Shanghai','-jar','/app/app.jar' if jar else old_jar(production),'--spring.profiles.active=mysql']
        command(command_args)
        owned.append(name)
        deadline=time.monotonic()+240
        while time.monotonic()<deadline:
            try:
                health=request('/health')
                if health.get('ok') is True:
                    return health
            except (RuntimeError,urllib.error.URLError,TimeoutError,ConnectionError):
                pass
            if not inspect(name)['State']['Running']:
                break
            time.sleep(2)
        logs=subprocess.run(['docker','logs',name],stdout=subprocess.PIPE,stderr=subprocess.STDOUT).stdout
        private_write(folder/(name+'.log'),logs.decode(errors='replace'))
        raise RuntimeError('Isolated candidate did not become healthy; restricted logs retained.')
    def stop(name):
        command(['docker','stop','--time','30',name])
        owned.remove(name)
    def passed(name):
        report['cases'].append({'name':name,'status':'PASS'})
        print('PASS '+name,flush=True)
    def account():
        return request('/auth/register',data={'email':'roadmap-'+secrets.token_hex(8)+'@example.test','password':account_password,'displayName':'合成 MySQL 验收'},expected=201)
    def checksum(db):
        tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name").splitlines()
        values={}
        for table in tables:
            require(re.fullmatch('[a-z0-9_]+',table),'Unexpected table name.')
            value=sql('CHECKSUM TABLE '+db+'.'+table+' EXTENDED').split()[-1]
            require(value!='NULL','Table checksum unavailable.')
            values[table]=value
        return values
    try:
        report['productionBefore']=request('/health',base='https://plan.zijun701.cn/api')
        report['jarSha256']=hashlib.sha256((folder/'api.jar').read_bytes()).hexdigest()
        private_write(folder/'candidate-private.json',json.dumps({'database':database,'restoredDatabase':restored,'username':username,'password':password,'jwt':jwt,'image':image,'network':network}))
        sql('CREATE DATABASE '+database+' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER \''+username+"'@'%' IDENTIFIED BY '"+password+"'; GRANT ALL ON "+database+".* TO '"+username+"'@'%';")
        private_write(folder/'candidate.env',candidate_env(database))
        current='plan-roadmap-'+run.replace('_','-')
        report['candidateHealth']=start(current,folder/'candidate.env',True)
        expected_version='22' if args.stage3 else '21'
        require(sql('SELECT max(version+0) FROM '+database+'.flyway_schema_history WHERE success=1')==expected_version,'Unexpected migration version.')
        passed('fresh MySQL 8.4 V1-V'+expected_version+' migration')
        a,b=account(),account()
        token=a['accessToken']
        other=b['accessToken']
        body={'operationKey':str(__import__('uuid').uuid4())}
        if not args.stage3:
            body={'operationKey':secrets.token_hex(4)+'-'+secrets.token_hex(2)+'-'+secrets.token_hex(2)+'-'+secrets.token_hex(2)+'-'+secrets.token_hex(6),'category':'USABILITY','title':'合成 MySQL 反馈','steps':'打开合成教学。','expectedResult':'提示清楚。','actualResult':'需要补充说明。','diagnostic':{'schemaVersion':1,'clientVersion':'0.1.14','platform':'WINDOWS','occurredAt':'2026-10-18T00:00:00Z','errorKind':'NONE','queueCount':0,'conflictCount':0}}
            feedback=request('/feedback',token=token,data=body,expected=201)
            require(request('/feedback',token=token,data=body,expected=201)['ticket']['id']==feedback['ticket']['id'],'Feedback replay created a duplicate.')
            request('/feedback/'+feedback['ticket']['id'],token=other,expected=404)
            request('/admin/feedback',token=other,expected=403)
            require(re.fullmatch('[a-f0-9-]{36}',a['user']['id']),'Unexpected synthetic owner id.')
            sql("UPDATE "+database+".users SET role='ADMIN' WHERE id='"+a['user']['id']+"'")
            admin=request('/auth/login',data={'email':a['user']['email'],'password':account_password})['accessToken']
            handled=request('/admin/feedback/'+feedback['ticket']['id'],token=admin,method='PUT',data={'status':'RESOLVED','body':'合成处理说明。','version':0})
            require(handled['ticket']['version']==1,'Feedback must advance exactly one version.')
            passed('feedback isolation admin handling and durable replay')
        plan=request('/plans',token=token,data={'name':'MySQL 合成周期','startDate':'2026-10-01','endDate':'2026-11-30','restDays':[7]},expected=201)
        task=request('/plans/'+plan['id']+'/tasks',token=token,data={'title':'合成流水任务','date':'2026-10-18','plannedMinutes':20,'category':'STUDY','acceptance':[],'required':True,'sortOrder':0},expected=201)
        progress=request('/tasks/'+task['id']+'/manual-time',token=token,data={'seconds':120,'version':0})
        request('/tasks/'+task['id']+'/complete',token=token,data={'note':'合成完成记录','acceptanceChecks':[],'version':progress['version']})
        request('/todos',token=token,data={'title':'MySQL 合成待办','dueDate':'2026-10-18'},expected=201)
        line=request('/mainlines',token=token,data={'name':'MySQL 合成路线','description':'长期目标','color':'#002FA7','executionMode':'SEQUENTIAL'},expected=201)
        first=request('/mainlines/'+line['id']+'/items',token=token,data={'title':'基础','plannedMinutes':20,'sortOrder':0,'acceptance':[],'routing':{'dependencyMode':'NONE','prerequisiteIds':[]}},expected=201)
        request('/mainlines/'+line['id']+'/items',token=token,data={'title':'进阶','plannedMinutes':20,'sortOrder':1,'acceptance':[],'routing':{'dependencyMode':'EXPLICIT','prerequisiteIds':[first['id']]}},expected=201)
        request('/weekly/reviews/2026-10-12',token=token,method='PUT',data={'wins':'合成来源复盘','blockers':'无','nextFocus':'小步推进'})
        request('/weekly/reviews/2026-10-12',token=other,method='PUT',data={'wins':'合成目标原复盘','blockers':'','nextFocus':''})
        backup=request('/data/backup',token=token)
        preview=request('/data/preview',token=other,data=backup)
        restore_body={'backup':backup,'checksum':preview['checksum'],'operationKey':body['operationKey'],'confirmed':True}
        restored_result=request('/data/restore',token=other,data=restore_body)
        require(request('/data/restore',token=other,data=restore_body)['batchId']==restored_result['batchId'],'Restore replay created a duplicate batch.')
        require(request('/weekly/reviews/2026-10-12',token=other)['wins']=='合成目标原复盘','Current weekly review was overwritten.')
        require(request('/data/restored-weekly-reviews',token=other)['content'][0]['wins']=='合成来源复盘','Weekly copy was not retained.')
        copied=request('/data/backup',token=other)
        require(len(copied['records']['time_entries'])==1 and copied['records']['time_entries'][0]['seconds']==120,'Ledger was not preserved.')
        require(copied['records']['plans'][0]['id']!=plan['id'],'Plan id was not remapped.')
        restored_items={row['title']:row for row in copied['records']['mainline_items']}
        require(restored_items['进阶']['prerequisite_ids_json']==[restored_items['基础']['id']],'Route prerequisite was not remapped.')
        passed('three-mode MySQL backup preview atomic new-copy restore and replay')
        if args.stage3:
            verify_orchestration(token,other,plan,line,passed)
        before=checksum(database)
        dump=command(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','-uroot','--single-transaction','--no-tablespaces','--set-gtid-purged=OFF',database],env=mysql_environment)
        require(not re.search(rb'(?im)^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',dump),'Database-switching SQL refused.')
        private_write(folder/'synthetic-mysql.sql.gz',gzip.compress(dump))
        sql('CREATE DATABASE '+restored+' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON '+restored+".* TO '"+username+"'@'%';")
        command(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot',restored],data=dump,env=mysql_environment)
        require(before==checksum(restored),'Restored synthetic database checksums differ.')
        passed('synthetic MySQL dump restore and all-table checksum comparison')
        stop(current)
        private_write(folder/'rollback.env',candidate_env(restored))
        old=current+'-rollback'
        report['oldImageHealth']=start(old,folder/'rollback.env')
        request('/data/backup',token=other,expected=404)
        request('/todos?from=2026-10-18&to=2026-10-18',token=other)
        stop(old)
        require(before==checksum(restored),'Old image startup changed restored data.')
        passed('actual previous 0.1.14 image reads upgraded isolated database without changing it')
        report['productionAfter']=request('/health',base='https://plan.zijun701.cn/api')
        require(report['productionBefore']==report['productionAfter'],'Public health changed during isolated verification.')
        require(request('/auth/config',base='https://plan.zijun701.cn/api')['registrationEnabled'] is False,'Public registration policy changed.')
        passed('public health and closed registration unchanged')
        report['database']=database
        report['restoredDatabase']=restored
        report['checkedTables']=len(before)
        report['status']='PASS'
    except Exception:
        report['failure']='Isolated verification failed. Private configuration and generated data retained; no production cutover was attempted.'
        raise
    finally:
        for name in list(owned):
            try:
                stop(name)
            except Exception:
                report['stopRequired']=True
        private_write(folder/'report.json',json.dumps(report,ensure_ascii=False,indent=2))
        print('Report status: '+report['status'],flush=True)

if __name__=='__main__':
    main()
