#!/usr/bin/env python3
"""0.1.20 cutover, fresh V28->V30 proof and owner-approved dual-mailbox feature."""
import importlib.util
import re
from pathlib import Path
spec=importlib.util.spec_from_file_location('historical_activation_base',Path(__file__).with_name('activate-ai-workbench-release.py'))
activation=importlib.util.module_from_spec(spec);spec.loader.exec_module(activation)
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.20','0.1.19','V28 -> V30'
activation.HASHES={'release.tar.gz':'b1a787012f5785b52fbd0b2950352309331a89a87f085b72d9b597aa6a8ce88f','backend/api.jar':'f46a8a7efd93e209be853bf991949fc837c88ba250b9be4499983e3a8d7bf352'}
base_validate,base_environment,base_runtime=activation.validate_proof,activation.next_environment,activation.approved_runtime

def validate_proof(proof,modified_at,now):
    base_validate(proof,modified_at,now)
    activation.require(proof.get('archiveSha256')==activation.HASHES['release.tar.gz']
        and proof.get('originalProjectionTables')==[] and proof.get('originalTablesChecked',0)>=48
        and proof.get('snapshotRestore',{}).get('tablesChecked',0)>=49,'Complete immutable production snapshot proof required.')

def next_environment(body):
    values=re.findall(r'^EMAIL_CHANGE_ENABLED=(.*)$',body,re.M)
    activation.require(values in [[],['false'],['true']],'Duplicate or ambiguous email-change flag.')
    updated=base_environment(body)
    return re.sub(r'^EMAIL_CHANGE_ENABLED=.*$','EMAIL_CHANGE_ENABLED=true',updated,flags=re.M) if values else updated.rstrip('\n')+'\nEMAIL_CHANGE_ENABLED=true\n'

def approved_runtime(container):
    base_runtime(container)
    activation.require(any(m['Destination']=='/run/secrets/mail' and not m['RW'] for m in container['Mounts']),'Existing read-only SMTP mount required; do not recreate credentials.')
    if container['Config']['Image']=='plan-platform-api:0.1.20':
        values=dict(v.split('=',1) for v in container['Config']['Env'] if '=' in v)
        activation.require(values.get('EMAIL_CHANGE_ENABLED')=='true','Owner-approved mailbox change not enabled.')

activation.validate_proof,activation.next_environment,activation.approved_runtime=validate_proof,next_environment,approved_runtime
if __name__=='__main__':activation.main()
