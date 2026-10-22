"""Read only, fixed synthetic candidate diagnosis; no payloads, emails or secrets printed."""
import json, os, pathlib, re, subprocess
root=pathlib.Path('/home/oyts/plan-platform'); directory=pathlib.Path.cwd().resolve()
assert re.fullmatch(str(root)+r'/historical-candidates/[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}',str(directory))
database='historical_acceptance_'+directory.name[-8:];env=dict(os.environ)
for line in (root/'current/.env').read_text().splitlines():
    if line.startswith('MYSQL_ROOT_PASSWORD='):env['MYSQL_PWD']=line.split('=',1)[1].strip().strip("'\"")
assert env.get('MYSQL_PWD')
queries={
  'tables':f"select count(*) from information_schema.tables where table_schema='{database}'",
  'receipts':"select if(exists(select 1 from information_schema.tables where table_schema=database() and table_name='legacy_recycling_supplements'),1,0)",
  'batchStates':'select status,count(*) from legacy_imports group by status',
  'recyclingVersions':"select coalesce(json_extract(report_json,'$.recyclingVersion'),'0'),count(*) from legacy_imports group by 1",
  'deletedPlans':'select count(*) from plans where deleted_at is not null',
  'defaultClientCharset':'select @@character_set_client,@@character_set_results',
  'syntheticEditedNameHex':"select hex(p.name) from plans p join legacy_imports l on p.id=json_unquote(json_extract(l.report_json,'$.createdPlanIds[0]'))"
}
out={}
for name,sql in queries.items():
    result=subprocess.run(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','-N','--batch',database],input=sql.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=30)
    assert result.returncode==0,'Synthetic inspection query failed';out[name]=result.stdout.decode().strip()
if '--dump-check' in __import__('sys').argv:
    result=subprocess.run(['docker','exec','-e','MYSQL_PWD','plan-platform-mysql-1','mysqldump','--default-character-set=utf8mb4','-uroot','--single-transaction','--no-tablespaces','--skip-lock-tables',database],stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,timeout=60)
    out['dumpExitCode']=result.returncode;out['dumpBytes']=len(result.stdout)
    # Fixed synthetic database only; do not print dump contents or environment values.
    out['dumpErrorCodes']=re.findall(r'(?:ERROR|Error|error)\s*:?\s*\d{3,5}',result.stderr.decode(errors='replace'))
    out['dumpErrorSafe']=result.stderr.decode(errors='replace').replace(env['MYSQL_PWD'],'[redacted]')[:400]
print(json.dumps(out))
