import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('formal_workbench', Path(__file__).with_name('android-ai-workbench-formal.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class SnapshotGuards(unittest.TestCase):
    def test_normal_read_is_not_retried(self):
        with patch.object(m.ui.Device, 'snapshot', return_value=[]) as read:
            self.assertEqual(m.StableDevice('synthetic').snapshot(), [])
            self.assertEqual(read.call_count, 1)

    def test_one_dump_failure_is_retried_once(self):
        with patch.object(m.ui.Device, 'snapshot', side_effect=[RuntimeError('Android command failed; private output withheld.'), []]) as read, patch.object(m.time, 'sleep'):
            self.assertEqual(m.StableDevice('synthetic').snapshot(), [])
            self.assertEqual(read.call_count, 2)

    def test_repeated_dump_failure_stops(self):
        with patch.object(m.ui.Device, 'snapshot', side_effect=RuntimeError('Android command failed; private output withheld.')) as read, patch.object(m.time, 'sleep'):
            with self.assertRaises(RuntimeError):
                m.StableDevice('synthetic').snapshot()
            self.assertEqual(read.call_count, 2)

    def test_auth_and_foreground_failures_are_never_retried(self):
        for message in ['Authentication UI visible; no input/capture performed.', 'Formal app is not focused; unlock/return to Plan Cabin before input.']:
            with patch.object(m.ui.Device, 'snapshot', side_effect=RuntimeError(message)) as read:
                with self.assertRaisesRegex(RuntimeError, message.split(';')[0]):
                    m.StableDevice('synthetic').snapshot()
                self.assertEqual(read.call_count, 1)


if __name__ == '__main__':
    unittest.main()
