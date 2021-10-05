from aqt.qt import qtmajor
if qtmajor > 5:
  from .findreplace_qt6 import *
else:
  from .findreplace_qt5 import *  # type: ignore
