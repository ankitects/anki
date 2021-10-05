from aqt.qt import qtmajor
if qtmajor > 5:
  from .profiles_qt6 import *
else:
  from .profiles_qt5 import *  # type: ignore
