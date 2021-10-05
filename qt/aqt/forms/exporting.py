from aqt.qt import qtmajor
if qtmajor > 5:
  from .exporting_qt6 import *
else:
  from .exporting_qt5 import *  # type: ignore
