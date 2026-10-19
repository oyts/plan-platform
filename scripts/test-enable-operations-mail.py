import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('enable',Path(__file__).with_name('enable-operations-mail.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
BODY='''services:
  backend:
    environment:
      OPERATIONS_ENABLED: "true"
      OPERATIONS_MAIL_ALERTS_ENABLED: "false"
      OPERATIONS_MAIL_TO: "synthetic@example.test"
    volumes:
      - read_only: true
'''
class EnableGuards(unittest.TestCase):
    def test_only_alert_flag_changes(self):
        output,recipient=m.next_overlay(BODY);self.assertEqual(recipient,'synthetic@example.test')
        self.assertEqual(output.replace('OPERATIONS_MAIL_ALERTS_ENABLED: "true"','OPERATIONS_MAIL_ALERTS_ENABLED: "false"'),BODY)
    def test_rejects_duplicate_or_already_enabled(self):
        for body in [BODY+BODY,BODY.replace('ALERTS_ENABLED: "false"','ALERTS_ENABLED: "true"')]:
            with self.assertRaises(RuntimeError):m.next_overlay(body)
    def test_rejects_multiple_or_injected_recipient(self):
        for recipient in ['one@example.test,two@example.test','one@example.test\nBcc: two@example.test']:
            with self.assertRaises(RuntimeError):m.next_overlay(BODY.replace('synthetic@example.test',recipient))
    def test_requires_operations_and_readonly(self):
        for body in [BODY.replace('OPERATIONS_ENABLED: "true"','OPERATIONS_ENABLED: "false"'),BODY.replace('read_only: true','read_only: false')]:
            with self.assertRaises(RuntimeError):m.next_overlay(body)
if __name__=='__main__':unittest.main()
