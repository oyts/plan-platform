import datetime as dt,gzip,hashlib,importlib.util,json,tempfile,unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('collector',Path(__file__).with_name('operations-status.py'));collector=importlib.util.module_from_spec(spec);spec.loader.exec_module(collector)

class CollectorTest(unittest.TestCase):
    def setup_tree(self,root):
        backups=root/'backups';backups.mkdir();operations=root/'operations';operations.mkdir();return backups,operations
    def backup(self,directory,name='plan_platform-mysql-20261018-010000.sql.gz'):
        path=directory/name;path.write_bytes(gzip.compress(b'-- synthetic SQL, no real account data\n'));digest=hashlib.sha256(path.read_bytes()).hexdigest();Path(str(path)+'.sha256').write_text(digest+'  '+name+'\n');return path,digest
    def args(self,backups,operations,verify=False):return SimpleNamespace(output=str(operations/'status.json'),backup_directory=str(backups),drill_evidence=str(operations/'drill.json'),failure_evidence=str(operations/'failures.json'),verify_oss=verify,group_readable=False)
    def test_bounded_local_verification_never_deletes_backups_and_does_not_guess_oss_or_drill(self):
        with tempfile.TemporaryDirectory(prefix='plan-ops-fixture-') as name:
            backups,operations=self.setup_tree(Path(name));path,digest=self.backup(backups);(backups/'unrelated-key.pem').write_text('synthetic-not-a-key');before=path.read_bytes();result=collector.collect(self.args(backups,operations));self.assertTrue(result['backups'][0]['localVerified']);self.assertEqual(result['backups'][0]['sha256'],digest);self.assertEqual(result['oss'],{});self.assertEqual(result['drill'],{});self.assertEqual(path.read_bytes(),before);self.assertEqual(len(list(backups.iterdir())),3)
    def test_bad_sidecar_zip_and_oversized_verification_are_unknown_not_false_success(self):
        with tempfile.TemporaryDirectory(prefix='plan-ops-fixture-') as name:
            backups,operations=self.setup_tree(Path(name));path,digest=self.backup(backups);self.assertEqual(collector.verify_local(path,[1,100]),'');Path(str(path)+'.sha256').write_text('0'*64+'  '+path.name);self.assertEqual(collector.verify_local(path,[10000,10000]),'');path.write_bytes(b'not gzip');Path(str(path)+'.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name);self.assertEqual(collector.verify_local(path,[10000,10000]),'')
    def test_oss_requires_downloaded_data_and_sidecar_with_identical_hash(self):
        with tempfile.TemporaryDirectory(prefix='plan-ops-fixture-') as name:
            backups,operations=self.setup_tree(Path(name));path,digest=self.backup(backups)
            def download(command,**kwargs):
                target=Path(command[3]);target.write_bytes(Path(str(path)+('.sha256' if command[2].endswith('.sha256') else '')).read_bytes())
            with patch.object(collector.subprocess,'run',side_effect=download):result=collector.collect(self.args(backups,operations,True))
            self.assertEqual(result['oss']['sha256'],digest);self.assertTrue(result['oss']['verified'])
            with patch.object(collector.subprocess,'run',side_effect=OSError('synthetic outage')):result=collector.collect(self.args(backups,operations,True))
            self.assertEqual(result['oss'],{});self.assertIn('OSS_FAILED',result['failures'])
    def test_output_is_limited_to_known_metadata_files_not_backup_or_secret_paths(self):
        with tempfile.TemporaryDirectory(prefix='plan-ops-fixture-') as name:
            backups,operations=self.setup_tree(Path(name))
            with self.assertRaises(ValueError):collector.output_path(str(backups/'status.json'),'status.json')
            with self.assertRaises(ValueError):collector.output_path(str(operations/'private-key.pem'),'status.json')
    def test_success_clears_only_oss_failure_and_stays_recovered_on_regular_collection(self):
        with tempfile.TemporaryDirectory(prefix='plan-ops-fixture-') as name:
            backups,operations=self.setup_tree(Path(name));path,digest=self.backup(backups)
            (operations/'failures.json').write_text(json.dumps({'OSS_FAILED':True,'BACKUP_FAILED':True,'RESTORE_FAILED':True}))
            def download(command,**kwargs):
                Path(command[3]).write_bytes(Path(str(path)+('.sha256' if command[2].endswith('.sha256') else '')).read_bytes())
            with patch.object(collector.subprocess,'run',side_effect=download):result=collector.collect(self.args(backups,operations,True))
            self.assertNotIn('OSS_FAILED',result['failures']);self.assertIn('BACKUP_FAILED',result['failures']);self.assertIn('RESTORE_FAILED',result['failures'])
            later=collector.collect(self.args(backups,operations))
            self.assertNotIn('OSS_FAILED',later['failures']);self.assertEqual(later['oss']['sha256'],digest)
    def test_failure_is_durable_until_real_verify_and_uses_bounded_tool_path(self):
        with tempfile.TemporaryDirectory(prefix='plan-ops-fixture-') as name:
            backups,operations=self.setup_tree(Path(name));self.backup(backups)
            with patch.object(collector.shutil,'which',return_value='/usr/local/bin/ossutil') as which, patch.object(collector.subprocess,'run',side_effect=OSError('synthetic outage')) as run:
                result=collector.collect(self.args(backups,operations,True))
            which.assert_called_with('ossutil',path='/usr/local/bin:/usr/bin:/bin')
            self.assertEqual(run.call_args[0][0][0],'/usr/local/bin/ossutil')
            self.assertIn('OSS_FAILED',result['failures']);self.assertIn('OSS_FAILED',collector.collect(self.args(backups,operations))['failures'])

if __name__=='__main__':unittest.main()
