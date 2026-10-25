"""Exact0.1.23 activation with V33-aware refusal of unsafe old writes."""
import importlib.util,re
from pathlib import Path
spec=importlib.util.spec_from_file_location('admin_activation_base',Path(__file__).with_name('activate-ai-workbench-release.py'))
activation=importlib.util.module_from_spec(spec);spec.loader.exec_module(activation)
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.23','0.1.22','V32 -> V33'
# Set only after immutable formal candidates and the archive have been verified.
activation.HASHES={'release.tar.gz':'1f38e13b7be3c4eb283cb272133cf20cf288f754bb81ebf121e6a9a30e961335','backend/api.jar':'382f9c57c3694ca6547604e12b52063af9d9359caafe5aeb27d4aee2bcf97ba2'}
# Actual0.1.22 supports generation/long planning, NOT the V33 runtime protocol.
activation.ROLLBACK_CAPABILITIES=['--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','no']
base_validate,base_environment,base_runtime=activation.validate_proof,activation.next_environment,activation.approved_runtime

def validate_proof(proof,modified_at,now):
    activation.require(all(v!='0'*64 for v in activation.HASHES.values()),'Accepted final hashes are not configured.')
    base_validate(proof,modified_at,now)
    activation.require(proof.get('archiveSha256')==activation.HASHES['release.tar.gz'] and proof.get('originalProjectionTables')==['ai_jobs']
        and proof.get('originalTablesChecked',0)>=54 and proof.get('snapshotRestore',{}).get('tablesChecked',0)>=55,'Complete V32 immutable snapshot and original-field projections required.')

def next_environment(body):
    for flag in ['EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED']:
        activation.require(re.findall('^'+flag+'=(.*)$',body,re.M)==['true'],'Retain approved mailbox change and bounded repair.')
    return base_environment(body)

def approved_runtime(container):
    base_runtime(container)
    values=dict(v.split('=',1) for v in container['Config']['Env'] if '=' in v)
    activation.require(values.get('EMAIL_CHANGE_ENABLED')=='true' and values.get('AI_REPAIR_ENABLED')=='true','Approved AI/mail flags must remain enabled.')
    activation.require(any(m['Destination']=='/run/secrets/mail' and not m['RW'] for m in container['Mounts']),'Retained read-only SMTP mount required.')
activation.validate_proof,activation.next_environment,activation.approved_runtime=validate_proof,next_environment,approved_runtime
if __name__=='__main__':activation.main()
