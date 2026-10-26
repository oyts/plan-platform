import importlib.util,time,unittest
from unittest.mock import patch
from types import SimpleNamespace
from pathlib import Path

def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
a=module('metric_activation','activate-metrics-release.py');t=module('metric_runtime','activate-runtime-metrics.py')

class MetricsReleaseGuards(unittest.TestCase):
    def test_exact_versions_and_old_capabilities(self):
        self.assertEqual((a.activation.NEW_VERSION,a.activation.OLD_VERSION,a.activation.MIGRATION),('0.1.24','0.1.23','V33 -> V34'))
        self.assertEqual(a.activation.ROLLBACK_CAPABILITIES[-4:],['--target-supports-admin-runtime','yes','--target-supports-metrics','no'])
    def test_unaccepted_or_stale_proof_refused(self):
        with self.assertRaises(RuntimeError):a.validate_proof({},time.time(),time.time())
        proof=dict(status='PASS',migration='V33 -> V34',previousImage='plan-platform-api:0.1.23',targetImage='plan-platform-api:0.1.24',productionApplicationChanged=False,apiSha256=a.activation.HASHES['backend/api.jar'],archiveSha256=a.activation.HASHES['release.tar.gz'],originalProjectionTables=['operations_mail_delivery'],originalTablesChecked=58,snapshotRestore=dict(tablesChecked=59))
        a.validate_proof(proof,time.time(),time.time())
        with self.assertRaises(RuntimeError):a.validate_proof(proof,time.time()-3601,time.time())
        proof['archiveSha256']='0'*64
        with self.assertRaises(RuntimeError):a.validate_proof(proof,time.time(),time.time())
    def test_collector_activation_postpones_known_paid_work(self):
        inspect='[{"Config":{"Env":["MYSQL_ROOT_PASSWORD=synthetic-only"]}}]'
        with patch.object(t,'run',return_value=inspect),patch.object(t.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout=b'0\n')):t.require_idle()
        with patch.object(t,'run',return_value=inspect),patch.object(t.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout=b'1\n')):
            with self.assertRaisesRegex(RuntimeError,'Active AI'):t.require_idle()
    def test_environment_retains_security_flags(self):
        body='PLAN_VERSION=0.1.23\nEMAIL_CHANGE_ENABLED=true\nAI_REPAIR_ENABLED=true\n'
        self.assertEqual(a.next_environment(body),body.replace('0.1.23','0.1.24'))
        for flag in ['EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED']:
            with self.assertRaises(RuntimeError):a.next_environment(body.replace(flag+'=true',flag+'=false'))
    def test_unknown_or_private_template_refused(self):
        with self.assertRaises(RuntimeError):t.validate_templates({})
        files=dict.fromkeys(['nginx.conf','nginx-base.conf','compose.yml','collect.sh','logrotate.conf'],'')
        files['nginx.conf']="log_format plan_private_metrics '$remote_addr';\naccess_log /var/log/plan-metrics/plan-metrics.log plan_private_metrics"
        with self.assertRaisesRegex(RuntimeError,'Private source'):t.validate_templates(files)
    def test_disabling_mail_and_rotation_are_deployment_gates(self):
        files={'nginx.conf':"log_format plan_private_metrics '$msec $status';\naccess_log /var/log/plan-metrics/plan-metrics.log plan_private_metrics;\nlocation = /client-updates/android/latest.json { return 404; }\nlocation = /downloads/manifest.json",'nginx-base.conf':'base','compose.yml':'TELEMETRY_MAIL_ALERTS_ENABLED: "false"','collect.sh':'flock -n 9\n/usr/sbin/logrotate -s','logrotate.conf':'rotate 2\nsize 4M\nsu oyts oyts'}
        t.validate_templates(files)
        for key in ['compose.yml','collect.sh','logrotate.conf']:
            with self.subTest(key=key):
                changed=dict(files);changed[key]='';
                with self.assertRaises(RuntimeError):t.validate_templates(changed)
if __name__=='__main__':unittest.main()
