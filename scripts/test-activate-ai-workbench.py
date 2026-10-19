import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('activation', Path(__file__).with_name('activate-ai-workbench-release.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class Guards(unittest.TestCase):
    def test_interruption_enters_exception_rollback_path(self):
        with self.assertRaisesRegex(RuntimeError, 'guarded application rollback'):
            m.interrupted(15, None)

    def proof(self):
        return dict(status='PASS', migration='V24 -> V25', previousImage='plan-platform-api:0.1.16',
                    targetImage='plan-platform-api:0.1.17', productionApplicationChanged=False,
                    apiSha256=m.HASHES['backend/api.jar'])

    def test_matching_fresh_proof(self):
        m.validate_proof(self.proof(), 100, 3700)

    def test_reject_wrong_proof_fields(self):
        for name, value in [('status', 'FAIL'), ('migration', 'V23 -> V24'), ('previousImage', 'old'),
                            ('targetImage', 'old'), ('apiSha256', '0' * 64), ('productionApplicationChanged', True)]:
            with self.subTest(name=name), self.assertRaises(RuntimeError):
                m.validate_proof(dict(self.proof(), **{name: value}), 100, 101)

    def test_expired_and_future_proofs(self):
        for now in [99, 3701]:
            with self.assertRaises(RuntimeError):
                m.validate_proof(self.proof(), 100, now)

    def test_environment_changes_version_only(self):
        body = 'DB_PASSWORD=synthetic\nPLAN_VERSION=0.1.16\nAI_ENABLED=true\n'
        self.assertEqual(m.next_environment(body), body.replace('0.1.16', '0.1.17'))

    def test_environment_rejects_missing_wrong_duplicate_versions(self):
        for body in ['', 'PLAN_VERSION=0.1.15\n', 'PLAN_VERSION=0.1.16\nPLAN_VERSION=0.1.16\n']:
            with self.assertRaises(RuntimeError):
                m.next_environment(body)

    def runtime(self):
        return {'Config': {'Env': [f + '=true' for f in m.FLAGS]}, 'Mounts': [
            {'Destination': '/run/secrets/deepseek-api-key', 'RW': False},
            {'Destination': '/run/plan-operations', 'RW': False}]}

    def test_approved_runtime(self):
        m.approved_runtime(self.runtime())

    def test_do_not_disable_previously_enabled_mail(self):
        c = self.runtime()
        c['Config']['Env'][-1] = 'OPERATIONS_MAIL_ALERTS_ENABLED=false'
        with self.assertRaises(RuntimeError):
            m.approved_runtime(c)

    def test_no_writable_secret_mount(self):
        for index in [0, 1]:
            c = self.runtime()
            c['Mounts'][index]['RW'] = True
            with self.assertRaises(RuntimeError):
                m.approved_runtime(c)


if __name__ == '__main__':
    unittest.main()
