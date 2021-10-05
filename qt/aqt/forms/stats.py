from aqt.qt import qtmajor
if qtmajor > 5:
  from .stats_qt6 import *
else:
  from .stats_qt5 import *  # type: ignore
