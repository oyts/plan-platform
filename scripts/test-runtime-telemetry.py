import importlib.util,json,tempfile,time,unittest
from unittest.mock import patch
from types import SimpleNamespace
from pathlib import Path
spec=importlib.util.spec_from_file_location('runtime_collector',Path(__file__).with_name('runtime-telemetry.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Collector(unittest.TestCase):
 def test_fixed_local_readiness_does_not_open_public_actuator(self):
  with patch.object(m.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout=b'{"status":"UP"}')) as command:
   self.assertTrue(m.probe());self.assertEqual(command.call_args.kwargs['timeout'],3);self.assertEqual(command.call_args.args[0],['docker','exec','plan-platform-backend-1','wget','-qO-','http://127.0.0.1:8080/actuator/health/readiness'])
  for response in [SimpleNamespace(returncode=1,stdout=b''),SimpleNamespace(returncode=0,stdout=b'{"status":"DOWN"}'),SimpleNamespace(returncode=0,stdout=b'x'*1025)]:
   with patch.object(m.subprocess,'run',return_value=response):self.assertFalse(m.probe())
  with patch.object(m.subprocess,'run',side_effect=m.subprocess.TimeoutExpired('docker',3)):self.assertFalse(m.probe())
 def resources(self,_):return dict(cpuPercent=25,memoryUsedBytes=50,memoryTotalBytes=100),[100,50]
 def collect(self,path,state,now):return m.collect(path,state,now,self.resources,lambda:True)
 def test_incremental_counts_no_raw_payload_and_partial_line(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);log=root/'plan-metrics.log';state=root/'runtime-cursor.json';now=int(time.time());line=f'{now}.000 api 500 1.250 0\n';log.write_text(line+f'{now}.000 auth 401 0.100 0');out,cursor=self.collect(log,state,now);self.assertEqual(out['minutes'][0]['apiErrors'],1);self.assertFalse(out['logComplete']);m.write(state,cursor);log.write_text(log.read_text()+'\n');out,cursor=self.collect(log,state,now);self.assertEqual(sum(r['requests'] for r in out['minutes']),2);self.assertEqual(sum(r['authFailures'] for r in out['minutes']),1);self.assertNotIn('api 500',json.dumps(out));m.write(state,cursor);out,_=self.collect(log,state,now);self.assertEqual(sum(r['requests'] for r in out['minutes']),2)
 def test_only_sanitized_log_and_invalid_input_are_not_echoed(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);log=root/'plan-metrics.log';log.write_text('private-url?token=MUST_NOT_LEAK\n');out,_=self.collect(log,root/'runtime-cursor.json',time.time());self.assertFalse(out['logComplete']);self.assertNotIn('MUST_NOT_LEAK',json.dumps(out));
   with self.assertRaises(ValueError):self.collect(root/'access.log',root/'runtime-cursor.json',time.time())
 def test_rotation_and_truncation_preserve_prior_aggregates(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);log=root/'plan-metrics.log';state=root/'runtime-cursor.json';now=int(time.time());log.write_text(f'{now}.000 public 200 0.010 0\n');out,cursor=self.collect(log,state,now);m.write(state,cursor);log.rename(root/'plan-metrics.log.1');log.write_text(f'{now}.000 scan 404 0.020 1\n');out,_=self.collect(log,state,now);self.assertTrue(out['rotated']);self.assertEqual(sum(r['requests'] for r in out['minutes']),2);self.assertEqual(sum(r['htmlRequests'] for r in out['minutes']),1)
 def test_size_cap_leaves_unread_backlog_explicit(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);log=root/'plan-metrics.log';now=int(time.time());log.write_text(f'{now}.000 api 200 0.010 0\n'*21000);out,cursor=self.collect(log,root/'runtime-cursor.json',now);self.assertFalse(out['logComplete']);self.assertLess(cursor['offset'],log.stat().st_size);self.assertLessEqual(sum(r['requests'] for r in out['minutes']),20000)
 def test_rotation_and_bad_line_keep_retained_window_unknown(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);log=root/'plan-metrics.log';state=root/'runtime-cursor.json';now=int(time.time());log.write_text('invalid\n');out,cursor=self.collect(log,state,now);m.write(state,cursor);out,cursor=self.collect(log,state,now+60);self.assertFalse(out['logComplete']);self.assertGreater(cursor['gapUntil'],now+60)
 def test_cursor_cannot_inject_private_fields(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);log=root/'plan-metrics.log';state=root/'runtime-cursor.json';now=int(time.time());log.write_text('');out,cursor=self.collect(log,state,now);cursor['samples'][0]['privateUrl']='not-allowed';m.write(state,cursor)
   with self.assertRaises(ValueError):self.collect(log,state,now)
 def test_interrupted_old_next_does_not_block_atomic_collection(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);out=root/'runtime.json';legacy=root/'runtime.json.next';legacy.write_text('interrupted');m.write(out,{'schemaVersion':1});self.assertEqual(json.loads(out.read_text()),{'schemaVersion':1});self.assertEqual(legacy.read_text(),'interrupted');self.assertEqual(list(root.glob('*.tmp')),[])
if __name__=='__main__':unittest.main()
