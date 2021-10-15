from aqt.qt import qtmajor
if qtmajor > 5:
  from .taglimit_qt6 import *
else:
  from .taglimit_qt5 import *  # type: ignore
