"""
Pytest configuration and environment bootstrapping for PHANTOM tests.
Ensures Tkinter and system libraries locate correct paths within virtual environments.
The Great Corporation
"""

import os
import sys
from pathlib import Path

# Under Windows inside a venv, Tkinter may fail to locate Tcl/Tk without explicit environment pointers
if sys.platform == "win32":
    py_base = Path(sys.base_prefix)
    tcl_dir = py_base / "tcl"
    if tcl_dir.exists():
        for sub in tcl_dir.glob("tcl*"):
            if sub.is_dir() and (sub / "init.tcl").exists():
                os.environ.setdefault("TCL_LIBRARY", str(sub))
                break
        for sub in tcl_dir.glob("tk*"):
            if sub.is_dir() and (sub / "tk.tcl").exists():
                os.environ.setdefault("TK_LIBRARY", str(sub))
                break
