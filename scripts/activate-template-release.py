#!/usr/bin/env python3
"""0.1.19 cutover with fresh immutable snapshot proof and retained overlays."""
import importlib.util
from pathlib import Path
spec=importlib.util.spec_from_file_location('guarded_activation',Path(__file__).with_name('activate-ai-workbench-release.py'))
activation=importlib.util.module_from_spec(spec);spec.loader.exec_module(activation)
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.19','0.1.18','V27 -> V28'
activation.HASHES={'release.tar.gz':'3293e8c573a7397337817007bd9dc3cc0fdfa4f99517efd06e60b80ce1f03ada','backend/api.jar':'b77b2e0970b37225dce66cfdb776fa23aeaf363c4e356811ec9a0dc1883c91b2'}
base_validate=activation.validate_proof
def validate_proof(proof,modified_at,now):
    base_validate(proof,modified_at,now)
    activation.require(proof.get('archiveSha256')==activation.HASHES['release.tar.gz']
        and proof.get('originalProjectionTables')==[] and proof.get('originalTablesChecked',0)>=46
        and proof.get('snapshotRestore',{}).get('tablesChecked',0)>=47,'Complete final template snapshot proof required.')
activation.validate_proof=validate_proof
if __name__=='__main__':activation.main()
