"""Makes every package the suite imports resolvable regardless of which
directory pytest is invoked from (mirrors the sys.path bootstrap used by
bridge/inference_server.py and the scripts/ entry points):

  ReSACO/    -> `resaco` (the ReSACO algorithm), `bridge`
  repo root  -> `mec_core` (shared env/config/networks), `baselines`
"""

import os
import sys

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)
sys.path.insert(0, os.path.dirname(_RESACO_DIR))
