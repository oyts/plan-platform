"""Only the newly added formal navigation visibility guard; no device needed."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('navigation', Path(__file__).with_name('android-navigation-acceptance.py'))
navigation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(navigation)

class NavigationVisibilityTest(unittest.TestCase):
    def node(self, bounds, label='使用教学'):
        return {'text': label, 'class': 'android.widget.TextView', 'bounds': bounds}

    def test_visible_header_at_top(self):
        self.assertTrue(navigation.near_top([self.node('[56,413][910,535]')], '使用教学'))

    def test_clipped_header_is_not_success(self):
        self.assertFalse(navigation.near_top([self.node('[56,147][910,147]')], '使用教学'))

    def test_header_far_down_is_not_success(self):
        self.assertFalse(navigation.near_top([self.node('[56,1800][910,1900]')], '使用教学'))

    def test_different_page_is_not_success(self):
        self.assertFalse(navigation.near_top([self.node('[56,413][910,535]', '手机客户端')], '使用教学'))

if __name__ == '__main__':
    unittest.main()
