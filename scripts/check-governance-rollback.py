#!/usr/bin/env python3
"""Refuse rollback that revives credentials or bypasses durable planning guards."""
import argparse,json,os,re,subprocess

def check_rollback(query,database,supports_generation=False,supports_long_planning=False,supports_admin_runtime=False):
    if not re.fullmatch(r'plan_platform|plan_acceptance_[0-9a-f_]+|long_[a-f0-9]{8}',database):raise RuntimeError('Only a managed database can be checked.')
    exists=query("select count(*) from information_schema.columns where table_schema='"+database+"' and table_name='users' and column_name='auth_generation'")
    if exists!='0' and query('select count(*) from '+database+'.users where auth_generation>0')!='0' and not supports_generation:
        raise RuntimeError('Rollback refused: governed accounts exist. Keep a generation-aware server; database and services unchanged.')
    outline=query("select count(*) from information_schema.columns where table_schema='"+database+"' and table_name='ai_sessions' and column_name='outline_only'")
    if outline!='0' and not supports_long_planning:
        count=query('select (select count(*) from '+database+'.ai_long_plans)+(select count(*) from '+database+'.ai_sessions where outline_only=TRUE)')
        if count!='0':raise RuntimeError('Rollback refused: durable long planning exists. Keep a batch-aware backend; an older writable server would bypass completion guards. Database and services unchanged.')
    runtime=query("select count(*) from information_schema.columns where table_schema='"+database+"' and table_name='ai_jobs' and column_name='runtime_version'")
    if runtime!='0' and not supports_admin_runtime:
        count=query('select (select count(*) from '+database+'.ai_runtime_policy where settings_json IS NOT NULL)+(select count(*) from '+database+'.admin_invitation_operations)+(select count(*) from '+database+'.ai_jobs where runtime_version IS NOT NULL)')
        if count!='0':raise RuntimeError('Rollback refused: admin runtime or invitation protocol exists. Keep a runtime-aware backend; an older writable server would ignore job snapshots or invite replay guards. Database and services unchanged.')
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--target-supports-generation',choices=['yes','no'],default='no');parser.add_argument('--target-supports-long-planning',choices=['yes','no'],default='no');parser.add_argument('--target-supports-admin-runtime',choices=['yes','no'],default='no');parser.add_argument('--database',default='plan_platform');args=parser.parse_args()
    if not re.fullmatch(r'plan_platform|plan_acceptance_[0-9a-f_]+|long_[a-f0-9]{8}',args.database):raise RuntimeError('Only the managed database can be checked.')
    inspect=json.loads(subprocess.run(['docker','inspect','plan-platform-mysql-1'],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout)[0]
    secret=next(value.split('=',1)[1] for value in inspect['Config']['Env'] if value.startswith('MYSQL_ROOT_PASSWORD='));environment={**os.environ,'MYSQL_PWD':secret}
    def query(statement):return subprocess.run(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','--batch','--skip-column-names'],input=statement.encode(),env=environment,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout.decode().strip()
    check_rollback(query,args.database,args.target_supports_generation=='yes',args.target_supports_long_planning=='yes',args.target_supports_admin_runtime=='yes')
    print('Credential, planning and admin-runtime rollback checks passed. No database or service was changed.')
if __name__=='__main__':main()
