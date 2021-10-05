from aqt.qt import qtmajor
if qtmajor > 5:
  from .main_qt6 import *
else:
  from .main_qt5 import *  # type: ignore
