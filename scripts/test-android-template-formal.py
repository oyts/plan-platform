import importlib.util,unittest
from pathlib import Path
from xml.etree.ElementTree import Element
spec=importlib.util.spec_from_file_location('formal',Path(__file__).with_name('android-template-formal.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class Guards(unittest.TestCase):
    def test_identity(self):
        self.assertEqual(m.ui.PACKAGE,'cn.zijun701.plan.app');self.assertEqual(len(m.SHA),64)
        m.u.verify_version('versionName=0.1.19 versionCode=24 ','0.1.19',24)
        with self.assertRaises(RuntimeError):m.u.verify_version('versionName=0.1.18 versionCode=23 ','0.1.19',24)
    def test_auth_refused(self):
        with self.assertRaises(RuntimeError):m.ui.assert_safe_snapshot([Element('node',{'text':'登录计划平台','password':'true'})])
    def test_errors_refused(self):
        with self.assertRaises(RuntimeError):m.u.no_errors([Element('node',{'text':'请求未完成（HTTP 500）'})])
    def test_allowed_template_controls(self):
        for label in ['个人模板库','个人整套模板库','保存一套结构','预览保存结构']:
            self.assertIn(label,m.ui.LABELS)
if __name__=='__main__':unittest.main()
