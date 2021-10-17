from aqt.qt import qtmajor
if qtmajor > 5:
  from .addfield_qt6 import *
else:
  from .addfield_qt5 import *  # type: ignore
