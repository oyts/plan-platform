"""Exact0.1.25 Web/API activation; native feeds stay at accepted0.1.24."""
import importlib.util,re
from pathlib import Path
spec=importlib.util.spec_from_file_location('updates_activation_base',Path(__file__).with_name('activate-ai-workbench-release.py'))
activation=importlib.util.module_from_spec(spec);spec.loader.exec_module(activation)
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.25','0.1.24','V34 -> V34'
activation.HASHES={'release.tar.gz':'4be0a01f7ebfed29b8559cf97bda5e44c90bfdd53d597a3decc6953ed3ba9e80','backend/api.jar':'78dfb43b0a0a2538bb1df4a3bc0ccdb0a8d9a93e8cf09ebaf2313a69738e9e73'}
activation.ROLLBACK_CAPABILITIES=['--target-supports-generation','yes','--target-supports-long-planning','yes','--target-supports-admin-runtime','yes','--target-supports-metrics','yes']
activation.OVERLAYS=activation.OVERLAYS+['production-telemetry.yml']
base_validate,base_environment,base_runtime=activation.validate_proof,activation.next_environment,activation.approved_runtime

def validate_proof(proof,modified_at,now):
    base_validate(proof,modified_at,now)
    activation.require(proof.get('archiveSha256')==activation.HASHES['release.tar.gz']
        and proof.get('originalProjectionTables')==['users','task_templates','mainlines'] and proof.get('originalTablesChecked',0)>=61
        and proof.get('snapshotRestore',{}).get('tablesChecked',0)>=62,
        'Complete unchanged V34 snapshot and both image boot proofs required.')

def next_environment(body):
    for flag in ['EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED']:
        activation.require(re.findall('^'+flag+'=(.*)$',body,re.M)==['true'],'Retain approved mailbox change and bounded repair.')
    return base_environment(body)

def approved_runtime(container):
    base_runtime(container)
    values=dict(v.split('=',1) for v in container['Config']['Env'] if '=' in v)
    for flag in ['EMAIL_CHANGE_ENABLED','AI_REPAIR_ENABLED','METRICS_ENABLED','TELEMETRY_ENABLED']:
        activation.require(values.get(flag)=='true','Approved runtime feature must remain enabled.')
    activation.require(values.get('TELEMETRY_MAIL_ALERTS_ENABLED')=='false','New metric mail remains disabled.')
    activation.require(any(m['Destination']=='/run/secrets/mail' and not m['RW'] for m in container['Mounts']),'Retain read-only SMTP mount.')
activation.validate_proof,activation.next_environment,activation.approved_runtime=validate_proof,next_environment,approved_runtime
if __name__=='__main__':activation.main()
