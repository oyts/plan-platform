import importlib.util,unittest
from pathlib import Path
from xml.etree.ElementTree import Element
spec=importlib.util.spec_from_file_location('mailops',Path(__file__).with_name('android-mail-operations-formal.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def node(text='',**attributes):return Element('node',{'text':text,**attributes})
class ReadOnlySettingsGuards(unittest.TestCase):
    def test_passwords_still_refused_by_default(self):
        with self.assertRaises(RuntimeError):m.ui.assert_safe_snapshot([node(password='true')])
    def test_only_explicit_empty_settings_read_allowed(self):
        m.ui.assert_safe_snapshot([node('账号安全与数据迁移'),node(password='true')],True)
    def test_populated_password_refused_even_on_settings(self):
        with self.assertRaises(RuntimeError):m.ui.assert_safe_snapshot([node('账号安全与数据迁移'),node('synthetic',password='true')],True)
    def test_login_never_allowed(self):
        for title in ['登录计划平台','注册计划平台']:
            with self.assertRaises(RuntimeError):m.ui.assert_safe_snapshot([node(title),node(password='true')],True)
    def test_unknown_password_screen_refused(self):
        with self.assertRaises(RuntimeError):m.ui.assert_safe_snapshot([node('Unknown'),node(password='true')],True)
if __name__=='__main__':unittest.main()
