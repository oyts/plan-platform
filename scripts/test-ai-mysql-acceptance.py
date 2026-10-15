"""Offline tests for the isolated acceptance harness; never contacts DeepSeek/ECS."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('ai_acceptance', Path(__file__).with_name('ai-mysql-acceptance.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class HarnessTest(unittest.TestCase):
    def test_invitation_matches_current_product_parameter(self):
        self.assertEqual(module.invitation_token('https://example.test/register?email=synthetic%40example.test&invite=synthetic.token'), 'synthetic.token')
        with self.assertRaises(RuntimeError):
            module.invitation_token('https://example.test/register?invite=a&invite=b')

    def test_private_write_is_exclusive_when_call_marker_already_exists(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory)/'marker.json'
            module.private_write(marker, {'submitted':1}, exclusive=True)
            with self.assertRaises(FileExistsError):
                module.private_write(marker, {'submitted':0}, exclusive=True)
            self.assertEqual(json.loads(marker.read_text())['submitted'], 1)

    def test_rejects_non_candidate_before_any_network_or_docker_call(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(module.subprocess, 'check_output') as docker:
            with self.assertRaisesRegex(RuntimeError, 'isolated candidate'):
                module.Acceptance(Path(directory))
            docker.assert_not_called()

    def test_real_paid_scenario_requires_exact_explicit_bound(self):
        harness = object.__new__(module.Acceptance)
        with patch.object(module, 'api') as api:
            for maximum in (0,1,4,6,100):
                with self.assertRaisesRegex(RuntimeError, 'exactly five'):
                    harness.real(maximum)
            api.assert_not_called()

    def test_paid_counter_persisted_before_an_uncertain_submission(self):
        harness = object.__new__(module.Acceptance)
        harness.owner = 'synthetic-test-token'
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory)/'marker.json'
            state = {'submitted':0,'maxCalls':5,'results':[]}
            with patch.object(module, 'api', side_effect=OSError('simulated disconnected response')) as api:
                with self.assertRaises(OSError):
                    harness.turn({'id':'test','version':0}, 'synthetic', state, marker)
                self.assertEqual(api.call_count, 1)
                self.assertEqual(json.loads(marker.read_text())['submitted'], 1)

    def test_never_submits_sixth_request(self):
        harness = object.__new__(module.Acceptance)
        with patch.object(module, 'api') as api:
            with self.assertRaisesRegex(RuntimeError, 'bound reached'):
                harness.turn({}, '', {'submitted':5,'maxCalls':5}, Path('unused'))
            api.assert_not_called()


if __name__ == '__main__':
    unittest.main()
