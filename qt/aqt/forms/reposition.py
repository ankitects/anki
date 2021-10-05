from aqt.qt import qtmajor
if qtmajor > 5:
  from .reposition_qt6 import *
else:
  from .reposition_qt5 import *  # type: ignore
