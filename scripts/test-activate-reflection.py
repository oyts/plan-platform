import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('reflection', Path(__file__).with_name('activate-reflection-release.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
a = m.activation


class Guards(unittest.TestCase):
    def proof(self):
        return dict(status='PASS', migration='V25 -> V27', previousImage='plan-platform-api:0.1.17',
                    targetImage='plan-platform-api:0.1.18', productionApplicationChanged=False,
                    apiSha256=a.HASHES['backend/api.jar'], archiveSha256=a.HASHES['release.tar.gz'],
                    originalTablesChecked=38, originalProjectionTables=[
                        'task_progress', 'weekly_reviews', 'restored_weekly_reviews', 'ai_sessions'])

    def test_fresh_final_proof(self):
        a.validate_proof(self.proof(), 100, 3700)

    def test_wrong_fields_and_incomplete_projection_rejected(self):
        for key, value in [('status', 'FAIL'), ('migration', 'V24 -> V25'), ('previousImage', 'old'),
                           ('targetImage', 'old'), ('apiSha256', '0'*64), ('archiveSha256', '0'*64),
                           ('productionApplicationChanged', True), ('originalTablesChecked', 0),
                           ('originalProjectionTables', ['task_progress'])]:
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                a.validate_proof(dict(self.proof(), **{key: value}), 100, 101)

    def test_proof_age(self):
        for now in [99, 3701]:
            with self.assertRaises(RuntimeError):
                a.validate_proof(self.proof(), 100, now)

    def test_only_version_changes(self):
        body='PLAN_VERSION=0.1.17\nPRIVATE=synthetic\nAI_ENABLED=true\n'
        self.assertEqual(a.next_environment(body), body.replace('0.1.17', '0.1.18'))
        for invalid in ['', 'PLAN_VERSION=0.1.16\n', body+body]:
            with self.assertRaises(RuntimeError):
                a.next_environment(invalid)

    def test_runtime_flags_and_readonly_mounts(self):
        runtime={'Config': {'Env': [f+'=true' for f in a.FLAGS]}, 'Mounts': [
            {'Destination': '/run/secrets/deepseek-api-key', 'RW': False},
            {'Destination': '/run/plan-operations', 'RW': False}]}
        a.approved_runtime(runtime)
        runtime['Mounts'][0]['RW']=True
        with self.assertRaises(RuntimeError):
            a.approved_runtime(runtime)


if __name__ == '__main__':
    unittest.main()
