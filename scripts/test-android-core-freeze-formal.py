import importlib.util,unittest
from pathlib import Path
from xml.etree.ElementTree import Element
spec=importlib.util.spec_from_file_location('core_formal',Path(__file__).with_name('android-core-freeze-formal.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Guards(unittest.TestCase):
    def test_exact_identity_and_versions(self):
        self.assertEqual(m.base.ui.PACKAGE,'cn.zijun701.plan.app')
        self.assertEqual((m.base.BEFORE_VERSION,m.base.BEFORE_CODE,m.base.VERSION,m.base.CODE),('0.1.25',30,'0.1.26',31))
        self.assertEqual(len(m.base.SHA),64)
    def test_no_authentication_or_password_ui(self):
        with self.assertRaises(RuntimeError):m.base.ui.assert_safe_snapshot([Element('node',{'text':'登录计划平台','password':'true'})])
    def test_connection_failure_is_not_a_pass(self):
        with self.assertRaises(RuntimeError):m.base.u.no_errors([Element('node',{'text':'请求未完成（HTTP 500）'})])
if __name__=='__main__':unittest.main()
