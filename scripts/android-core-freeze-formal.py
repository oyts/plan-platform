"""Formal25/30 to26/31 readonly retention; never fill auth or submit a queue."""
import importlib.util
from pathlib import Path
spec=importlib.util.spec_from_file_location('core_formal_base',Path(__file__).with_name('android-update-experience-formal.py'))
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
base.BEFORE_VERSION,base.BEFORE_CODE='0.1.25',30
base.VERSION,base.CODE='0.1.26',31
base.SHA='4767453195c758865cb0a1e2ef5e2c7f53b6164ed5028f6ec67c091420597313'
base.FOLDER=base.ROOT/'acceptance-results/native-core-freeze-0126'
if __name__=='__main__':base.main()
