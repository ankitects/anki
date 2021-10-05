from aqt.qt import qtmajor
if qtmajor > 5:
  from .setlang_qt6 import *
else:
  from .setlang_qt5 import *  # type: ignore
