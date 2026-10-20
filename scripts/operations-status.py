#!/usr/bin/env python3
"""Bounded backup metadata collector. No deletion, database access, mail or credentials."""
import argparse,datetime as dt,gzip,hashlib,json,os,re,shutil,subprocess,tempfile,zlib
from pathlib import Path

NAME=re.compile(r'(?:plan_platform|plan_acceptance_[0-9a-f_]+)-mysql-(\d{8}-\d{6})\.sql\.gz')
FAILURES={'BACKUP_FAILED','OSS_FAILED','RESTORE_FAILED'}
def now():return dt.datetime.now(dt.timezone.utc).isoformat()
def output_path(value,name):
    path=Path(value)
    if not path.is_absolute() or path.name!=name or path.parent.name!='operations' or path.is_symlink():raise ValueError('Use the exact operations metadata output path, not a backup or configuration file.')
    path.parent.mkdir(parents=True,exist_ok=True);return path
def write(path,value,group_readable=False):
    descriptor,temporary=tempfile.mkstemp(prefix='metadata-',suffix='.tmp',dir=path.parent)
    try:
        with os.fdopen(descriptor,'w',encoding='utf-8') as stream:json.dump(value,stream,ensure_ascii=False);stream.flush();os.fsync(stream.fileno())
        os.chmod(temporary,0o640 if group_readable else 0o600);os.replace(temporary,path)
    finally:
        if os.path.exists(temporary):os.unlink(temporary)
def evidence(value,name):
    if not value:return {}
    path=Path(value)
    if path.name!=name or path.parent.name!='operations' or path.is_symlink() or not path.is_file() or path.stat().st_size>4096:return {}
    try:return json.loads(path.read_text(encoding='utf-8'))
    except (ValueError,OSError):return {}
def verify_local(path,budget):
    if path.is_symlink() or not path.is_file() or path.stat().st_size>budget[0]:return ''
    sidecar=Path(str(path)+'.sha256')
    if sidecar.is_symlink() or not sidecar.is_file() or sidecar.stat().st_size>4096:return ''
    try:
        parts=sidecar.read_text(encoding='utf-8').split()
        if len(parts)!=2 or not re.fullmatch('[a-fA-F0-9]{64}',parts[0]) or parts[1].lstrip('*')!=path.name:return ''
        digest=hashlib.sha256()
        with path.open('rb') as stream:
            for block in iter(lambda:stream.read(65536),b''):
                budget[0]-=len(block)
                if budget[0]<0:return ''
                digest.update(block)
        if digest.hexdigest()!=parts[0].lower():return ''
        with gzip.open(path,'rb') as stream:
            for block in iter(lambda:stream.read(65536),b''):
                budget[1]-=len(block)
                if budget[1]<0:return ''
        return digest.hexdigest()
    except (OSError,ValueError,EOFError,zlib.error):return ''
