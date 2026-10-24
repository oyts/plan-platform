"""Exact0.1.22 activation after fresh immutable-snapshot and native acceptance."""
import importlib.util,re
from pathlib import Path
spec=importlib.util.spec_from_file_location('long_activation_base',Path(__file__).with_name('activate-ai-workbench-release.py'))
activation=importlib.util.module_from_spec(spec);spec.loader.exec_module(activation)
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.22','0.1.21','V31 -> V32'
# Exact locally accepted0.1.22 bytes; never rebuild a published version.
activation.HASHES={'release.tar.gz':'5b962fb870806b1705b9d270d3c298662059885cefce3878ca30ab488233f26c','backend/api.jar':'c3ba488184f2738495d5dd39839fd35b8671b818755edc6d013f3c7312c14b71'}
base_validate,base_environment,base_runtime=activation.validate_proof,activation.next_environment,activation.approved_runtime
def validate_proof(proof,modified_at,now):
    activation.require(all(v!='0'*64 for v in activation.HASHES.values()),'Accepted final hashes are not configured.')
    base_validate(proof,modified_at,now)
    activation.require(proof.get('archiveSha256')==activation.HASHES['release.tar.gz'] and proof.get('originalProjectionTables')==['ai_sessions']
        and proof.get('originalTablesChecked',0)>=51 and proof.get('snapshotRestore',{}).get('tablesChecked',0)>=52,'Complete V31 immutable snapshot and original-field projections required.')
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
