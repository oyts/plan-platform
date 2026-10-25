import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('rollback_guard',Path(__file__).with_name('check-governance-rollback.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Guards(unittest.TestCase):
    def query(self,generation=True,revoked=0,v32=True,flows=0,v33=False,admin_changes=0):
        def q(statement):
            if 'information_schema.columns' in statement:return str(int(generation if 'auth_generation' in statement else v33 if 'runtime_version' in statement else v32))
            if 'auth_generation>0' in statement:return str(revoked)
            if 'ai_long_plans' in statement:return str(flows)
            if 'admin_invitation_operations' in statement:return str(admin_changes)
            raise AssertionError('Unexpected query')
        return q
    def test_old_schema_and_empty_new_protocol_allow_prior_backend(self):
        for v32 in [False,True]:m.check_rollback(self.query(v32=v32),'plan_platform',True,False)
    def test_long_planning_refuses_old_backend_even_after_generation_guard(self):
        with self.assertRaisesRegex(RuntimeError,'long planning'):m.check_rollback(self.query(flows=2),'plan_platform',True,False)
    def test_new_backend_may_preserve_protocol_but_not_revoked_credentials(self):
        m.check_rollback(self.query(flows=2,revoked=1),'plan_platform',True,True)
        with self.assertRaisesRegex(RuntimeError,'governed'):m.check_rollback(self.query(flows=2,revoked=1),'plan_platform',False,True)
    def test_query_failure_cannot_be_treated_as_permission(self):
        def unavailable(_):raise RuntimeError('db unavailable')
        with self.assertRaises(RuntimeError):m.check_rollback(unavailable,'plan_platform',True,True)
    def test_runtime_config_invites_and_job_snapshots_refuse_older_writer(self):
        m.check_rollback(self.query(v33=True),'plan_platform',True,True,False)
        with self.assertRaisesRegex(RuntimeError,'admin runtime'):m.check_rollback(self.query(v33=True,admin_changes=1),'plan_platform',True,True,False)
        m.check_rollback(self.query(v33=True,admin_changes=5),'plan_platform',True,True,True)
    def test_database_injection_or_unmanaged_target_rejected(self):
        for name in ['mysql','plan_platform;DROP','long_../../','long_abc']:
            with self.assertRaises(RuntimeError):m.check_rollback(self.query(),name)
        m.check_rollback(self.query(),'long_f03ebc54',True)
if __name__=='__main__':unittest.main()
