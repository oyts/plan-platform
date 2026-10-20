import importlib.util
import unittest
from pathlib import Path
from xml.etree.ElementTree import Element

spec=importlib.util.spec_from_file_location('formal',Path(__file__).with_name('android-reflection-formal.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)


class ReadOnlyGuards(unittest.TestCase):
    def test_release_identity(self):
        self.assertEqual(m.ui.PACKAGE,'cn.zijun701.plan.app')
        self.assertEqual(len(m.SHA),64)
        m.u.verify_version('versionName=0.1.18 versionCode=23 ', '0.1.18',23)
        with self.assertRaises(RuntimeError):
            m.u.verify_version('versionName=0.1.17 versionCode=22 ', '0.1.18',23)

    def test_auth_page_refused(self):
        with self.assertRaises(RuntimeError):
            m.ui.assert_safe_snapshot([Element('node',{'text':'登录计划平台','password':'true'})])

    def test_connection_error_refused(self):
        with self.assertRaises(RuntimeError):
            m.u.no_errors([Element('node',{'text':'请求未完成（HTTP 500）'})])

    def test_formal_entries_allowlisted(self):
        for label in ['日周复盘','系统提醒','保存云端复盘','开启本设备提醒']:
            self.assertIn(label,m.ui.LABELS)


if __name__=='__main__':unittest.main()
