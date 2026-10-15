"""No network: guardrails for the bounded existing-object acceptance runner."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('adjustment_acceptance',Path(__file__).with_name('ai-adjustment-acceptance.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class AdjustmentHarnessTest(unittest.TestCase):
    def test_explicit_three_request_bound_before_any_api(self):
        harness=object.__new__(module.Adjustments)
        with patch.object(module,'api') as api:
            for bound in (0,1,2,4,5,100):
                with self.assertRaisesRegex(RuntimeError,'three bounded'):harness.real_adjustments(bound)
            api.assert_not_called()

    def test_reuses_persist_before_submit_counter_without_retry(self):
        self.assertIs(module.Adjustments.turn,module.Acceptance.turn)

    def test_default_entry_is_free_and_explicit_flag_gates_paid_calls(self):
        source=Path(__file__).with_name('ai-adjustment-acceptance.py').read_text(encoding='utf-8')
        self.assertIn("'--real-max-calls',type=int,default=0",source)
        self.assertIn("'adjustment-real-started.json'",source)
        self.assertIn('private_write(marker, state, exclusive=True)',source)

if __name__=='__main__':unittest.main()
