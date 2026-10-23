#!/usr/bin/env python3
"""Owner-approved two-message SMTP acceptance in an isolated, loopback-only H2 container.
Private recipients/state are local files, never CLI arguments or output. No production DB access.
"""
import hashlib, json, os, pathlib, secrets, subprocess, sys, time, urllib.request, urllib.error
os.umask(0o077)
directory=pathlib.Path.cwd().resolve()
assert str(directory).startswith('/home/oyts/plan-platform/historical-mail/')
assert directory.name.replace('-','').isalnum()
name='plan-historical-mail-'+directory.name
port=43176
jar=pathlib.Path('/home/oyts/plan-platform/historical-candidates/20261022T131700Z-8a25d3ec/api.jar')
expected='452809438722476dde9a7a9c890967a4140b033aa940f39c915809e4e476e7e6'
def command(args):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=90)
    if result.returncode: raise RuntimeError('Isolated command failed; private output retained off chat')
    return result.stdout.decode().strip()
def api(path,data=None,token=None,expected=200):
    req=urllib.request.Request(f'http://127.0.0.1:{port}/api'+path,
        None if data is None else json.dumps(data).encode(),
        {'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})})
    try: result=urllib.request.urlopen(req,timeout=30)
    except urllib.error.HTTPError as error: result=error
    body=result.read();code=result.code;result.close()
    if code!=expected: raise RuntimeError('Isolated HTTP status '+str(code)+' (no response body printed)')
    return json.loads(body) if body else None
def save(path,value):
    pathlib.Path(path).write_text(json.dumps(value));os.chmod(path,0o600)
mode=sys.argv[1] if len(sys.argv)==2 else ''
assert mode in ['start','status','finish','stop']
if mode=='stop':
    command(['docker','stop','--time','15',name]);print('Isolated mail container stopped. Production unchanged.');sys.exit()
if mode=='start':
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==expected
    recipients=json.loads(pathlib.Path('recipients.json').read_text())
    assert set(recipients)=={'old','new'} and recipients['old']!=recipients['new']
    assert all(isinstance(x,str) and '@' in x and '\n' not in x and '\r' not in x for x in recipients.values())
    # Persist before any possibly chargeable SMTP submission; a failed run must not be blindly retried.
    with open('started.json','x') as stream: json.dump({'at':time.time(),'maximumMessages':2},stream)
    names=['SPRING_MAIL_HOST','SPRING_MAIL_PORT','SPRING_MAIL_USERNAME','MAIL_FROM']
    settings=command(['docker','exec','plan-platform-backend-1','printenv',*names]).splitlines()
    assert len(settings)==4 and settings[0]=='smtp.qq.com' and settings[1]=='465'
    env=dict(zip(names,settings))
    env.update({'SPRING_PROFILES_ACTIVE':'acceptance','SERVER_PORT':'8080',
        'SPRING_DATASOURCE_URL':'jdbc:h2:mem:real_mail;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1',
        'SPRING_DATASOURCE_DRIVER_CLASS_NAME':'org.h2.Driver','SPRING_DATASOURCE_USERNAME':'sa',
        'SPRING_DATASOURCE_PASSWORD':'','JWT_SECRET':secrets.token_hex(48),
        'SPRING_CONFIG_IMPORT':'configtree:/run/secrets/mail/',
        'SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH':'true','SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE':'true',
        'SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_CHECKSERVERIDENTITY':'true',
        'SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT':'5000',
        'SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT':'5000','SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT':'5000',
        'APP_PUBLIC_URL':'http://127.0.0.1:43177','AI_ENABLED':'false','OPERATIONS_ENABLED':'false',
        'OPERATIONS_MAIL_ALERTS_ENABLED':'false','EMAIL_CHANGE_ENABLED':'true',
        'EMAIL_VERIFICATION_ENABLED':'false','PASSWORD_RESET_ENABLED':'false'})
    pathlib.Path('candidate.env').write_text('\n'.join(k+'='+v for k,v in env.items())+'\n')
    os.chmod('candidate.env',0o600)
    command(['docker','run','-d','--name',name,'--memory','384m','--memory-swap','512m','--pids-limit','128',
        '--cpus','0.75','--read-only','--tmpfs','/tmp:rw,noexec,nosuid,size=32m',
        '--env-file',str(directory/'candidate.env'),'-p',f'127.0.0.1:{port}:8080',
        '-v',str(jar)+':/app/api.jar:ro','-v','/etc/plan-platform-mail:/run/secrets/mail:ro',
        '--entrypoint','java','plan-platform-api:0.1.19','-Xmx192m','-jar','/app/api.jar',
        '--app.security.registration-enabled=true','--app.security.bootstrap-admin-email=',
        '--app.security.refresh-cookie-secure=false','--app.operations.scheduler-enabled=false'])
    for _ in range(90):
        try:
            if api('/health')['ok']: break
        except Exception: time.sleep(1)
    else: raise RuntimeError('Isolated readiness failed; no mail submitted')
    password=secrets.token_urlsafe(30)
    account=api('/auth/register',{'email':recipients['old'],'password':password,'displayName':'Owner mailbox acceptance'},expected=201)
    state={'password':password,'token':account['accessToken'],'email':recipients['old'],'newEmail':recipients['new'],'startedAt':time.time()}
    save('private-state.json',state)
    with open('mail-submission-started.json','x') as stream: json.dump({'at':time.time(),'allowedMessages':2},stream)
    response=api('/auth/email-change/request',{'currentPassword':password,'newEmail':recipients['new']},state['token'])
    state['requestId']=response['requestId'];save('private-state.json',state)
    save('report.json',{'status':'WAITING_OWNER_RECEIPT','smtpAccepted':2,'receiptConfirmed':False,'productionChanged':False,'jarSha256':expected})
    print('Exactly two verification messages accepted by SMTP; inbox receipt is NOT yet confirmed. No production change.')
else:
    state=json.loads(pathlib.Path('private-state.json').read_text())
    assert time.time()-state['startedAt']<1800,'Test request expired; do not auto-resend'
    status=api('/auth/email-change/status',token=state['token'])
    print(json.dumps({k:status[k] for k in ['state','oldVerified','newVerified']}))
    if mode=='finish':
        assert status['oldVerified'] and status['newVerified'],'Owner must actively verify both mailbox links'
        with open('finish-started.json','x') as stream: json.dump({'at':time.time()},stream)
        api('/auth/email-change/complete',{'requestId':state['requestId'],'currentPassword':state['password'],'confirmed':True},state['token'],204)
        api('/auth/me',token=state['token'],expected=401)
        new=api('/auth/login',{'email':state['newEmail'],'password':state['password']})
        assert new['user']['email'].lower()==state['newEmail'].lower()
        save('report.json',{'status':'PASS','smtpAccepted':2,'bothOwnerProofsConfirmed':True,'oldSessionRejected':True,'newLoginPassed':True,'productionChanged':False,'jarSha256':expected})
        command(['docker','stop','--time','15',name]);print('Dual-mailbox owner proofs and final confirmation PASS; isolated container stopped.')
