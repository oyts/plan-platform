"""Exact0.1.24 activation; collection is enabled separately after public health."""
import importlib.util,re
from pathlib import Path
spec=importlib.util.spec_from_file_location('metrics_activation_base',Path(__file__).with_name('activate-ai-workbench-release.py'))
activation=importlib.util.module_from_spec(spec);spec.loader.exec_module(activation)
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.24','0.1.23','V33 -> V34'
activation.HASHES={'release.tar.gz':'a4a0d911146b1cf5ce998479c70b2db52922d9acd09c5a87b302bade1775f50f','backend/api.jar':'9bbd951547020f9a55e54f517e9d4b5e8ce2d1fede6a018cd99e64fc1a720368'}
activation.ROLLBACK_CAPABILITIES=['--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','yes','--target-supports-metrics','no']
activation.OVERLAYS=activation.OVERLAYS+['production-telemetry.yml']
base_validate,base_environment,base_runtime=activation.validate_proof,activation.next_environment,activation.approved_runtime

def validate_proof(proof,modified_at,now):
    activation.require(all(v!='0'*64 for v in activation.HASHES.values()),'Accepted final hashes are not configured.')
    base_validate(proof,modified_at,now)
    activation.require(proof.get('archiveSha256')==activation.HASHES['release.tar.gz'] and proof.get('originalProjectionTables')==['operations_mail_delivery']
        and proof.get('originalTablesChecked',0)>=58 and proof.get('snapshotRestore',{}).get('tablesChecked',0)>=59,'Complete V33 snapshot and original mail-column protection required.')

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
