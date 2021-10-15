from aqt.qt import qtmajor
if qtmajor > 5:
  from .modelopts_qt6 import *
else:
  from .modelopts_qt5 import *  # type: ignore
