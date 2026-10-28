import importlib.util,time,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('updates_activation',Path(__file__).with_name('activate-update-experience-release.py'))
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)

class UpdateReleaseGuards(unittest.TestCase):
    def proof(self):
        return dict(status='PASS',migration='V34 -> V34',previousImage='plan-platform-api:0.1.24',targetImage='plan-platform-api:0.1.25',productionApplicationChanged=False,apiSha256=a.activation.HASHES['backend/api.jar'],archiveSha256=a.activation.HASHES['release.tar.gz'],originalProjectionTables=['users','task_templates','mainlines'],originalTablesChecked=61,snapshotRestore=dict(tablesChecked=62))
    def runtime(self):
        flags=list(a.activation.FLAGS)+['EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED','METRICS_ENABLED','TELEMETRY_ENABLED']
        return {'Config':{'Env':[f+'=true' for f in flags]+['TELEMETRY_MAIL_ALERTS_ENABLED=false']},'Mounts':[dict(Destination=p,RW=False) for p in ['/run/secrets/deepseek-api-key','/run/plan-operations','/run/secrets/mail']]}
    def test_exact_immutable_versions_and_all_previous_capabilities(self):
        self.assertEqual((a.activation.NEW_VERSION,a.activation.OLD_VERSION,a.activation.MIGRATION),('0.1.25','0.1.24','V34 -> V34'))
        self.assertEqual(a.activation.ROLLBACK_CAPABILITIES[1::2],['yes']*4)
        self.assertIn('production-telemetry.yml',a.activation.OVERLAYS)
    def test_matching_fresh_proof_required(self):
        now=time.time();a.validate_proof(self.proof(),now,now)
        for key,value in [('migration','V33 -> V34'),('targetImage','plan-platform-api:0.1.24'),('apiSha256','0'*64),('archiveSha256','0'*64),('originalTablesChecked',60),('originalProjectionTables',['users']),('productionApplicationChanged',True)]:
            p=self.proof();p[key]=value
            with self.subTest(key=key),self.assertRaises(RuntimeError):a.validate_proof(p,now,now)
    def test_restore_or_clock_failure_stops(self):
        now=time.time();p=self.proof();p['snapshotRestore']['tablesChecked']=61
        with self.assertRaises(RuntimeError):a.validate_proof(p,now,now)
        for modified in [now-3601,now+1]:
            with self.assertRaises(RuntimeError):a.validate_proof(self.proof(),modified,now)
    def test_environment_only_advances_version(self):
        body='PLAN_VERSION=0.1.24\nEMAIL_CHANGE_ENABLED=true\nAI_REPAIR_ENABLED=true\nPRIVATE_SYNTHETIC=not-printed\n'
        self.assertEqual(a.next_environment(body),body.replace('0.1.24','0.1.25'))
        for flag in ['EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED']:
            with self.assertRaises(RuntimeError):a.next_environment(body.replace(flag+'=true',flag+'=false'))
    def test_runtime_requires_retained_features_and_mail_off(self):
        a.approved_runtime(self.runtime())
        for flag in ['METRICS_ENABLED','TELEMETRY_ENABLED','EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED']:
            c=self.runtime();c['Config']['Env']=[v.replace(flag+'=true',flag+'=false') for v in c['Config']['Env']]
            with self.assertRaises(RuntimeError):a.approved_runtime(c)
        c=self.runtime();c['Config']['Env']=[v.replace('TELEMETRY_MAIL_ALERTS_ENABLED=false','TELEMETRY_MAIL_ALERTS_ENABLED=true') for v in c['Config']['Env']]
        with self.assertRaises(RuntimeError):a.approved_runtime(c)
    def test_writable_or_missing_secret_mount_refused(self):
        for p in ['/run/secrets/deepseek-api-key','/run/plan-operations','/run/secrets/mail']:
            c=self.runtime();c['Mounts']=[m for m in c['Mounts'] if m['Destination']!=p]
            with self.assertRaises(RuntimeError):a.approved_runtime(c)
            c=self.runtime();next(m for m in c['Mounts'] if m['Destination']==p)['RW']=True
            with self.assertRaises(RuntimeError):a.approved_runtime(c)
if __name__=='__main__':unittest.main()
