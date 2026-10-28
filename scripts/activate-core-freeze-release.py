"""Exact0.1.26 activation, retaining all approved runtime and native feeds."""
import importlib.util
from pathlib import Path
spec=importlib.util.spec_from_file_location('core_activation_base',Path(__file__).with_name('activate-update-experience-release.py'))
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
activation=base.activation
activation.NEW_VERSION,activation.OLD_VERSION,activation.MIGRATION='0.1.26','0.1.25','V34 -> V34'
activation.HASHES={'release.tar.gz':'012dfda43e3de0fb86761ebb784c10a9217200e878712fb25a3f5c79c3df7895','backend/api.jar':'da8d50fbe22bac2ebf6ce9f84b5ce1bfa405476ab3e2d374f8c2462cc6434d22'}
validate_proof,next_environment,approved_runtime=base.validate_proof,base.next_environment,base.approved_runtime
if __name__=='__main__':activation.main()
