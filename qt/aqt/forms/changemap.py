from aqt.qt import qtmajor
if qtmajor > 5:
  from .changemap_qt6 import *
else:
  from .changemap_qt5 import *  # type: ignore
