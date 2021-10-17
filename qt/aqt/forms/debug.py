from aqt.qt import qtmajor
if qtmajor > 5:
  from .debug_qt6 import *
else:
  from .debug_qt5 import *  # type: ignore
