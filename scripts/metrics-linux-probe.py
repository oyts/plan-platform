"""Synthetic bounded collector baseline on the approved ECS candidate only."""
import importlib.util,json,pathlib,resource,time,sys,hashlib
root=pathlib.Path.cwd().resolve();assert root.parent==pathlib.Path('/home/oyts/plan-platform/metrics-candidates')
assert sys.argv[1:] in [[],['--final']];final=sys.argv[1:]==['--final'];work=root/('collector-probe-final' if final else 'collector-probe-first');work.mkdir(mode=0o700)
spec=importlib.util.spec_from_file_location('collector',root/'runtime-telemetry.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
log=work/'plan-metrics.log';state=work/'runtime-cursor.json';now=int(time.time());line=f'{now}.000 api 200 0.010 0\n';log.write_text(line*21000)
cpu=time.process_time();start=time.monotonic();out,cursor=m.collect(log,state,now,probe_fn=lambda:True);elapsed=time.monotonic()-start;used=time.process_time()-cpu
assert not out['logComplete'] and sum(r['requests'] for r in out['minutes'])==20000;m.write(state,cursor);second,next_cursor=m.collect(log,state,now,probe_fn=lambda:True);assert sum(r['requests'] for r in second['minutes'])==21000 and second['logComplete'];m.write(state,next_cursor)
log.rename(work/'plan-metrics.log.1');log.write_text(line);rotated,_=m.collect(log,state,now,probe_fn=lambda:True);assert not rotated['logComplete'] and rotated['rotated']
size=log.stat().st_size+(work/'plan-metrics.log.1').stat().st_size;rss=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
assert elapsed<3 and used<2.5 and rss<65536 and len(json.dumps(second))<65536 and size<1048576
with (root/('linux-final-report.json' if final else 'linux-report.json')).open('x') as f:json.dump({'status':'PASS','collectorSha256':hashlib.sha256((root/'runtime-telemetry.py').read_bytes()).hexdigest(),'syntheticRows':21000,'firstRunRows':20000,'firstRunWallSeconds':round(elapsed,4),'firstRunCpuSeconds':round(used,4),'peakRssKiB':rss,'syntheticLogBytes':size,'aggregateBytes':len(json.dumps(second)),'scope':'Host Python collector on synthetic logs; not concurrent commercial load, no production raw logs or outbound provider/mail calls.'},f,indent=2)
