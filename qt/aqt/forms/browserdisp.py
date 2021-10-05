from aqt.qt import qtmajor
if qtmajor > 5:
  from .browserdisp_qt6 import *
else:
  from .browserdisp_qt5 import *  # type: ignore
