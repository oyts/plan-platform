#!/usr/bin/env python3
"""Isolated V32->V33 exercise; never mounts AI/mail secrets or writes production."""
import concurrent.futures,datetime as dt,gzip,hashlib,io,json,os,pathlib,re,secrets,subprocess,time,traceback,urllib.request,urllib.error,uuid

def main():
    root=pathlib.Path('/home/oyts/plan-platform');folder=pathlib.Path.cwd().resolve()
    assert re.fullmatch(str(root)+r'/admin-center-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(folder))
    os.umask(0o077)
    with (folder/'started.json').open('x') as f:json.dump({'started':dt.datetime.now(dt.timezone.utc).isoformat()},f)
    suffix=folder.name[-8:];database='plan_acceptance_'+folder.name[:8]+'_'+suffix;restored='admin_restore_'+suffix;user='admin_test_'+suffix
    port=43181;containers=[];env=dict(os.environ);report=dict(status='FAIL',productionWrites=0,paidAiCalls=0,realEmails=0,cases=[])
    for line in (root/'current/.env').read_text().splitlines():
        if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
    assert env.get('MYSQL_PWD'),'Restricted DB authentication unavailable'
    def cmd(argv,data=None,expected=0):
        p=subprocess.run(argv,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=180)
        if p.returncode!=expected:
            (folder/'command-error.private').write_bytes(p.stderr)
            raise RuntimeError('Isolated command failed; private error retained')
        return p.stdout
    def sql(statement,db=None):return cmd(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','--default-character-set=utf8mb4','-uroot','-N','--batch',*([db] if db else [])],statement.encode()).decode().strip()
    def api(path,data=None,token=None,status=200,method=None):
        request=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,None if data is None else json.dumps(data,ensure_ascii=False).encode(),{'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})},method=method)
        try:r=urllib.request.urlopen(request,timeout=45)
        except urllib.error.HTTPError as e:r=e
        with r:
            body=r.read();assert r.code==status,f'Synthetic status {r.code}, expected {status}, path {path}'
        return json.loads(body) if body else None
    def health():return json.load(urllib.request.urlopen('https://plan.zijun701.cn/api/health',timeout=20))
    def start(label,new=True):
        assert not re.search(':'+str(port)+r'\s',cmd(['ss','-lnt']).decode()),'Isolated port occupied'
        name='plan-admin-'+label+'-'+suffix
        args=['docker','run','-d','--name',name,'--network','plan-platform_default','--memory','480m','--cpus','0.6','-p',f'127.0.0.1:{port}:8080','--env-file',str(folder/'candidate.env'),'--entrypoint','java']
        if new:args+=['-v',str(folder/'api.jar')+':/candidate/api.jar:ro']
        cmd(args+['plan-platform-api:0.1.22','-jar','/candidate/api.jar' if new else '/app/api.jar','--app.ai.worker-enabled=false']);containers.append(name)
        for _ in range(120):
            assert cmd(['docker','inspect','--format','{{.State.Running}}',name]).strip()==b'true','Synthetic service exited'
            try:
                if api('/health')['ok']:return name
            except (OSError,AssertionError):pass
            time.sleep(2)
        raise RuntimeError('Isolated readiness timeout')
    def stop(name):cmd(['docker','stop',name])
    def columns(db):
        return {t:sql("SELECT column_name FROM information_schema.columns WHERE table_schema='"+db+"' AND table_name='"+t+"' ORDER BY ordinal_position").split() for t in sql("SELECT table_name FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name").split()}
    def projections(db,tables,history=False):
        answer={}
        for table,cols in tables.items():
            if table=='flyway_schema_history' and not history:continue
            assert all(re.fullmatch('[a-zA-Z0-9_]+',s) for s in [table,*cols])
            fields=','.join('`'+s+'`' for s in cols)
            answer[table]=hashlib.sha256(sql('SELECT '+fields+' FROM `'+table+'` ORDER BY '+fields,db).encode()).hexdigest()
        return answer
    def account(role):
        credentials={'email':'admin-mysql-'+uuid.uuid4().hex+'@example.test','password':'Synthetic-'+secrets.token_urlsafe(28)+'1','displayName':'管理中心合成'+role}
        identity=api('/auth/register',credentials,status=201)
        if role=='ADMIN':sql("UPDATE users SET role='ADMIN' WHERE id='"+identity['user']['id']+"'",database)
        return api('/auth/login',{k:credentials[k] for k in ['email','password']})
    def guard(aware,blocked):
        p=subprocess.run(['python3',str(folder/'check-governance-rollback.py'),'--database',database,'--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','yes' if aware else 'no'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=30)
        assert (p.returncode!=0)==blocked
        if blocked:assert b'admin runtime' in p.stderr
    try:
        expected=(folder/'api.jar.sha256').read_text().split()[0];assert re.fullmatch('[a-f0-9]{64}',expected) and hashlib.sha256((folder/'api.jar').read_bytes()).hexdigest()==expected
        (folder/'api.jar').chmod(0o644);report['apiSha256']=expected;before=health();assert before['version']=='0.1.22'
        memory=int(next(line.split()[1] for line in pathlib.Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemAvailable:')));assert memory>600000,'Insufficient spare memory; do not disturb production'
        assert sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('"+database+"','"+restored+"')")=='0'
        password=secrets.token_urlsafe(32);sql(f"CREATE DATABASE {database} CHARACTER SET utf8mb4; CREATE USER '{user}'@'%' IDENTIFIED BY '{password}'; GRANT ALL ON {database}.* TO '{user}'@'%';")
        cfg={'SPRING_DATASOURCE_URL':f'jdbc:mysql://mysql:3306/{database}?serverTimezone=Asia/Shanghai','SPRING_DATASOURCE_USERNAME':user,'SPRING_DATASOURCE_PASSWORD':password,'SPRING_DATASOURCE_DRIVER_CLASS_NAME':'com.mysql.cj.jdbc.Driver','JWT_SECRET':secrets.token_urlsafe(48),'JWT_ISSUER':'synthetic-admin','REGISTRATION_ENABLED':'true','INVITATION_ENABLED':'true','EMAIL_VERIFICATION_ENABLED':'false','EMAIL_CHANGE_ENABLED':'false','PASSWORD_RESET_ENABLED':'false','REFRESH_COOKIE_SECURE':'false','AI_ENABLED':'true','AI_API_KEY_FILE':'/synthetic-never-mounted','AI_DEFAULT_DAILY_LIMIT':'20','AI_MAX_CONCURRENT':'2','AI_QUEUE_CAPACITY':'4','APP_AI_WORKER_ENABLED':'false','OPERATIONS_ENABLED':'false','MEMBER_GOVERNANCE_ENABLED':'false','OPERATIONS_MAIL_ALERTS_ENABLED':'false','SPRING_AUTOCONFIGURE_EXCLUDE':'org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration','JAVA_TOOL_OPTIONS':'-Xms64m -Xmx224m -XX:MaxMetaspaceSize=144m -XX:ReservedCodeCacheSize=48m -Xss512k','SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE':'2'}
        with (folder/'candidate.env').open('x') as f:f.write(''.join(k+'='+v+'\n' for k,v in cfg.items()))
        old=start('v32',False);admin=account('ADMIN');other=account('ADMIN');member=account('USER');a=admin['accessToken'];b=other['accessToken'];u=member['accessToken']
        assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',database)=='32'
        sql('UPDATE ai_quota_policy SET daily_limit=20 WHERE id=1',database)
        s=api('/ai/sessions',{'mode':'TODO','consent':True},u);oldjob=api('/ai/sessions/'+s['id']+'/jobs',{'version':s['version'],'message':'合成讨论，不生成任务','intent':'DIALOGUE','operationKey':str(uuid.uuid4())},u,status=202)
        invitebody={'email':'synthetic-invite-'+uuid.uuid4().hex+'@example.test','expiresInHours':24,'operationKey':str(uuid.uuid4())};originalInvite=api('/admin/invitations',invitebody,a,status=201)
        oldcols=columns(database);original=projections(database,oldcols);stop(old)
        new=start('v33');assert sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history',database)=='33';assert projections(database,oldcols)==original
        assert sql('SELECT COUNT(*) FROM ai_jobs WHERE runtime_version IS NOT NULL',database)=='0';assert sql('SELECT settings_json IS NULL FROM ai_runtime_policy WHERE id=1',database)=='1'
        initial=api('/admin/ai/runtime',token=a);assert initial['limits']['maxConcurrent']==2 and initial['limits']['queueCapacity']==4;guard(False,False)
        stop(new);legacy=start('v32-read',False);assert api('/ai/sessions/'+s['id'],token=u)['id']==s['id'];assert api('/admin/invitations',token=a)[0]['id']==originalInvite['id'];stop(legacy)
        report['originalProjectionTables']=len(original);report['cases'].append('V32->V33 every original-column projection unchanged; pre-protocol actual0.1.22 read compatibility')
        new=start('exercise');api('/admin/ai/runtime',token=u,status=403);api('/admin/access',token=u,status=403)
        limits={**initial['limits'],'maxConcurrent':1,'queueCapacity':0,'maxOutputTokens':512,'totalOutputTokens':1024,'timeoutSeconds':75}
        changed=api('/admin/ai/runtime',{'version':initial['version'],'limits':limits},a,method='PUT');api('/admin/ai/runtime',{'version':initial['version'],'limits':limits},b,status=409,method='PUT')
        assert changed['limits']==limits and api('/ai/quota',token=u)['pending']==1
        legacy=sql("SELECT runtime_input_chars,runtime_output_tokens,runtime_total_output_tokens,runtime_timeout_seconds,runtime_version FROM ai_jobs WHERE id='"+oldjob['id']+"'",database)
        assert legacy=='\t'.join(map(str,[initial['limits']['maxInputChars'],initial['limits']['maxOutputTokens'],initial['limits']['totalOutputTokens'],initial['limits']['timeoutSeconds'],initial['version']]));assert sql('SELECT state FROM ai_jobs WHERE id=\''+oldjob['id']+'\'',database)=='QUEUED'
        # One unfinished request per owner remains in force independently of global capacity.
        busySession=api('/ai/sessions',{'mode':'TODO','consent':True},u);api('/ai/sessions/'+busySession['id']+'/jobs',{'version':busySession['version'],'message':'同账号合成拒绝','intent':'DIALOGUE','operationKey':str(uuid.uuid4())},u,status=429)
        second=account('USER')['accessToken'];s2=api('/ai/sessions',{'mode':'TODO','consent':True},second);jobbody={'version':s2['version'],'message':'合成第二条','intent':'DIALOGUE','operationKey':str(uuid.uuid4())};api('/ai/sessions/'+s2['id']+'/jobs',jobbody,second,status=503)
        raised=api('/admin/ai/runtime',{'version':changed['version'],'limits':{**limits,'maxConcurrent':2,'queueCapacity':2}},a,method='PUT')
        job=api('/ai/sessions/'+s2['id']+'/jobs',jobbody,second,status=202);assert api('/ai/sessions/'+s2['id']+'/jobs',jobbody,second,status=202)['id']==job['id']
        snap=sql("SELECT runtime_input_chars,runtime_output_tokens,runtime_total_output_tokens,runtime_timeout_seconds,runtime_version FROM ai_jobs WHERE id='"+job['id']+"'",database)
        assert snap=='\t'.join(map(str,[limits['maxInputChars'],512,1024,75,raised['version']]))
        assert sql('SELECT COUNT(*) FROM ai_job_attempts',database)=='0';guard(False,True);guard(True,False)
        report['cases'].append('Numeric policy persisted, stale409, USER403, lower admission drains existing queue, quota intact and new jobs snapshot exact limits')
        state=api('/admin/ai/runtime',token=a);body={'version':state['version'],'limits':{**state['limits'],'timeoutSeconds':76}}
        def race(token):
            req=urllib.request.Request(f'http://127.0.0.1:{port}/api/admin/ai/runtime',json.dumps(body).encode(),{'Content-Type':'application/json','Authorization':'Bearer '+token},method='PUT')
            try:r=urllib.request.urlopen(req,timeout=45)
            except urllib.error.HTTPError as e:r=e
            with r:r.read();return r.code
        with concurrent.futures.ThreadPoolExecutor(2) as pool:assert sorted(pool.map(race,[a,b]))==[200,409]
        audit=api('/admin/ai/runtime/audit',token=a);current=api('/admin/ai/runtime',token=a);restoredPolicy=api('/admin/ai/runtime/restore/'+audit['items'][0]['id'],{'version':current['version'],'confirmed':True},a)
        assert restoredPolicy['limits']==audit['items'][0]['before']['limits'];assert api('/ai/quota',token=u)['pending']==1 and api('/ai/quota',token=second)['pending']==1
        report['cases'].append('Concurrent two-admin one commit, complete before/after audit, explicit restore creates new version without resetting quota')
        # The original invitation still works until a successful replacement; all content is synthetic.
        sql("UPDATE invitations SET created_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 61 SECOND) WHERE id='"+originalInvite['id']+"'",database)
        replacement=api('/admin/invitations',invitebody,a,status=201);assert replacement['id']!=originalInvite['id'] and replacement['deliveryStatus']=='FALLBACK'
        replay=api('/admin/invitations',invitebody,a,status=201);assert replay['id']==replacement['id'] and replay['replayed'] and replay['registrationUrl'] is None
        api('/admin/invitations',{**invitebody,'operationKey':str(uuid.uuid4())},b,status=429)
        assert sql("SELECT revoked_at IS NOT NULL FROM invitations WHERE id='"+originalInvite['id']+"'",database)=='1'
        assert sql('SELECT COUNT(*) FROM admin_invitation_operations',database)=='1';assert sql("SELECT COUNT(*) FROM invitations WHERE email='"+invitebody['email']+"'",database)=='2'
        # Actual old ADMIN JWT is refused immediately when the current database role no longer grants access.
        sql("UPDATE users SET role='USER' WHERE id='"+other['user']['id']+"'",database);api('/admin/overview',token=b,status=403)
        report['cases'].append('Durable invitation replay has no link or second delivery, shared cooldown, replacement revokes only on creation; stale ADMIN JWT denied')
        kept=api('/admin/ai/runtime',token=a);auditCount=sql('SELECT COUNT(*) FROM ai_runtime_audit',database);stop(new)
        restarted=start('restart');assert api('/admin/ai/runtime',token=a)==kept;assert sql('SELECT COUNT(*) FROM ai_runtime_audit',database)==auditCount;assert api('/admin/invitations',invitebody,a,status=201)['replayed'];assert sql("SELECT runtime_input_chars,runtime_output_tokens,runtime_total_output_tokens,runtime_timeout_seconds,runtime_version FROM ai_jobs WHERE id='"+job['id']+"'",database)==snap;stop(restarted)
        cols=columns(database);expectedRows=projections(database,cols,True)
        dump=cmd(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',database]);assert not re.search(rb'^\s*(USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)',dump,re.I|re.M)
        zipped=io.BytesIO()
        with gzip.GzipFile(fileobj=zipped,mode='wb',mtime=0) as archive:archive.write(dump)
        data=zipped.getvalue();(folder/'synthetic.sql.gz').write_bytes(data);report['backupSha256']=hashlib.sha256(data).hexdigest()
        sql(f'CREATE DATABASE {restored} CHARACTER SET utf8mb4;');sql(dump.decode(),restored);assert projections(restored,cols,True)==expectedRows
        report['restoredTables']=len(cols);report['cases'].append('Restart preserves settings, audit, operations and queued snapshots; full V33 dump restored with every table equal')
        assert before==health();assert sql('SELECT COUNT(*) FROM ai_job_attempts',database)=='0';report['status']='PASS'
    except Exception as failure:
        report['errorType']=type(failure).__name__;report['failedLine']=traceback.extract_tb(failure.__traceback__)[-1].lineno
        raise
    finally:
        for name in containers:
            try:
                if cmd(['docker','inspect','--format','{{.State.Running}}',name]).strip()==b'true':stop(name)
            except Exception:report['cleanupUnconfirmed']=True
        (folder/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps({'status':report['status'],'cases':len(report['cases']),'productionWrites':0,'privateEvidence':str(folder/'report.json')}))
if __name__=='__main__':main()
