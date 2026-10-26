"""Bounded incremental sanitized Nginx metrics + Linux resources. No raw URL/IP/UA.

Only the dedicated five-column plan-metrics.log is accepted. Existing raw access
logs are never opened. Cursor/state are private and output is a read-only mount.
"""
import argparse,datetime as dt,json,os,re,time,urllib.request,tempfile
from pathlib import Path
ROUTES={'public','api','auth','scan','other'}
FIELDS={'minute','requests','errors','authFailures','limited','scans','bots','durationMs','slow','htmlRequests','apiRequests','apiErrors','apiDurationMs'}
LINE=re.compile(rb'([0-9]{10}\.[0-9]{3}) (public|api|auth|scan|other) ([1-5][0-9]{2}) ([0-9]{1,5}\.[0-9]{3}) ([01])\r?\n')
def write(path,value):
    path=Path(path)
    if path.name not in {'runtime.json','runtime-cursor.json'} or path.is_symlink() or not path.parent.is_dir():raise ValueError('Expected managed metric output')
    fd,temporary=tempfile.mkstemp(prefix=path.name+'.',suffix='.tmp',dir=path.parent)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as stream:json.dump(value,stream,separators=(',',':'));stream.flush();os.fsync(stream.fileno())
        os.chmod(temporary,0o640);os.replace(temporary,path)
    finally:
        if os.path.exists(temporary):os.unlink(temporary)
def read_state(path):
    path=Path(path)
    if path.is_symlink() or path.stat().st_size>131072:raise ValueError('Invalid cursor')
    value=json.loads(path.read_text());assert set(value)<= {'inode','offset','cpu','minutes','samples','gapUntil'}
    assert isinstance(value.get('offset',0),int) and value.get('offset',0)>=0
    return value
def resources(previous):
    cpu=list(map(int,Path('/proc/stat').read_text().splitlines()[0].split()[1:9]));total=sum(cpu);idle=cpu[3]+cpu[4]
    used=None
    if previous and total>previous[0] and idle>=previous[1]:used=round(100*(1-(idle-previous[1])/(total-previous[0])),2)
    mem={k:int(v.split()[0])*1024 for k,v in (line.split(':',1) for line in Path('/proc/meminfo').read_text().splitlines())}
    return {'cpuPercent':used,'memoryUsedBytes':mem['MemTotal']-mem['MemAvailable'],'memoryTotalBytes':mem['MemTotal']},[total,idle]
def probe():
    try:
        with urllib.request.urlopen('http://127.0.0.1:43140/actuator/health/readiness',timeout=3) as r:return json.loads(r.read(1024)).get('status')=='UP'
    except (OSError,ValueError):return False
