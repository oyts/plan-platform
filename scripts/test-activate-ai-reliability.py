import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('reliability',Path(__file__).with_name('activate-ai-reliability-release.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);a=m.activation
class Guards(unittest.TestCase):
    def proof(self):return dict(status='PASS',migration='V30 -> V31',previousImage='plan-platform-api:0.1.20',targetImage='plan-platform-api:0.1.21',productionApplicationChanged=False,apiSha256=a.HASHES['backend/api.jar'],archiveSha256=a.HASHES['release.tar.gz'],originalProjectionTables=['ai_quota_requests','ai_sessions','ai_jobs'],originalTablesChecked=50,snapshotRestore={'tablesChecked':51})
    def test_exact_fresh_proof(self):a.validate_proof(self.proof(),100,101)
    def test_reject_changed_incomplete_or_stale_proof(self):
        for k,v in [('status','FAIL'),('migration','V28 -> V30'),('apiSha256','0'*64),('archiveSha256','0'*64),('originalTablesChecked',49),('snapshotRestore',{'tablesChecked':50}),('originalProjectionTables',[]),('productionApplicationChanged',True)]:
            with self.subTest(k=k),self.assertRaises(RuntimeError):a.validate_proof(dict(self.proof(),**{k:v}),100,101)
        for now in [99,3701]:
            with self.assertRaises(RuntimeError):a.validate_proof(self.proof(),100,now)
    def test_only_version_and_repair_change(self):
        body='PLAN_VERSION=0.1.20\nEMAIL_CHANGE_ENABLED=true\nPRIVATE=synthetic\n'
        for flag in ['','AI_REPAIR_ENABLED=false\n','AI_REPAIR_ENABLED=true\n']:
            changed=a.next_environment(body+flag);self.assertIn('PLAN_VERSION=0.1.21\n',changed);self.assertIn('PRIVATE=synthetic\n',changed);self.assertEqual(changed.count('AI_REPAIR_ENABLED=true'),1)
        for body in ['',body.replace('true','false'),body+'AI_REPAIR_ENABLED=true\nAI_REPAIR_ENABLED=false\n']:
            with self.assertRaises(RuntimeError):a.next_environment(body)
    def runtime(self,version):return {'Config':{'Image':'plan-platform-api:'+version,'Env':[f+'=true' for f in a.FLAGS]+['EMAIL_CHANGE_ENABLED=true','AI_REPAIR_ENABLED=true']},'Mounts':[{'Destination':p,'RW':False} for p in ['/run/secrets/deepseek-api-key','/run/plan-operations','/run/secrets/mail']]}
    def test_retained_runtime_and_repair_gate(self):
        for version in ['0.1.20','0.1.21']:a.approved_runtime(self.runtime(version))
        c=self.runtime('0.1.21');c['Config']['Env'][-1]='AI_REPAIR_ENABLED=false'
        with self.assertRaises(RuntimeError):a.approved_runtime(c)
        c=self.runtime('0.1.21');c['Mounts'][-1]['RW']=True
        with self.assertRaises(RuntimeError):a.approved_runtime(c)
if __name__=='__main__':unittest.main()
