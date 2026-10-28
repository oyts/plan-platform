"""Post0.1.26 immutable snapshot, OSS roundtrip and retained runtime proof."""
import importlib.util
from pathlib import Path
spec=importlib.util.spec_from_file_location('core_post_base',Path(__file__).with_name('verify-update-experience-production.py'))
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
base.EXPECTED_VERSION='0.1.26'
base.EXPECTED_RELEASE='0.1.26-012dfda4'
if __name__=='__main__':base.main()
