#!/usr/bin/env python3
"""Bounded 0.1.18 activation using the established guarded release workflow."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('guarded_activation', Path(__file__).with_name('activate-ai-workbench-release.py'))
activation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(activation)
activation.NEW_VERSION, activation.OLD_VERSION, activation.MIGRATION = '0.1.18', '0.1.17', 'V25 -> V27'
activation.HASHES = {
    'release.tar.gz': 'b7654985d3349c6d220881e350883f1138eabbc1cc91cb06426e5514f5ac18fb',
    'backend/api.jar': '0e4f00684fb04468b75fe8cc6577fe971d330225a8574d09bea0f8ddb1fd4ce5',
}
base_validate = activation.validate_proof


def validate_proof(proof, modified_at, now):
    base_validate(proof, modified_at, now)
    activation.require(proof.get('archiveSha256') == activation.HASHES['release.tar.gz']
                       and set(proof.get('originalProjectionTables', [])) == {
                           'task_progress', 'weekly_reviews', 'restored_weekly_reviews', 'ai_sessions'}
                       and proof.get('originalTablesChecked', 0) >= 38,
                       'Final reflection archive and complete original-field projection proof required.')


activation.validate_proof = validate_proof
if __name__ == '__main__':
    activation.main()
