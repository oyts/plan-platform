import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('prepare',Path(__file__).with_name('prepare-account-operations-overlay.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class OverlayGuards(unittest.TestCase):
    def test_only_an_inactive_restricted_candidate_and_no_mail_activation(self):
        text=m.render(Path('/home/oyts/plan-platform/operations'),'synthetic@example.test')
        self.assertIn('OPERATIONS_MAIL_ALERTS_ENABLED: "false"',text);self.assertIn('read_only: true',text);self.assertIn('EMAIL_VERIFICATION_ENABLED: "true"',text);self.assertNotIn('PASSWORD',text)
    def test_rejects_header_and_recipient_list(self):
        for value in ['one@example.test,two@example.test','one@example.test\nBcc: other@example.test']:
            with self.assertRaises(ValueError):m.render(Path('/home/oyts/plan-platform/operations'),value)
    def test_rejects_broad_or_injected_directory(self):
        for value in ['/','/home/oyts/plan-platform','/tmp/operations','/home/oyts/plan-platform/operations\nother']:
            with self.assertRaises(ValueError):m.render(Path(value),'synthetic@example.test')
if __name__=='__main__':unittest.main()
