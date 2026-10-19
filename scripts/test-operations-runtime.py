import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('runtime',Path(__file__).with_name('configure-operations-runtime.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class RuntimeGuards(unittest.TestCase):
    def setUp(self):self.root=Path('/home/oyts/plan-platform');self.backups=self.root/'releases/0.1.15-50bc92a/backups'
    def test_preserves_existing_cron_and_is_idempotent(self):
        old='15 3 * * * /bin/sh /approved-backup.sh\n';new=m.merged_cron(old,self.root)
        self.assertTrue(new.startswith(old));self.assertEqual(m.merged_cron(new,self.root),new)
    def test_rejects_changed_managed_entry(self):
        with self.assertRaises(ValueError):m.merged_cron('* * * * * unexpected '+m.MARKER,self.root)
    def test_rejects_duplicate_entries(self):
        with self.assertRaises(ValueError):m.merged_cron((m.MARKER+'\n')*2,self.root)
    def test_paths_cannot_be_injected_or_broad(self):
        for path in ['/',str(self.root),'/tmp/backups',str(self.backups)+';echo unsafe']:
            with self.assertRaises(ValueError):m.validate(self.root,Path(path))
    def test_collection_has_no_mail_database_or_delete(self):
        script=m.collector_body(self.root,self.backups)
        self.assertIn('flock -n',script);self.assertIn('test -f',script);self.assertIn('--group-readable',script)
        for forbidden in ['rm ','docker ','sendmail','smtp','mysqldump']:self.assertNotIn(forbidden,script)
if __name__=='__main__':unittest.main()
