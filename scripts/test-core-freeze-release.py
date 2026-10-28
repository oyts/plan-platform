import importlib.util,time,unittest
from pathlib import Path
def load(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
a=load('core_release','activate-core-freeze-release.py')
class CoreGuards(unittest.TestCase):
    def proof(self):return dict(status='PASS',migration='V34 -> V34',previousImage='plan-platform-api:0.1.25',targetImage='plan-platform-api:0.1.26',productionApplicationChanged=False,apiSha256=a.activation.HASHES['backend/api.jar'],archiveSha256=a.activation.HASHES['release.tar.gz'],originalProjectionTables=['users','task_templates','mainlines'],originalTablesChecked=61,snapshotRestore=dict(tablesChecked=62))
    def test_exact_versions_and_existing_capabilities(self):
        self.assertEqual((a.activation.NEW_VERSION,a.activation.OLD_VERSION),('0.1.26','0.1.25'));self.assertEqual(a.activation.ROLLBACK_CAPABILITIES[1::2],['yes']*4);self.assertIn('production-telemetry.yml',a.activation.OVERLAYS)
    def test_fresh_immutable_unchanged_schema_proof(self):
        now=time.time();a.validate_proof(self.proof(),now,now)
        for k,v in [('targetImage','plan-platform-api:0.1.25'),('archiveSha256','0'*64),('apiSha256','0'*64),('originalTablesChecked',60),('productionApplicationChanged',True),('migration','V33 -> V34')]:
            p=self.proof();p[k]=v
            with self.subTest(k=k),self.assertRaises(RuntimeError):a.validate_proof(p,now,now)
        for t in [now-3601,now+1]:
            with self.assertRaises(RuntimeError):a.validate_proof(self.proof(),t,now)
    def test_environment_preserves_features_and_secrets(self):
        body='PLAN_VERSION=0.1.25\nEMAIL_CHANGE_ENABLED=true\nAI_REPAIR_ENABLED=true\nPRIVATE_SYNTHETIC=not-printed\n';self.assertEqual(a.next_environment(body),body.replace('0.1.25','0.1.26'))
        with self.assertRaises(RuntimeError):a.next_environment(body.replace('AI_REPAIR_ENABLED=true','AI_REPAIR_ENABLED=false'))
    def test_post_verification_uses_exact_release(self):
        m=load('core_post','verify-core-freeze-production.py');self.assertEqual((m.base.EXPECTED_VERSION,m.base.EXPECTED_RELEASE),('0.1.26','0.1.26-012dfda4'))
if __name__=='__main__':unittest.main()
