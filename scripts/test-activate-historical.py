import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('historical',Path(__file__).with_name('activate-historical-release.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);a=m.activation
class Guards(unittest.TestCase):
    def proof(self):
        return dict(status='PASS',migration='V28 -> V30',previousImage='plan-platform-api:0.1.19',targetImage='plan-platform-api:0.1.20',productionApplicationChanged=False,apiSha256=a.HASHES['backend/api.jar'],archiveSha256=a.HASHES['release.tar.gz'],originalProjectionTables=[],originalTablesChecked=48,snapshotRestore={'tablesChecked':49})
    def test_exact_proof(self):a.validate_proof(self.proof(),100,101)
    def test_stale_wrong_or_incomplete_proof(self):
        for k,v in [('status','FAIL'),('migration','V27 -> V28'),('apiSha256','0'*64),('archiveSha256','0'*64),('originalTablesChecked',47),('snapshotRestore',{'tablesChecked':48}),('originalProjectionTables',['users']),('productionApplicationChanged',True)]:
            with self.subTest(k=k),self.assertRaises(RuntimeError):a.validate_proof(dict(self.proof(),**{k:v}),100,101)
        for now in [99,3701]:
            with self.assertRaises(RuntimeError):a.validate_proof(self.proof(),100,now)
    def test_only_version_and_approved_flag_change(self):
        for suffix in ['','EMAIL_CHANGE_ENABLED=false\n','EMAIL_CHANGE_ENABLED=true\n']:
            body='PLAN_VERSION=0.1.19\nPRIVATE=synthetic\n'+suffix
            updated=a.next_environment(body)
            self.assertIn('PLAN_VERSION=0.1.20\n',updated);self.assertIn('PRIVATE=synthetic\n',updated)
            self.assertEqual(updated.count('EMAIL_CHANGE_ENABLED=true'),1)
        for body in ['','PLAN_VERSION=old\n','PLAN_VERSION=0.1.19\nEMAIL_CHANGE_ENABLED=false\nEMAIL_CHANGE_ENABLED=true\n']:
            with self.assertRaises(RuntimeError):a.next_environment(body)
    def test_retained_flags_and_mounts(self):
        c={'Config':{'Image':'plan-platform-api:0.1.20','Env':[f+'=true' for f in a.FLAGS]+['EMAIL_CHANGE_ENABLED=true']},'Mounts':[{'Destination':p,'RW':False} for p in ['/run/secrets/deepseek-api-key','/run/plan-operations','/run/secrets/mail']]}
        a.approved_runtime(c)
        c['Mounts'][-1]['RW']=True
        with self.assertRaises(RuntimeError):a.approved_runtime(c)
        c['Mounts'][-1]['RW']=False;c['Config']['Env'][-1]='EMAIL_CHANGE_ENABLED=false'
        with self.assertRaises(RuntimeError):a.approved_runtime(c)
if __name__=='__main__':unittest.main()
