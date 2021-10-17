from aqt.qt import qtmajor
if qtmajor > 5:
  from .progress_qt6 import *
else:
  from .progress_qt5 import *  # type: ignore
