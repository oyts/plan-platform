"""Actual rotation on a private synthetic log; never signals a production container."""
import grp,json,os,pathlib,pwd,re,subprocess
root=pathlib.Path.cwd().resolve();assert re.fullmatch('/home/oyts/plan-platform/metrics-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(root));assert pwd.getpwuid(os.getuid()).pw_name=='oyts' and grp.getgrgid(os.getgid()).gr_name=='oyts';os.umask(0o077)
work=root/'logrotate-probe';work.mkdir(mode=0o700);log=work/'plan-metrics.log';payload=b'1760000000.000 api 200 0.010 0\n'*50;log.write_bytes(payload)
config=work/'logrotate.conf';config.write_text(str(log)+''' {
 size 4M
 rotate 2
 missingok
 notifempty
 create 0640 oyts oyts
 su oyts oyts
 sharedscripts
 postrotate
  /bin/true
 endscript
}
''')
p=subprocess.run(['/usr/sbin/logrotate','--force','--state',str(work/'state'),str(config)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=10)
if p.returncode:(work/'error.private').write_bytes(p.stderr);raise RuntimeError('Synthetic log rotation failed; private stderr retained')
assert log.read_bytes()==b'' and (work/'plan-metrics.log.1').read_bytes()==payload and log.stat().st_mode&0o777==0o640
with (root/'logrotate-report.json').open('x') as f:json.dump({'status':'PASS','owner':'oyts','group':'oyts','mode':'0640','rotatedBytes':len(payload),'retentionFiles':2,'productionFilesChanged':0,'scope':'Actual Linux force rotation on synthetic file only; postrotate replaced with /bin/true. Production container reopen not executed.'},f,indent=2)
print('Synthetic Linux logrotate PASS; production not changed.')
