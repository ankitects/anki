from aqt.qt import qtmajor
if qtmajor > 5:
  from .browseropts_qt6 import *
else:
  from .browseropts_qt5 import *  # type: ignore
