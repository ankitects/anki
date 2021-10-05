from aqt.qt import qtmajor
if qtmajor > 5:
  from .preferences_qt6 import *
else:
  from .preferences_qt5 import *  # type: ignore
