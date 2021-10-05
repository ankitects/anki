from aqt.qt import qtmajor
if qtmajor > 5:
  from .browser_qt6 import *
else:
  from .browser_qt5 import *  # type: ignore