def collect(log,state,now=None,resource_fn=resources,probe_fn=probe):
    now=time.time() if now is None else now;stamp=dt.datetime.fromtimestamp(now,dt.timezone.utc).isoformat()
    if Path(log).name!='plan-metrics.log' or Path(log).is_symlink():raise ValueError('Only dedicated sanitized log accepted')
    prior=read_state(state) if Path(state).exists() else {};minutes={}
    for row in prior.get('minutes',[]):
        if set(row)!=FIELDS or not all(isinstance(v,(int,float)) and v>=0 for v in row.values()):raise ValueError('Invalid retained aggregate')
        if now//60-60<=row['minute']<=now//60:minutes[row['minute']]=row
    stat=Path(log).stat();offset=prior.get('offset',0)
    rotated=bool(prior) and (prior.get('inode')!=stat.st_ino or offset>stat.st_size)
    gap_until=prior.get('gapUntil',0)
    if not isinstance(gap_until,(int,float)) or gap_until<0:raise ValueError('Invalid gap marker')
    # Do not open rotated/raw files. An unread renamed tail is unknown for one hour.
    if rotated:gap_until=now+3600
    if rotated:offset=0
    # Fixed per-run byte/line/time limits. Incomplete lines are reread next run.
    started=time.monotonic();invalid=0;count=0
    fd=os.open(log,os.O_RDONLY|getattr(os,'O_NOFOLLOW',0))
    with os.fdopen(fd,'rb') as stream:
        if os.fstat(stream.fileno()).st_ino!=stat.st_ino:raise ValueError('Log rotated during open')
        stream.seek(offset);data=stream.read(1024*1024)
        for line in data.splitlines(keepends=True):
            if not line.endswith(b'\n') or count>=20000 or time.monotonic()-started>2:break
            offset+=len(line);count+=1;match=LINE.fullmatch(line)
            if not match:invalid+=1;continue
            timestamp,route,status,duration,bot=match.groups();minute=int(float(timestamp))//60
            if not now//60-60<=minute<=now//60:continue
            row=minutes.setdefault(minute,dict.fromkeys(FIELDS,0));row['minute']=minute
            code=int(status);elapsed=round(float(duration)*1000);row['requests']+=1;row['errors']+=int(code>=500);row['authFailures']+=int(route==b'auth' and code in [401,403]);row['limited']+=int(code==429);row['scans']+=int(route==b'scan');row['bots']+=int(bot==b'1');row['durationMs']+=elapsed;row['slow']+=int(float(duration)>=1);row['htmlRequests']+=int(route==b'public');row['apiRequests']+=int(route in [b'api',b'auth']);row['apiErrors']+=int(route in [b'api',b'auth'] and code>=500);row['apiDurationMs']+=elapsed if route in [b'api',b'auth'] else 0
    if invalid:gap_until=now+3600
    complete=offset>=stat.st_size and invalid==0 and now>=gap_until
    resource,cpu=resource_fn(prior.get('cpu'))
    sample={'minute':int(now)//60,**resource,'healthy':probe_fn()}
    samples=[]
    for s in prior.get('samples',[]):
        if set(s)!={'minute','cpuPercent','memoryUsedBytes','memoryTotalBytes','healthy'}:raise ValueError('Invalid retained resource')
        if not isinstance(s['minute'],int) or not isinstance(s['healthy'],bool):raise ValueError('Invalid resource types')
        if s['cpuPercent'] is not None and (not isinstance(s['cpuPercent'],(int,float)) or not 0<=s['cpuPercent']<=100):raise ValueError('Invalid CPU')
        if not all(isinstance(s[k],int) for k in ['memoryUsedBytes','memoryTotalBytes']) or not 0<=s['memoryUsedBytes']<=s['memoryTotalBytes'] or s['memoryTotalBytes']<=0:raise ValueError('Invalid memory')
        if now//60-60<=s['minute']<now//60:samples.append(s)
    samples.append(sample)
    # Zero is legitimate only for a completed observed minute, never old history.
    current=int(now)//60
    if complete:minutes.setdefault(current,dict(dict.fromkeys(FIELDS,0),minute=current))
    output={'schemaVersion':1,'observedAt':stamp,'logComplete':complete,'rotated':rotated,'invalidLines':invalid,'resource':resource,'healthy':sample['healthy'],'minutes':sorted(minutes.values(),key=lambda r:r['minute'])[-61:],'samples':samples[-61:]}
    cursor={'inode':stat.st_ino,'offset':offset,'cpu':cpu,'minutes':output['minutes'],'samples':output['samples'],'gapUntil':gap_until}
    if len(json.dumps(output))>65536:raise ValueError('Aggregate byte limit')
    return output,cursor
def main():
    p=argparse.ArgumentParser();p.add_argument('--log',required=True);p.add_argument('--state',required=True);p.add_argument('--output',required=True);a=p.parse_args();os.umask(0o027)
    try:output,cursor=collect(a.log,a.state);write(a.state,cursor);write(a.output,output);print('Bounded sanitized runtime metadata saved; no mail or raw logs exposed.')
    except (OSError,ValueError,AssertionError):raise SystemExit('Runtime collection failed; old metadata retained and will become stale. No secrets printed.')
if __name__=='__main__':main()
