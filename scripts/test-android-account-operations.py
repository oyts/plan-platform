"""Safety checks for the focused 0.1.13 real-device driver; no device needed."""
import importlib.util
import unittest
from pathlib import Path
from xml.etree.ElementTree import Element

spec = importlib.util.spec_from_file_location('operations', Path(__file__).with_name('android-account-operations-acceptance.py'))
operations = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operations)


def node(text, bounds='[10,500][400,600]', **attributes):
    return Element('node', {'text': text, 'bounds': bounds, **attributes})


class Device:
    def __init__(self, snapshots):
        self.snapshots = iter(snapshots)
        self.scrolls = 0

    def snapshot(self):
        return next(self.snapshots)

    def scroll(self, direction):
        assert direction == 'down'
        self.scrolls += 1


class OperationsSafetyTest(unittest.TestCase):
    def test_field_requires_exact_adjacent_state(self):
        operations.field([node('密码找回'), node('已启用')], '密码找回', '已启用')
        for rows in ([node('密码找回'), node('未启用')], [node('密码找回')],
                     [node('密码找回'), node('已启用'), node('密码找回')]):
            with self.assertRaises(RuntimeError):
                operations.field(rows, '密码找回', '已启用')

    def test_hidden_control_is_not_actionable(self):
        dock = node('更多', '[0,2500][1260,2738]', clickable='true')
        hidden = [node('刷新用量', '[0,2600][500,2700]'), dock]
        visible = [node('刷新用量'), dock]
        device = Device([hidden, visible])
        self.assertIs(operations.reveal(device, '刷新用量', limit=2), visible)
        self.assertEqual(device.scrolls, 1)

    def test_missing_dock_refuses_interaction(self):
        with self.assertRaises(RuntimeError):
            operations.reveal(Device([[node('刷新用量')]]), '刷新用量', limit=1)

    def test_business_error_stops_before_scroll(self):
        device = Device([[node('Error: synthetic connection failure')]])
        with self.assertRaises(RuntimeError):
            operations.reveal(device, '刷新用量', limit=1)
        self.assertEqual(device.scrolls, 0)

    def test_clipped_header_not_accepted(self):
        dock = node('更多', '[0,2500][1260,2738]', clickable='true')
        device = Device([[node('刷新用量', '[0,200][500,300]'), dock]])
        with self.assertRaises(RuntimeError):
            operations.reveal(device, '刷新用量', limit=1)

    def test_accessible_link_description_supported(self):
        dock = node('更多', '[0,2500][1260,2738]', clickable='true')
        link = node('', **{'content-desc': '进入 AI 四步教学'})
        nodes = [link, dock]
        self.assertTrue(operations.present(nodes, '进入 AI 四步教学'))
        self.assertIs(operations.reveal(Device([nodes]), '进入 AI 四步教学', limit=1), nodes)


if __name__ == '__main__':
    unittest.main()
