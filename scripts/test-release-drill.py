import gzip
import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec=importlib.util.spec_from_file_location('drill', Path(__file__).with_name('roadmap-release-drill.py'))
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class SnapshotGuards(unittest.TestCase):
    def test_closure_uses_exact_previous_version_and_schema(self):
        self.assertEqual(m.selected_profile('historical-closure'),('0.1.20','0.1.19',28,30))
        self.assertEqual(m.selected_profile('templates'),('0.1.19','0.1.18',27,28))
        with self.assertRaises(RuntimeError):m.selected_profile('unknown')
    def backup(self, folder, body):
        p=Path(folder)/'synthetic.sql.gz'
        p.write_bytes(gzip.compress(body))
        Path(str(p)+'.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
        return p

    def test_verified_dump(self):
        with tempfile.TemporaryDirectory() as folder:
            p=self.backup(folder,b'CREATE TABLE test(id INT);\n')
            self.assertEqual(m.snapshot_bytes(p),b'CREATE TABLE test(id INT);\n')

    def test_changed_bytes_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            p=self.backup(folder,b'-- synthetic\n')
            p.write_bytes(gzip.compress(b'-- changed\n'))
            with self.assertRaises(RuntimeError): m.snapshot_bytes(p)

    def test_database_switches_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            for body in [b'USE plan_platform;',b'CREATE DATABASE plan_platform;',b'  DROP DATABASE plan_platform;']:
                with self.subTest(body=body):
                    p=self.backup(folder,body)
                    with self.assertRaises(RuntimeError): m.snapshot_bytes(p)

    def test_reference_checksum_mismatch_stops(self):
        def sql(query):
            if query.startswith('CREATE'): return ''
            if 'information_schema' in query: return 'synthetic'
            return 'table\t' + ('2' if '_reference.' in query else '1')
        with tempfile.TemporaryDirectory() as folder:
            p=self.backup(folder,b'-- synthetic\n')
            original=m.command
            try:
                m.command=lambda *args,**kwargs:''
                with self.assertRaisesRegex(RuntimeError,'checksum differs'): m.snapshot_restore(p,sql,{})
            finally: m.command=original


if __name__=='__main__': unittest.main()
