"""Version guard unit tests, not device acceptance."""
import importlib.util
import unittest
from unittest.mock import Mock, patch
from pathlib import Path
spec=importlib.util.spec_from_file_location('upgrade',Path(__file__).with_name('android-post-upgrade-acceptance.py'))
upgrade=importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)
class UpgradeVersionTests(unittest.TestCase):
    def test_accepts_only_explicit_matching_version_and_code(self):
        upgrade.verify_version('versionName=0.1.8\nversionCode=13 minSdk=24','0.1.8',13)
        upgrade.verify_version('versionName=0.1.7\nversionCode=12 minSdk=24','0.1.7',12)
    def test_rejects_stale_or_prefix_matching_metadata(self):
        for text in ['versionName=0.1.7\nversionCode=12','versionName=0.1.80\nversionCode=13','versionName=0.1.8\nversionCode=130','versionName=0.1.8']:
            with self.assertRaises(RuntimeError):upgrade.verify_version(text,'0.1.8',13)
    def test_rejects_invalid_expected_identity(self):
        for version,code in [('latest',13),('0.1.8',0),('0.1.8','13')]:
            with self.assertRaises(RuntimeError):upgrade.verify_version('',version,code)
    def test_todo_reentry_switches_from_a_remembered_non_todo_mode(self):
        device=Mock()
        device.snapshot.return_value=[]
        with patch.object(upgrade,'wait',return_value=[]):upgrade.todo(device,'nativeupgrade-test-offline')
        self.assertEqual([call.args[0] for call in device.click.call_args_list],['工作模式','今日待办'])
        device.click_navigation.assert_called_once_with('待办')
    def test_todo_reentry_keeps_the_existing_todo_mode(self):
        device=Mock()
        device.snapshot.return_value=[{'content-desc':'待办','clickable':'true'}]
        with patch.object(upgrade,'wait',return_value=[]):upgrade.todo(device,'nativeupgrade-test-offline')
        device.click.assert_not_called()
        device.click_navigation.assert_called_once_with('待办')
    def test_hidden_or_dock_occluded_control_is_not_actionable(self):
        for area in ['[100,2738][400,2738]','[100,2400][400,2650]','[100,147][400,300]']:
            nodes=[{'text':'同步等待项','clickable':'true','bounds':area},{'text':'更多','clickable':'true','bounds':'[600,2506][1260,2738]'}]
            self.assertFalse(upgrade.visible_above_dock(nodes,'同步等待项'))
    def test_only_fully_visible_unique_control_can_be_used(self):
        nodes=[{'text':'同步等待项','clickable':'true','bounds':'[100,500][400,650]'},{'text':'更多','clickable':'true','bounds':'[600,2506][1260,2738]'}]
        self.assertTrue(upgrade.visible_above_dock(nodes,'同步等待项'))
        self.assertFalse(upgrade.visible_above_dock(nodes+[nodes[0]],'同步等待项'))
    def test_accessibility_labelled_course_link_can_be_revealed(self):
        nodes=[{'content-desc':'周期计划','clickable':'true','bounds':'[100,500][400,650]'},{'text':'更多','clickable':'true','bounds':'[600,2506][1260,2738]'}]
        self.assertTrue(upgrade.visible_above_dock(nodes,'周期计划'))
if __name__=='__main__':unittest.main()
