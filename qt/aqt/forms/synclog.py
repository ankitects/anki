from aqt.qt import qtmajor
if qtmajor > 5:
  from .synclog_qt6 import *
else:
  from .synclog_qt5 import *  # type: ignore
