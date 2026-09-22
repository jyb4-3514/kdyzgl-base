# -*- coding: utf-8 -*-
"""pytest 公共配置：把 src 加入 sys.path，使 `collector` 包可导入。

这样无需额外打包/安装即可在 hrm-dev/collector 目录下执行 `python -m pytest tests`。
"""
from __future__ import annotations

import sys
from pathlib import Path

_SRC_DIR = Path(__file__).resolve().parents[1] / "src"
if str(_SRC_DIR) not in sys.path:
    sys.path.insert(0, str(_SRC_DIR))
