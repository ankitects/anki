from aqt.qt import qtmajor
if qtmajor > 5:
  from .about_qt6 import *
else:
  from .about_qt5 import *  # type: ignore
