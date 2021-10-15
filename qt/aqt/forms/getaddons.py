from aqt.qt import qtmajor
if qtmajor > 5:
  from .getaddons_qt6 import *
else:
  from .getaddons_qt5 import *  # type: ignore
