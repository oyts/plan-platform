import copy,datetime as dt,importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('update_production_verification',Path(__file__).with_name('verify-update-experience-production.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ProductionVerificationGuards(unittest.TestCase):
    def setUp(self):
        self.now=dt.datetime(2026,10,28,0,50,tzinfo=dt.timezone.utc)
        self.value=dict(schemaVersion=1,observedAt='2026-10-28T00:49:50+00:00',healthy=True,logComplete=True,samples=[{}, {}, {}])
    def test_fresh_actual_metadata(self):
        self.assertEqual(m.runtime_facts(self.value,self.now)['ageSeconds'],10)
    def test_missing_stale_or_incomplete_runtime_refused(self):
        for key,value in [('observedAt',None),('observedAt','2026-10-28T00:47:00+00:00'),('observedAt','2026-10-28T00:50:01+00:00'),('healthy',False),('logComplete',False),('schemaVersion',2),('samples',[])]:
            v=copy.deepcopy(self.value);v[key]=value
            with self.subTest(key=key,value=value),self.assertRaises(RuntimeError):m.runtime_facts(v,self.now)
    def test_retained_managed_backup_location(self):
        p='/home/oyts/plan-platform/releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261028-005000.sql.gz'
        self.assertEqual(str(m.backup_location(p)).replace('\\','/'),p)
    def test_foreign_or_ambiguous_backup_locations_refused(self):
        for p in ['/tmp/plan_platform-mysql-20261028-005000.sql.gz','/home/oyts/plan-platform/releases/0.1.25-deadbeef/../backups/plan_platform-mysql-20261028-005000.sql.gz','/home/oyts/plan-platform/releases/0.1.25-deadbeef/backups/private.json','/home/other/plan-platform/releases/0.1.25-deadbeef/backups/plan_platform-mysql-20261028-005000.sql.gz']:
            with self.subTest(path=p),self.assertRaises(RuntimeError):m.backup_location(p)
if __name__=='__main__':unittest.main()