def collect(args):
    target=output_path(args.output,'status.json');directory=Path(args.backup_directory)
    if not directory.is_absolute() or directory.is_symlink() or not directory.is_dir() or directory.resolve()==target.parent.resolve():raise ValueError('Specify an existing, separate backup directory.')
    files=sorted((p for p in directory.iterdir() if NAME.fullmatch(p.name) and p.is_file() and not p.is_symlink()),key=lambda p:NAME.fullmatch(p.name)[1],reverse=True)
    budget=[64*1024*1024,256*1024*1024];backups=[]
    for path in files[:200]:
        try:completed=dt.datetime.strptime(NAME.fullmatch(path.name)[1],'%Y%m%d-%H%M%S').replace(tzinfo=dt.timezone.utc)
        except ValueError:continue
        digest=verify_local(path,budget);backups.append({'name':path.name,'completedAt':completed.isoformat(),'bytes':path.stat().st_size,'sha256':digest,'localVerified':bool(digest)})
    proof=evidence(args.drill_evidence,'drill.json');drill={k:proof[k] for k in ['sha256','completedAt','matched'] if k in proof}
    prior=evidence(str(target.parent/'oss.json'),'oss.json');oss={k:prior[k] for k in ['sha256','verifiedAt','verified'] if k in prior}
    failure=evidence(args.failure_evidence,'failures.json');failed=[code for code in FAILURES if failure.get(code) is True]
    newest=next((row for row in backups if row['localVerified']),None)
    if args.verify_oss and newest:
        # Only an explicit CLI option performs network reads using the existing ECS role.
        try:
            with tempfile.TemporaryDirectory(prefix='plan-oss-proof-') as temporary:
                destination=Path(temporary)/newest['name'];remote='oss://zj-ecs-server/plan-console-backups/mysql/'+newest['name']
                for suffix in ['', '.sha256']:
                    executable=shutil.which('ossutil',path='/usr/local/bin:/usr/bin:/bin') or 'ossutil'
                    subprocess.run([executable,'cp',remote+suffix,str(destination)+suffix,'--mode','EcsRamRole','--region','cn-hangzhou','--endpoint','https://oss-cn-hangzhou-internal.aliyuncs.com'],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=300)
                digest=verify_local(destination,[64*1024*1024,256*1024*1024])
                if digest!=newest['sha256']:raise ValueError('OSS downloaded hash mismatch.')
            oss={'sha256':digest,'verifiedAt':now(),'verified':True};write(output_path(str(target.parent/'oss.json'),'oss.json'),oss,args.group_readable)
            failed=[code for code in failed if code!='OSS_FAILED']
        except (OSError,ValueError,subprocess.SubprocessError):oss={};failed=list(set(failed+['OSS_FAILED']))
        # Ordinary five-minute collections must not resurrect an old failure
        # after a real roundtrip succeeded, nor erase a genuine failed attempt.
        failure_state={code:code in failed for code in FAILURES}
        write(output_path(str(target.parent/'failures.json'),'failures.json'),failure_state,args.group_readable)
    disk=shutil.disk_usage(directory)
    result={'schemaVersion':1,'observedAt':now(),'catalogComplete':len(files)<=200,'backups':backups,'oss':oss,'drill':drill,'disk':{'freeBytes':disk.free,'totalBytes':disk.total},'failures':sorted(failed)}
    if len(json.dumps(result).encode())>65536:raise ValueError('Metadata cap exceeded; old status retained.')
    write(target,result,args.group_readable);return result
def main():
    parser=argparse.ArgumentParser();commands=parser.add_subparsers(dest='action');commands.required=True
    collect_command=commands.add_parser('collect');collect_command.add_argument('--backup-directory',required=True);collect_command.add_argument('--output',required=True);collect_command.add_argument('--drill-evidence');collect_command.add_argument('--failure-evidence');collect_command.add_argument('--verify-oss',action='store_true');collect_command.add_argument('--group-readable',action='store_true')
    drill=commands.add_parser('record-drill');drill.add_argument('--backup',required=True);drill.add_argument('--output',required=True);drill.add_argument('--group-readable',action='store_true')
    failure=commands.add_parser('record-result');failure.add_argument('--code',choices=sorted(FAILURES),required=True);failure.add_argument('--failed',choices=['yes','no'],required=True);failure.add_argument('--output',required=True);failure.add_argument('--group-readable',action='store_true')
    args=parser.parse_args()
    if args.action=='collect':collect(args);print('Bounded operational metadata saved. No backup deleted, database opened or email sent.')
    elif args.action=='record-drill':
        target=output_path(args.output,'drill.json');source=Path(args.backup)
        if not source.is_absolute() or not NAME.fullmatch(source.name):raise ValueError('Use a verified MySQL backup path.')
        digest=verify_local(source,[64*1024*1024,256*1024*1024])
        if not digest:raise ValueError('Local backup could not be verified; old drill evidence retained.')
        write(target,{'sha256':digest,'completedAt':now(),'matched':True},args.group_readable);print('Drill evidence saved; call only after an actual successful isolated restore comparison.')
    else:
        target=output_path(args.output,'failures.json');old=evidence(str(target),'failures.json');value={code:bool(old.get(code,False)) for code in FAILURES};value[args.code]=args.failed=='yes';write(target,value,args.group_readable);print('Operation result metadata saved. No email sent.')
if __name__=='__main__':main()
