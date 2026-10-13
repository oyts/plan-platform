"""Offline safety tests. They do not represent a device acceptance result."""
import importlib.util
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

spec=importlib.util.spec_from_file_location('android_ui',Path(__file__).with_name('android-ui-acceptance.py'))
ui=importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)

class SyntheticInputSafetyTests(unittest.TestCase):
    def device(self,label='待办标题',before='',after='nativeupgrade-test',outcome='OK (1 test)'):
        device=ui.Device('test-device')
        snapshots=iter([
            [ET.Element('node',text=label),ET.Element('node',text=before,attrib={'class':'android.widget.EditText'})],
            [ET.Element('node',text=label),ET.Element('node',text=after,attrib={'class':'android.widget.EditText'})],
        ])
        device.snapshot=lambda:next(snapshots)
        device.foreground=lambda:None
        calls=[]
        def adb(*args):
            calls.append(args)
            return outcome
        device.adb=adb
        return device,calls

    def test_rejects_non_synthetic_input(self):
        device,calls=self.device()
        with self.assertRaises(RuntimeError):device.type_title('real task')
        self.assertEqual(calls,[])

    def test_preserves_existing_real_title(self):
        device,calls=self.device(before='user task')
        with self.assertRaises(RuntimeError):device.type_title('nativeupgrade-test')
        self.assertEqual(calls,[])

    def test_requires_instrumentation_success(self):
        device,_=self.device(outcome='FAILURES!!! Tests run: 1, Failures: 1')
        with self.assertRaises(RuntimeError):device.type_title('nativeupgrade-test')

    def test_requires_exact_field_readback(self):
        device,_=self.device(after='wrong value')
        with self.assertRaises(RuntimeError):device.type_title('nativeupgrade-test')

    def test_sets_synthetic_title_without_key_injection(self):
        device,calls=self.device()
        device.type_title('nativeupgrade-test')
        self.assertEqual(calls[0][:3],('shell','am','instrument'))
        self.assertIn('nativeupgrade-test',calls[0])
        self.assertNotIn('input',calls[0])

    def test_clears_search_without_empty_shell_argument(self):
        device,calls=self.device(label='搜索',before='probe',after='')
        device.type_title('',label='搜索')
        self.assertNotIn('text',calls[0])
        self.assertNotIn('',calls[0])

if __name__=='__main__':unittest.main()
