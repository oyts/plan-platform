#!/usr/bin/env python3
"""Refuse a server rollback that could revive credentials revoked by member governance."""
import argparse,json,os,re,subprocess
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--target-supports-generation',choices=['yes','no'],default='no');parser.add_argument('--database',default='plan_platform');args=parser.parse_args()
    if not re.fullmatch(r'plan_platform|plan_acceptance_[0-9a-f_]+',args.database):raise RuntimeError('Only the managed database can be checked.')
    inspect=json.loads(subprocess.run(['docker','inspect','plan-platform-mysql-1'],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout)[0]
    secret=next(value.split('=',1)[1] for value in inspect['Config']['Env'] if value.startswith('MYSQL_ROOT_PASSWORD='));environment={**os.environ,'MYSQL_PWD':secret}
    def query(statement):return subprocess.run(['docker','exec','-i','-e','MYSQL_PWD','plan-platform-mysql-1','mysql','-uroot','--batch','--skip-column-names'],input=statement.encode(),env=environment,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout.decode().strip()
    exists=query("select count(*) from information_schema.columns where table_schema='"+args.database+"' and table_name='users' and column_name='auth_generation'")
    if exists!='0' and query('select count(*) from '+args.database+'.users where auth_generation>0')!='0' and args.target_supports_generation!='yes':raise SystemExit('Rollback refused: governed accounts exist. Keep a generation-aware server; restoring an old binary could revive access tokens. Database and services unchanged.')
    print('Governance rollback check passed. No database or service was changed.')
if __name__=='__main__':main()
