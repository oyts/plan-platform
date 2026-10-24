import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('long_activation',Path(__file__).with_name('activate-ai-long-release.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);a=m.activation
class Guards(unittest.TestCase):
    def setUp(self):self.old=dict(a.HASHES);a.HASHES={'release.tar.gz':'a'*64,'backend/api.jar':'b'*64}
    def tearDown(self):a.HASHES=self.old
    def proof(self):return dict(status='PASS',migration='V31 -> V32',previousImage='plan-platform-api:0.1.21',targetImage='plan-platform-api:0.1.22',productionApplicationChanged=False,apiSha256=a.HASHES['backend/api.jar'],archiveSha256=a.HASHES['release.tar.gz'],originalProjectionTables=['ai_sessions'],originalTablesChecked=51,snapshotRestore={'tablesChecked':52})
    def test_exact_fresh_proof(self):a.validate_proof(self.proof(),100,101)
    def test_reject_missing_changed_incomplete_or_stale_proof(self):
        for k,v in [('status','FAIL'),('migration','V30 -> V31'),('apiSha256','0'*64),('archiveSha256','0'*64),('originalTablesChecked',50),('snapshotRestore',{'tablesChecked':51}),('originalProjectionTables',[]),('productionApplicationChanged',True)]:
            with self.subTest(k=k),self.assertRaises(RuntimeError):a.validate_proof(dict(self.proof(),**{k:v}),100,101)
        for now in [99,3701]:
            with self.assertRaises(RuntimeError):a.validate_proof(self.proof(),100,now)
    def test_placeholder_hashes_cannot_activate(self):
        a.HASHES={'release.tar.gz':'0'*64,'backend/api.jar':'0'*64}
        with self.assertRaises(RuntimeError):a.validate_proof(self.proof(),100,101)
    def test_only_version_changes_existing_flags_retained(self):
        body='PLAN_VERSION=0.1.21\nEMAIL_CHANGE_ENABLED=true\nAI_REPAIR_ENABLED=true\nPRIVATE=synthetic\n'
        self.assertEqual(a.next_environment(body),body.replace('PLAN_VERSION=0.1.21','PLAN_VERSION=0.1.22'))
        for source in ['',body.replace('EMAIL_CHANGE_ENABLED=true','EMAIL_CHANGE_ENABLED=false'),body+'AI_REPAIR_ENABLED=false\n']:
            with self.assertRaises(RuntimeError):a.next_environment(source)
    def test_runtime_mounts_and_existing_feature_flags(self):
        c={'Config':{'Env':[f+'=true' for f in a.FLAGS]+['EMAIL_CHANGE_ENABLED=true','AI_REPAIR_ENABLED=true']},'Mounts':[{'Destination':p,'RW':False} for p in ['/run/secrets/deepseek-api-key','/run/plan-operations','/run/secrets/mail']]}
        a.approved_runtime(c);c['Mounts'][-1]['RW']=True
        with self.assertRaises(RuntimeError):a.approved_runtime(c)
if __name__=='__main__':unittest.main()
