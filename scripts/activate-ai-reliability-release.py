"""Exact0.1.21 activation; preserve approved overlays and enable bounded repair only here."""
import importlib.util,re
from pathlib import Path
spec=importlib.util.spec_from_file_location('reliability_activation_base',Path(__file__).with_name('activate-ai-workbench-release.py'))
activation=importlib.util.module_from_spec(spec);spec.loader.exec_module(activation)
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.21','0.1.20','V30 -> V31'
activation.HASHES={'release.tar.gz':'459347da4c535141abf8174c17be5eee8617494e79269b2be03f0a0b8ed16841','backend/api.jar':'b19f89f94fcebcb0c50d51a8151f7c558ee2c99dd4a509473fe64605b7512737'}
base_validate,base_environment,base_runtime=activation.validate_proof,activation.next_environment,activation.approved_runtime
def validate_proof(proof,modified_at,now):
    base_validate(proof,modified_at,now)
    activation.require(proof.get('archiveSha256')==activation.HASHES['release.tar.gz']
        and proof.get('originalProjectionTables')==['ai_quota_requests','ai_sessions','ai_jobs']
        and proof.get('originalTablesChecked',0)>=50
        and proof.get('snapshotRestore',{}).get('tablesChecked',0)>=51,'Complete V30 immutable snapshot and original-field projections required.')
def next_environment(body):
    activation.require(re.findall(r'^EMAIL_CHANGE_ENABLED=(.*)$',body,re.M)==['true'],'Retain approved email-change feature.')
    values=re.findall(r'^AI_REPAIR_ENABLED=(.*)$',body,re.M)
    activation.require(values in [[],['false'],['true']],'Ambiguous repair flag.')
    updated=base_environment(body)
    return re.sub(r'^AI_REPAIR_ENABLED=.*$','AI_REPAIR_ENABLED=true',updated,flags=re.M) if values else updated.rstrip('\n')+'\nAI_REPAIR_ENABLED=true\n'
def approved_runtime(container):
    base_runtime(container)
    values=dict(v.split('=',1) for v in container['Config']['Env'] if '=' in v)
    activation.require(values.get('EMAIL_CHANGE_ENABLED')=='true','Approved mailbox change must remain enabled.')
    activation.require(any(m['Destination']=='/run/secrets/mail' and not m['RW'] for m in container['Mounts']),'Retained read-only SMTP mount required.')
    if container['Config']['Image']=='plan-platform-api:0.1.21':
        activation.require(values.get('AI_REPAIR_ENABLED')=='true','Bounded repair not enabled in new release.')
activation.validate_proof,activation.next_environment,activation.approved_runtime=validate_proof,next_environment,approved_runtime
if __name__=='__main__':activation.main()
